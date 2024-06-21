/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.exec.planner.sql.handlers.query;

import static com.dremio.exec.planner.sql.handlers.SqlHandlerUtil.PLANNER_SOURCE_TARGET_SOURCE_TYPE_SPAN_ATTRIBUTE_NAME;

import com.dremio.catalog.model.CatalogEntityKey;
import com.dremio.catalog.model.ResolvedVersionContext;
import com.dremio.common.exceptions.UserException;
import com.dremio.common.utils.protos.QueryIdHelper;
import com.dremio.exec.ExecConstants;
import com.dremio.exec.catalog.Catalog;
import com.dremio.exec.catalog.CatalogOptions;
import com.dremio.exec.catalog.CatalogUtil;
import com.dremio.exec.catalog.ColumnCountTooLargeException;
import com.dremio.exec.catalog.DatasetCatalog;
import com.dremio.exec.catalog.DatasetCatalog.UpdateStatus;
import com.dremio.exec.catalog.DremioTable;
import com.dremio.exec.catalog.MutablePlugin;
import com.dremio.exec.catalog.SourceCatalog;
import com.dremio.exec.physical.PhysicalPlan;
import com.dremio.exec.physical.base.CombineSmallFileOptions;
import com.dremio.exec.physical.base.IcebergWriterOptions;
import com.dremio.exec.physical.base.ImmutableIcebergWriterOptions;
import com.dremio.exec.physical.base.ImmutableTableFormatWriterOptions;
import com.dremio.exec.physical.base.PhysicalOperator;
import com.dremio.exec.physical.base.TableFormatWriterOptions;
import com.dremio.exec.physical.base.TableFormatWriterOptions.TableFormatOperation;
import com.dremio.exec.physical.base.WriterOptions;
import com.dremio.exec.planner.DremioRexBuilder;
import com.dremio.exec.planner.logical.CreateTableEntry;
import com.dremio.exec.planner.logical.ProjectRel;
import com.dremio.exec.planner.logical.Rel;
import com.dremio.exec.planner.logical.ScreenRel;
import com.dremio.exec.planner.logical.WriterRel;
import com.dremio.exec.planner.physical.PlannerSettings;
import com.dremio.exec.planner.physical.Prel;
import com.dremio.exec.planner.physical.PrelUtil;
import com.dremio.exec.planner.sql.CalciteArrowHelper;
import com.dremio.exec.planner.sql.SqlExceptionHelper;
import com.dremio.exec.planner.sql.handlers.ConvertedRelNode;
import com.dremio.exec.planner.sql.handlers.DrelTransformer;
import com.dremio.exec.planner.sql.handlers.PlanLogUtil;
import com.dremio.exec.planner.sql.handlers.PrelTransformer;
import com.dremio.exec.planner.sql.handlers.SqlHandlerConfig;
import com.dremio.exec.planner.sql.handlers.SqlHandlerUtil;
import com.dremio.exec.planner.sql.handlers.SqlToRelTransformer;
import com.dremio.exec.planner.sql.parser.DataAdditionCmdCall;
import com.dremio.exec.planner.sql.parser.SqlCopyIntoTable;
import com.dremio.exec.planner.sql.parser.SqlCreateEmptyTable;
import com.dremio.exec.planner.sql.parser.SqlCreateTable;
import com.dremio.exec.record.BatchSchema;
import com.dremio.exec.store.DatasetRetrievalOptions;
import com.dremio.exec.store.StoragePlugin;
import com.dremio.exec.store.dfs.FileSystemPlugin;
import com.dremio.exec.store.dfs.IcebergTableProps;
import com.dremio.exec.store.dfs.copyinto.SystemIcebergTablePluginAwareCreateTableEntry;
import com.dremio.exec.store.dfs.system.SystemIcebergTablesStoragePluginConfig;
import com.dremio.exec.store.iceberg.IcebergSerDe;
import com.dremio.exec.store.iceberg.IcebergUtils;
import com.dremio.exec.store.iceberg.SchemaConverter;
import com.dremio.exec.store.iceberg.SupportsIcebergMutablePlugin;
import com.dremio.exec.store.iceberg.model.IcebergCommandType;
import com.dremio.exec.store.iceberg.model.IcebergModel;
import com.dremio.exec.util.ColumnUtils;
import com.dremio.exec.work.foreman.SqlUnsupportedException;
import com.dremio.io.file.FileSystem;
import com.dremio.options.OptionManager;
import com.dremio.service.namespace.NamespaceKey;
import com.dremio.service.namespace.file.proto.FileType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.protostuff.ByteString;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.NlsString;
import org.apache.calcite.util.Pair;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.io.FileIO;

public abstract class DataAdditionCmdHandler implements SqlToPlanHandler {
  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(DataAdditionCmdHandler.class);

  private String textPlan;
  private Rel drel;
  private CreateTableEntry tableEntry = null;
  private BatchSchema tableSchemaFromKVStore = null;
  private List<String> partitionColumns = null;
  private Map<String, Object> storageOptionsMap = null;
  private boolean isIcebergTable = false;
  private boolean isVersionedTable = false;
  private Supplier<DremioTable> dremioTable = null;

  public DataAdditionCmdHandler() {}

  public boolean isIcebergTable() {
    return isIcebergTable;
  }

  private boolean isVersionedTable() {
    return isVersionedTable;
  }

  public TableFormatOperation getIcebergWriterOperation() {
    if (!isIcebergTable) {
      return TableFormatOperation.NONE;
    }
    return isCreate() ? TableFormatOperation.CREATE : TableFormatOperation.INSERT;
  }

  public abstract boolean isCreate();

  protected void cleanUp(DatasetCatalog datasetCatalog, NamespaceKey key) {}

  @WithSpan
  public PhysicalPlan getPlan(
      Catalog catalog,
      CatalogEntityKey catalogEntityKey,
      SqlHandlerConfig config,
      String sql,
      DataAdditionCmdCall sqlCmd,
      ResolvedVersionContext version)
      throws Exception {
    try {
      Span.current()
          .setAttribute(
              PLANNER_SOURCE_TARGET_SOURCE_TYPE_SPAN_ATTRIBUTE_NAME,
              SqlHandlerUtil.getSourceType(catalog, catalogEntityKey.getRootEntity()));
      final ConvertedRelNode convertedRelNode =
          SqlToRelTransformer.validateAndConvert(config, sqlCmd.getQuery());
      final RelDataType validatedRowType = convertedRelNode.getValidatedRowType();

      long maxColumnCount =
          config.getContext().getOptions().getOption(CatalogOptions.METADATA_LEAF_COLUMN_MAX);
      if (validatedRowType.getFieldCount() > maxColumnCount) {
        throw new ColumnCountTooLargeException((int) maxColumnCount);
      }

      final RelNode queryRelNode = convertedRelNode.getConvertedNode();
      // Table field names should resolve to validated query fields for CTAS
      // with unspecified field names. This helps to prevent unexpected table
      // field names for CTAS (DX-65794).
      final List<String> tableFieldNames =
          isCreate() && sqlCmd.getFieldNames().size() == 0
              ? validatedRowType.getFieldNames()
              : sqlCmd.getFieldNames();
      final RelNode newTblRelNode =
          SqlHandlerUtil.resolveNewTableRel(
              false, tableFieldNames, validatedRowType, queryRelNode, !isCreate());

      final long ringCount = config.getContext().getOptions().getOption(PlannerSettings.RING_COUNT);

      ByteString extendedByteString = null;
      if (!isCreate()) {
        extendedByteString =
            dremioTableSupplier(catalog, catalogEntityKey)
                .get()
                .getDatasetConfig()
                .getReadDefinition()
                .getExtendedProperty();
      }

      // For Insert command we make sure query schema match exactly with table schema,
      // which includes partition columns. So, not checking here
      final RelNode newTblRelNodeWithPCol =
          SqlHandlerUtil.qualifyPartitionCol(
              newTblRelNode,
              isCreate()
                  ? sqlCmd.getPartitionColumns(
                      null /*param is unused in this interface for create */)
                  : Lists.newArrayList());

      PlanLogUtil.log("Calcite", newTblRelNodeWithPCol, logger, null);

      final List<String> partitionFieldNames =
          sqlCmd.getPartitionColumns(dremioTableSupplier(catalog, catalogEntityKey).get());
      final Set<String> fieldNames =
          validatedRowType.getFieldNames().stream().collect(Collectors.toSet());
      TableFormatWriterOptions tableFormatOptions =
          new ImmutableTableFormatWriterOptions.Builder()
              .setOperation(getIcebergWriterOperation())
              .build();
      Map<String, String> tableProperties =
          isCreate()
              ? IcebergUtils.convertTableProperties(
                  ((SqlCreateTable) sqlCmd).getTablePropertyNameList(),
                  ((SqlCreateTable) sqlCmd).getTablePropertyValueList(),
                  false)
              : Collections.emptyMap();
      WriterOptions options =
          new WriterOptions(
              (int) ringCount,
              partitionFieldNames,
              sqlCmd.getSortColumns(),
              sqlCmd.getDistributionColumns(),
              sqlCmd.getPartitionDistributionStrategy(config, partitionFieldNames, fieldNames),
              sqlCmd.getLocation(),
              sqlCmd.isSingleWriter(),
              Long.MAX_VALUE,
              tableFormatOptions,
              extendedByteString,
              version,
              tableProperties);

      if (config
          .getContext()
          .getOptions()
          .getOption(ExecConstants.ENABLE_ICEBERG_COMBINE_SMALL_FILES_FOR_DML)) {
        CombineSmallFileOptions combineSmallFileOptions =
            CombineSmallFileOptions.builder()
                .setSmallFileSize(
                    Double.valueOf(
                            config
                                    .getContext()
                                    .getOptions()
                                    .getOption(ExecConstants.PARQUET_BLOCK_SIZE_VALIDATOR)
                                * config
                                    .getContext()
                                    .getOptions()
                                    .getOption(ExecConstants.SMALL_PARQUET_BLOCK_SIZE_RATIO))
                        .longValue())
                .build();
        options = options.withCombineSmallFileOptions(combineSmallFileOptions);
      }

      // Convert the query to Dremio Logical plan and insert a writer operator on top.
      drel =
          this.convertToDrel(
              config,
              newTblRelNodeWithPCol,
              catalog,
              catalogEntityKey,
              options,
              newTblRelNode.getRowType(),
              storageOptionsMap,
              sqlCmd.getFieldNames(),
              sqlCmd);

      final Pair<Prel, String> convertToPrel = PrelTransformer.convertToPrel(config, drel);
      final Prel prel = convertToPrel.getKey();
      textPlan = convertToPrel.getValue();
      PhysicalOperator pop = PrelTransformer.convertToPop(config, prel);

      PhysicalPlan plan =
          PrelTransformer.convertToPlan(
              config,
              pop,
              isIcebergTable() && !isVersionedTable()
                  ? () -> refreshDataset(catalog, catalogEntityKey.toNamespaceKey(), isCreate())
                  : null,
              () -> cleanUp(catalog, catalogEntityKey.toNamespaceKey()));

      PlanLogUtil.log(config, "Dremio Plan", plan, logger);
      return plan;

    } catch (Exception ex) {
      throw SqlExceptionHelper.coerceException(logger, sql, ex, true);
    }
  }

  protected CreateTableEntry getTableEntry() {
    return tableEntry;
  }

  protected void checkExistenceValidity(CatalogEntityKey path, DremioTable dremioTable) {
    if (dremioTable != null && isCreate()) {
      throw UserException.validationError()
          .message("A table or view with given name [%s] already exists.", path.toString())
          .build(logger);
    }
    if (dremioTable == null && !isCreate()) {
      throw UserException.validationError().message("Table [%s] not found", path).buildSilently();
    }
  }

  // For iceberg tables, do a refresh after the attempt completion.
  public static void refreshDataset(
      DatasetCatalog datasetCatalog, NamespaceKey key, boolean autopromote) {
    DatasetRetrievalOptions options =
        DatasetRetrievalOptions.newBuilder()
            .setAutoPromote(autopromote)
            .setForceUpdate(true)
            .build()
            .withFallback(DatasetRetrievalOptions.DEFAULT);

    UpdateStatus updateStatus = datasetCatalog.refreshDataset(key, options, false);
    logger.info(
        "refreshed{} dataset {}, update status \"{}\"",
        autopromote ? " and autopromoted" : "",
        key,
        updateStatus.name());
  }

  private Rel convertToDrel(
      SqlHandlerConfig config,
      RelNode relNode,
      Catalog datasetCatalog,
      CatalogEntityKey key,
      WriterOptions options,
      RelDataType queryRowType,
      final Map<String, Object> storageOptions,
      final List<String> fieldNames,
      DataAdditionCmdCall sqlCmd)
      throws SqlUnsupportedException {
    Rel convertedRelNode = DrelTransformer.convertToDrel(config, relNode);

    // Put a non-trivial topProject to ensure the final output field name is preserved, when
    // necessary.
    // Only insert project when the field count from the child is same as that of the queryRowType.

    String queryId = "";
    IcebergTableProps icebergTableProps = null;
    if (isIcebergTable()) {
      queryId = QueryIdHelper.getQueryId(config.getContext().getQueryId());
      ByteString partitionSpec = null;
      String sortOrder = null;
      BatchSchema tableSchema = null;
      String icebergSchema = null;
      Long snapshotId = null;
      List<String> sortColumns;
      if (!isCreate()) {
        DremioTable dremioTable = dremioTableSupplier(datasetCatalog, key).get();
        tableSchemaFromKVStore = dremioTable.getSchema();
        partitionColumns =
            dremioTable.getDatasetConfig().getReadDefinition().getPartitionColumnsList();
        partitionSpec =
            IcebergUtils.getCurrentPartitionSpec(
                dremioTable.getDatasetConfig().getPhysicalDataset(),
                dremioTable.getSchema(),
                options.getPartitionColumns());
        sortOrder =
            IcebergUtils.getCurrentSortOrder(
                dremioTable.getDatasetConfig().getPhysicalDataset(),
                config.getContext().getOptions());
        tableSchema = dremioTable.getSchema();
        snapshotId =
            dremioTable
                .getDataset()
                .getDatasetConfig()
                .getPhysicalDataset()
                .getIcebergMetadata()
                .getSnapshotId();
        icebergSchema =
            IcebergUtils.getCurrentIcebergSchema(
                dremioTable.getDatasetConfig().getPhysicalDataset(), dremioTable.getSchema());
        // This is insert statement update  key to use existing table from catalog
        key =
            CatalogEntityKey.namespaceKeyToCatalogEntityKey(
                dremioTable.getPath(), key.getTableVersionContext());
      } else {
        tableSchema = CalciteArrowHelper.fromCalciteRowType(queryRowType);
        PartitionSpec partitionSpecBytes =
            IcebergUtils.getIcebergPartitionSpecFromTransforms(
                tableSchema, sqlCmd.getPartitionTransforms(null), null);
        partitionSpec =
            ByteString.copyFrom(IcebergSerDe.serializePartitionSpec(partitionSpecBytes));
        sortOrder =
            IcebergSerDe.serializeSortOrderAsJson(
                IcebergUtils.getIcebergSortOrder(
                    tableSchema,
                    sqlCmd.getSortColumns(),
                    partitionSpecBytes.schema(),
                    config.getContext().getOptions()));
        icebergSchema = IcebergSerDe.serializedSchemaAsJson(partitionSpecBytes.schema());
      }
      Schema schema = SchemaConverter.getBuilder().build().toIcebergSchema(tableSchema);
      SortOrder deserializedSortOrder =
          IcebergSerDe.deserializeSortOrderFromJson(schema, sortOrder);
      sortColumns =
          IcebergUtils.getColumnsFromSortOrder(
              deserializedSortOrder, config.getContext().getOptions());
      icebergTableProps =
          new IcebergTableProps(
              null,
              queryId,
              null,
              options.getPartitionColumns(),
              isCreate() ? IcebergCommandType.CREATE : IcebergCommandType.INSERT,
              null,
              key.getEntityName(),
              null,
              options.getVersion(),
              partitionSpec,
              icebergSchema,
              null,
              sortOrder,
              options.getTableProperties(),
              FileType.PARQUET); // TODO: DX-43311 Should we allow null version?
      icebergTableProps.setPersistedFullSchema(tableSchema);
      IcebergWriterOptions icebergOptions =
          new ImmutableIcebergWriterOptions.Builder()
              .from(options.getTableFormatOptions().getIcebergSpecificOptions())
              .setIcebergTableProps(icebergTableProps)
              .build();
      TableFormatWriterOptions tableFormatWriterOptions =
          new ImmutableTableFormatWriterOptions.Builder()
              .from(options.getTableFormatOptions())
              .setIcebergSpecificOptions(icebergOptions)
              .setSnapshotId(snapshotId)
              .build();
      options.setSortColumns(sortColumns);
      options.setTableFormatOptions(tableFormatWriterOptions);
    }

    logger.debug(
        "Creating new table with WriterOptions : '{}' icebergTableProps : '{}' ",
        options,
        icebergTableProps);

    tableEntry =
        datasetCatalog.createNewTable(
            key.toNamespaceKey(), icebergTableProps, options, storageOptions);

    // we are running COPY INTO with CONTINUE option
    if (sqlCmd instanceof SqlCopyIntoTable
        && ((SqlCopyIntoTable) sqlCmd).isTableExtended()
        && tableEntry instanceof SystemIcebergTablePluginAwareCreateTableEntry) {
      ((SystemIcebergTablePluginAwareCreateTableEntry) tableEntry)
          .setSystemIcebergTablesPlugin(
              (datasetCatalog.getSource(
                  SystemIcebergTablesStoragePluginConfig.SYSTEM_ICEBERG_TABLES_PLUGIN_NAME)));
    }

    if (isIcebergTable()) {
      Preconditions.checkState(
          tableEntry.getIcebergTableProps().getTableLocation() != null
              && !tableEntry.getIcebergTableProps().getTableLocation().isEmpty(),
          "Table folder location must not be empty");
    }

    if (!isCreate()) {
      BatchSchema partSchemaWithSelectedFields =
          tableSchemaFromKVStore.subset(fieldNames).orElse(tableSchemaFromKVStore);
      queryRowType =
          CalciteArrowHelper.wrap(partSchemaWithSelectedFields)
              .toCalciteRecordType(
                  convertedRelNode.getCluster().getTypeFactory(),
                  PrelUtil.getPlannerSettings(convertedRelNode.getCluster())
                      .isFullNestedSchemaSupport());
      logger.debug("Inserting into table with schema : '{}' ", tableSchemaFromKVStore.toString());
    }

    // DX-54255: Don't add cast projection, if inserting values from another table
    if (RelOptUtil.findTables(convertedRelNode).isEmpty()
        && !(sqlCmd instanceof SqlCopyIntoTable)) {
      convertedRelNode = addCastProject(convertedRelNode, queryRowType);
    }

    // skip writer and display DML results on UI only
    if (!config.getContext().getOptions().getOption(ExecConstants.ENABLE_DML_DISPLAY_RESULT_ONLY)
        || !(sqlCmd instanceof SqlCopyIntoTable)) {
      convertedRelNode =
          new WriterRel(
              convertedRelNode.getCluster(),
              convertedRelNode.getCluster().traitSet().plus(Rel.LOGICAL),
              convertedRelNode,
              tableEntry,
              queryRowType);
    }

    convertedRelNode =
        SqlHandlerUtil.storeQueryResultsIfNeeded(
            config.getConverter().getParserConfig(), config.getContext(), convertedRelNode);

    return new ScreenRel(
        convertedRelNode.getCluster(), convertedRelNode.getTraitSet(), convertedRelNode);
  }

  public Rel addCastProject(RelNode convertedRelNode, RelDataType queryRowType) {
    RexBuilder rexBuilder = convertedRelNode.getCluster().getRexBuilder();
    List<RelDataTypeField> fields = queryRowType.getFieldList();
    List<RelDataTypeField> inputFields = convertedRelNode.getRowType().getFieldList();
    List<RexNode> castExprs = new ArrayList<>();

    if (inputFields.size() > fields.size()) {
      throw UserException.validationError()
          .message(
              "The number of fields in values cannot exceed "
                  + "the number of fields in the schema. Schema: %d, Given: %d",
              fields.size(), inputFields.size())
          .buildSilently();
    }

    for (int i = 0; i < fields.size(); i++) {
      RelDataType type = fields.get(i).getType();
      RexNode rexNode =
          i < inputFields.size()
              ? new RexInputRef(i, inputFields.get(i).getType())
              : rexBuilder.makeNullLiteral(type);
      RexNode castExpr;
      if (!isCastSupportedForType(type)) {
        castExpr = rexNode;
      } else if (rexBuilder instanceof DremioRexBuilder) {
        castExpr = ((DremioRexBuilder) rexBuilder).makeAbstractCastIgnoreType(type, rexNode);
      } else {
        castExpr = rexBuilder.makeAbstractCast(type, rexNode);
      }

      castExprs.add(castExpr);
    }

    return ProjectRel.create(
        convertedRelNode.getCluster(),
        convertedRelNode.getCluster().traitSet().plus(Rel.LOGICAL),
        convertedRelNode,
        castExprs,
        queryRowType);
  }

  private boolean isCastSupportedForType(RelDataType type) {
    SqlTypeName typeName = type.getSqlTypeName();
    return typeName != SqlTypeName.ROW
        && typeName != SqlTypeName.STRUCTURED
        && typeName != SqlTypeName.MAP;
  }

  public void validateIcebergSchemaForInsertCommand(
      DataAdditionCmdCall sqlInsertTable, SqlHandlerConfig config) {
    IcebergTableProps icebergTableProps = tableEntry.getIcebergTableProps();
    Preconditions.checkState(
        icebergTableProps.getIcebergOpType() == IcebergCommandType.INSERT,
        "unexpected state found");

    BatchSchema querySchema = icebergTableProps.getFullSchema();
    Preconditions.checkState(
        tableEntry.getPlugin() instanceof SupportsIcebergMutablePlugin,
        "Plugin not instance of SupportsIcebergMutablePlugin");
    SupportsIcebergMutablePlugin plugin = (SupportsIcebergMutablePlugin) tableEntry.getPlugin();
    try (FileSystem fs =
        plugin.createFS(icebergTableProps.getTableLocation(), tableEntry.getUserName(), null)) {
      FileIO fileIO = plugin.createIcebergFileIO(fs, null, null, null, null);
      IcebergModel icebergModel =
          plugin.getIcebergModel(icebergTableProps, tableEntry.getUserName(), null, fileIO);
      Table table =
          icebergModel.getIcebergTable(
              icebergModel.getTableIdentifier(icebergTableProps.getTableLocation()));
      SchemaConverter schemaConverter =
          SchemaConverter.getBuilder()
              .setTableName(table.name())
              .setMapTypeEnabled(
                  config.getContext().getOptions().getOption(ExecConstants.ENABLE_MAP_DATA_TYPE))
              .build();
      BatchSchema icebergSchema = schemaConverter.fromIceberg(table.schema());

      // this check can be removed once we support schema evolution in dremio.
      if (!icebergSchema.equalsIgnoreCase(tableSchemaFromKVStore)) {
        throw UserException.validationError()
            .message(
                "The schema for table %s does not match with the iceberg %s.",
                tableSchemaFromKVStore, icebergSchema)
            .buildSilently();
      }

      BatchSchema partSchemaWithSelectedFields =
          tableSchemaFromKVStore
              .subset(sqlInsertTable.getFieldNames())
              .orElse(tableSchemaFromKVStore);
      if (sqlInsertTable instanceof SqlCopyIntoTable
          && ((SqlCopyIntoTable) sqlInsertTable).isTableExtended()) {
        querySchema =
            BatchSchema.of(
                querySchema.getFields().stream()
                    .filter(
                        f -> !f.getName().equalsIgnoreCase(ColumnUtils.COPY_HISTORY_COLUMN_NAME))
                    .collect(Collectors.toList())
                    .toArray(new Field[querySchema.getFieldCount() - 1]));
      }
      if (!querySchema.equalsTypesWithoutPositions(partSchemaWithSelectedFields)) {
        throw UserException.validationError()
            .message(
                "Table %s doesn't match with query %s.", partSchemaWithSelectedFields, querySchema)
            .buildSilently();
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private boolean comparePartitionColumnLists(List<String> icebergPartitionColumns) {
    boolean icebergHasPartitionSpec =
        (icebergPartitionColumns != null && icebergPartitionColumns.size() > 0);
    boolean kvStoreHasPartitionSpec = (partitionColumns != null && partitionColumns.size() > 0);
    if (icebergHasPartitionSpec != kvStoreHasPartitionSpec) {
      return false;
    }

    if (!icebergHasPartitionSpec && !kvStoreHasPartitionSpec) {
      return true;
    }

    if (icebergPartitionColumns.size() != partitionColumns.size()) {
      return false;
    }

    for (int index = 0; index < icebergPartitionColumns.size(); ++index) {
      if (!icebergPartitionColumns.get(index).equalsIgnoreCase(partitionColumns.get(index))) {
        return false;
      }
    }

    return true;
  }

  /**
   * Helper method to create map of key, value pairs, the value is a Java type object.
   *
   * @param args
   * @return
   */
  @VisibleForTesting
  public void createStorageOptionsMap(final SqlNodeList args) {
    if (args == null || args.size() == 0) {
      return;
    }

    final ImmutableMap.Builder<String, Object> storageOptions = ImmutableMap.builder();
    for (SqlNode operand : args) {
      if (operand.getKind() != SqlKind.ARGUMENT_ASSIGNMENT) {
        throw UserException.unsupportedError()
            .message(
                "Unsupported argument type. Only assignment arguments (param => value) are supported.")
            .build(logger);
      }
      final List<SqlNode> operandList = ((SqlCall) operand).getOperandList();

      final String name = ((SqlIdentifier) operandList.get(1)).getSimple();
      SqlNode literal = operandList.get(0);
      if (!(literal instanceof SqlLiteral)) {
        throw UserException.unsupportedError()
            .message("Only literals are accepted for storage option values")
            .build(logger);
      }

      Object value = ((SqlLiteral) literal).getValue();
      if (value instanceof NlsString) {
        value = ((NlsString) value).getValue();
      }
      storageOptions.put(name, value);
    }

    this.storageOptionsMap = storageOptions.build();
  }

  public static boolean isStoreAsOptionIceberg(Map<String, Object> storageOptionsMap) {

    if (storageOptionsMap != null) {
      return storageOptionsMap.get("type").equals("iceberg") && storageOptionsMap.size() == 1;
    }

    return false;
  }

  public static boolean isStorageFormatIceberg(
      SourceCatalog sourceCatalog, NamespaceKey path, Map<String, Object> storageOptionsMap) {
    if (storageOptionsMap == null) {
      return "iceberg".equals(getDefaultCtasFormat(sourceCatalog, path));

    } else {
      return isStoreAsOptionIceberg(storageOptionsMap);
    }
  }

  public static boolean validatePath(SourceCatalog sourceCatalog, NamespaceKey path) {
    try {
      sourceCatalog.getSource(path.getRoot());
    } catch (UserException uex) {
      return false;
    }
    return true;
  }

  public static String getDefaultCtasFormat(SourceCatalog sourceCatalog, NamespaceKey path) {
    StoragePlugin storagePlugin = sourceCatalog.getSource(path.getRoot());
    Preconditions.checkState(storagePlugin instanceof MutablePlugin, "Source is not mutable");
    return ((MutablePlugin) storagePlugin).getDefaultCtasFormat();
  }

  @VisibleForTesting
  public void validateTableFormatOptions(
      Catalog catalog,
      CatalogEntityKey catalogEntityKey,
      OptionManager options,
      ResolvedVersionContext resolvedVersionContext) {
    if (isCreate()) {
      boolean isStorageIceberg =
          isStorageFormatIceberg(catalog, catalogEntityKey.toNamespaceKey(), storageOptionsMap);
      if (isStorageIceberg) {
        resetStorageOptions();
      }
      boolean isIcebergTableSupported =
          IcebergUtils.isIcebergDMLFeatureEnabled(
                  catalog, catalogEntityKey.toNamespaceKey(), options, storageOptionsMap)
              && IcebergUtils.validatePluginSupportForIceberg(
                  catalog, catalogEntityKey.toNamespaceKey());
      isIcebergTable = isIcebergTableSupported && isStorageIceberg;
      if (!isIcebergTableSupported && isStorageIceberg) {
        logger.warn(
            "Please enable required support options to perform create operation in specified/default iceberg format for {}.",
            catalogEntityKey.toNamespaceKey().toString());
      }
    } else {
      isIcebergTable =
          IcebergUtils.isIcebergDMLFeatureEnabled(
                  catalog, catalogEntityKey.toNamespaceKey(), options, storageOptionsMap)
              && IcebergUtils.validatePluginSupportForIceberg(
                  catalog, catalogEntityKey.toNamespaceKey());
    }
    if (resolvedVersionContext != null) {
      CatalogUtil.validateResolvedVersionIsBranch(resolvedVersionContext);
      isVersionedTable =
          CatalogUtil.requestedPluginSupportsVersionedTables(
              catalogEntityKey.toNamespaceKey(), catalog);
      isIcebergTable = isVersionedTable;
    }
  }

  private void resetStorageOptions() {
    storageOptionsMap = null;
  }

  public static void validateCreateTableLocation(
      SourceCatalog sourceCatalog, NamespaceKey path, SqlCreateEmptyTable sqlCreateEmptyTable) {
    if (sqlCreateEmptyTable.getLocation() != null) {
      StoragePlugin storagePlugin = sourceCatalog.getSource(path.getRoot());
      if (storagePlugin instanceof FileSystemPlugin) {
        throw UserException.parseError()
            .message("LOCATION clause is not supported in the query for this source")
            .build(logger);
      }
    }
  }

  @Override
  public String getTextPlan() {
    return textPlan;
  }

  @Override
  public Rel getLogicalPlan() {
    return drel;
  }

  protected ResolvedVersionContext getResolvedVersionContextIfVersioned(
      CatalogEntityKey key, Catalog catalog) {
    if (CatalogUtil.requestedPluginSupportsVersionedTables(key.getRootEntity(), catalog)) {
      return CatalogUtil.resolveVersionContext(
          catalog, key.getRootEntity(), key.getTableVersionContext().asVersionContext());
    }
    return null;
  }

  protected Supplier<DremioTable> dremioTableSupplier(
      Catalog catalog, CatalogEntityKey catalogEntityKey) {
    if (dremioTable == null) {
      dremioTable = Suppliers.memoize(() -> catalog.getTable(catalogEntityKey));
    }
    return dremioTable;
  }
}
