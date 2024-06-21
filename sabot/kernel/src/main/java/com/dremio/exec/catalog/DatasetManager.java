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
package com.dremio.exec.catalog;

import static com.dremio.exec.catalog.CatalogUtil.permittedNessieKey;
import static com.dremio.exec.catalog.VersionedDatasetId.isVersionedDatasetId;
import static com.dremio.exec.planner.physical.PlannerSettings.FULL_NESTED_SCHEMA_SUPPORT;

import com.dremio.catalog.model.dataset.TableVersionContext;
import com.dremio.common.exceptions.UserException;
import com.dremio.common.utils.PathUtils;
import com.dremio.connector.ConnectorException;
import com.dremio.connector.impersonation.extensions.SupportsImpersonation;
import com.dremio.connector.metadata.DatasetHandle;
import com.dremio.connector.metadata.DatasetMetadata;
import com.dremio.connector.metadata.PartitionChunkListing;
import com.dremio.connector.metadata.SourceMetadata;
import com.dremio.datastore.SearchQueryUtils;
import com.dremio.datastore.api.LegacyIndexedStore.LegacyFindByCondition;
import com.dremio.exec.catalog.CatalogImpl.IdentityResolver;
import com.dremio.exec.catalog.ManagedStoragePlugin.MetadataAccessType;
import com.dremio.exec.catalog.conf.ConnectionConf;
import com.dremio.exec.dotfile.View;
import com.dremio.exec.planner.logical.ViewTable;
import com.dremio.exec.planner.sql.CalciteArrowHelper;
import com.dremio.exec.record.BatchSchema;
import com.dremio.exec.store.DatasetRetrievalOptions;
import com.dremio.exec.store.NamespaceTable;
import com.dremio.exec.store.NessieReferenceException;
import com.dremio.exec.store.SchemaConfig;
import com.dremio.exec.store.StoragePlugin;
import com.dremio.exec.store.TableMetadata;
import com.dremio.exec.store.Views;
import com.dremio.exec.store.dfs.FileSystemPlugin;
import com.dremio.exec.store.dfs.ImpersonationConf;
import com.dremio.exec.util.ViewFieldsHelper;
import com.dremio.options.OptionManager;
import com.dremio.service.namespace.DatasetHelper;
import com.dremio.service.namespace.DatasetMetadataSaver;
import com.dremio.service.namespace.NamespaceAttribute;
import com.dremio.service.namespace.NamespaceException;
import com.dremio.service.namespace.NamespaceIndexKeys;
import com.dremio.service.namespace.NamespaceKey;
import com.dremio.service.namespace.NamespaceNotFoundException;
import com.dremio.service.namespace.NamespaceOptions;
import com.dremio.service.namespace.NamespaceService;
import com.dremio.service.namespace.NamespaceUtils;
import com.dremio.service.namespace.dataset.proto.DatasetConfig;
import com.dremio.service.namespace.dataset.proto.DatasetType;
import com.dremio.service.namespace.proto.EntityId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The workhorse of Catalog, responsible for retrieving datasets and interacting with sources as
 * necessary to complete a query.
 *
 * <p>This operates entirely in the context of the user's namespace with the exception of the
 * DatasetSaver, which hides access to the SystemUser namespace solely for purposes of retrieving
 * datasets.
 */
class DatasetManager {

  private static final Logger logger = LoggerFactory.getLogger(DatasetManager.class);

  private final PluginRetriever plugins;
  private final NamespaceService userNamespaceService;
  private final OptionManager optionManager;
  private final String userName;
  private final IdentityResolver identityProvider;
  private final VersionContextResolver versionContextResolver;
  private final VersionedDatasetAdapterFactory versionedDatasetAdapterFactory;

  public DatasetManager(
      PluginRetriever plugins,
      NamespaceService userNamespaceService,
      OptionManager optionManager,
      String userName,
      IdentityResolver identityProvider,
      VersionContextResolver versionContextResolver,
      VersionedDatasetAdapterFactory versionedDatasetAdapterFactory) {
    this.userNamespaceService = userNamespaceService;
    this.plugins = plugins;
    this.optionManager = optionManager;
    this.userName = userName;
    this.identityProvider = identityProvider;
    this.versionContextResolver = versionContextResolver;
    this.versionedDatasetAdapterFactory = versionedDatasetAdapterFactory;
  }

  /**
   * A path is ambiguous if it has two parts and the root contains a period. In this case, we don't
   * know whether the root should be considered a single part or multiple parts and need to do a
   * search for an unescaped path rather than a lookup.
   *
   * <p>This is because JDBC & ODBC tools use a two part naming scheme and thus we also present
   * Dremio datasets using this two part scheme where all parts of the path except the leaf are
   * presented as part of the schema of the table.
   *
   * @param key Key to test
   * @return Whether path is ambiguous.
   */
  private boolean isAmbiguousKey(NamespaceKey key) {
    if (key.size() != 2) {
      return false;
    }

    return key.getRoot().contains(".");
  }

  private DatasetConfig getConfig(final NamespaceKey key) {
    if (!isAmbiguousKey(key)) {
      try {
        return userNamespaceService.getDataset(key);
      } catch (NamespaceNotFoundException ex) {
        return null;
      } catch (NamespaceException ex) {
        throw Throwables.propagate(ex);
      }
    }

    /**
     * If we have an ambiguous key, let's search for possible matches in the namespace.
     *
     * <p>From there we will: - only consider keys that have the same leaf value (since ambiguity
     * isn't allowed there) - return the first key by ordering the keys by segment (cis then cs).
     */
    final LegacyFindByCondition condition = new LegacyFindByCondition();
    condition.setCondition(
        SearchQueryUtils.newTermQuery(
            NamespaceIndexKeys.UNQUOTED_LC_PATH, key.toUnescapedString().toLowerCase()));
    // do a case insensitive order first.
    // do a case sensitive order second.
    return StreamSupport.stream(userNamespaceService.find(condition).spliterator(), false)
        .filter(entry -> entry.getKey().getLeaf().equalsIgnoreCase(key.getLeaf()))
        .map(input -> input.getValue().getDataset())
        .min(
            (o1, o2) -> {
              List<String> p1 = o1.getFullPathList();
              List<String> p2 = o2.getFullPathList();

              final int size = Math.max(p1.size(), p2.size());

              for (int i = 0; i < size; i++) {

                // do a case insensitive order first.
                int cmp = p1.get(i).toLowerCase().compareTo(p2.get(i).toLowerCase());
                if (cmp != 0) {
                  return cmp;
                }

                // do a case sensitive order second.
                cmp = p1.get(i).compareTo(p2.get(i));
                if (cmp != 0) {
                  return cmp;
                }
              }

              Preconditions.checkArgument(
                  p1.size() == p2.size(),
                  "Two keys were indexed the same but had different lengths. %s, %s",
                  p1,
                  p2);
              return 0;
            })
        .orElse(null);
  }

  private DatasetConfig getConfig(final String datasetId) {
    return userNamespaceService.findDatasetByUUID(datasetId);
  }

  private NamespaceKey getCanonicalKey(NamespaceKey key) {
    Preconditions.checkArgument(isAmbiguousKey(key), "%s should be ambiguous", key.getSchemaPath());
    List<String> pathComponents = key.getPathComponents();
    List<String> entries = new ArrayList<>();
    for (String component : pathComponents) {
      entries.addAll(Arrays.asList(component.split("\\.(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")));
    }
    return new NamespaceKey(entries);
  }

  @WithSpan
  public DremioTable getTable(
      NamespaceKey key, MetadataRequestOptions options, boolean ignoreColumnCount) {
    final DatasetConfig config = getConfig(key);

    if (config != null) {
      // canonicalize the path.
      key = new NamespaceKey(config.getFullPathList());
    }

    if (isAmbiguousKey(key)) {
      key = getCanonicalKey(key);
    }

    String pluginName = key.getRoot();
    final ManagedStoragePlugin plugin = plugins.getPlugin(pluginName, false);

    if (config == null) {
      logger.debug("Got a null config");
    } else {
      logger.debug("Got config id {}", config.getId());
    }

    if (plugin != null) {

      // if we have a plugin and the info isn't a vds (this happens in home, where VDS are
      // intermingled with plugin datasets).
      if (config == null || config.getType() != DatasetType.VIRTUAL_DATASET) {
        return getTableFromPlugin(key, config, plugin, options, ignoreColumnCount);
      }
    }

    if (config == null) {
      return null;
    }

    // at this point, we should only be looking at virtual datasets.
    if (config.getType() != DatasetType.VIRTUAL_DATASET) {
      // if we're not looking at a virtual dataset, it must mean that we hit a race condition where
      // the source has been removed but the dataset was retrieved just before.
      return null;
    }

    return createTableFromVirtualDataset(config, options);
  }

  public DremioTable getTable(String datasetId, MetadataRequestOptions options) {
    final DatasetConfig config = getConfig(datasetId);

    if (config == null) {
      if (isVersionedDatasetId(datasetId)) {
        Span.current().setAttribute("dremio.catalog.getTable.isVersionedDatasetId", true);
        // try lookup in external catalog
        return getTableFromNessieCatalog(datasetId, options);
      } else {
        return null;
      }
    }
    Span.current().setAttribute("dremio.catalog.getTable.isVersionedDatasetId", false);

    NamespaceKey key = new NamespaceKey(config.getFullPathList());

    return getTable(key, options, false);
  }

  @WithSpan
  private DremioTable getTableFromNessieCatalog(String datasetId, MetadataRequestOptions options) {
    VersionedDatasetId versionedDatasetId = null;
    try {
      versionedDatasetId = VersionedDatasetId.fromString(datasetId);
    } catch (JsonProcessingException e) {
      logger.debug("Could not parse VersionedDatasetId from string : {}", datasetId, e);
      return null;
    }

    List<String> tableKey = versionedDatasetId.getTableKey();
    TableVersionContext versionContext = versionedDatasetId.getVersionContext();
    String pluginName = tableKey.get(0);
    final ManagedStoragePlugin plugin = plugins.getPlugin(pluginName, false);
    if (plugin == null) {
      return null;
    }
    MetadataRequestOptions optionsWithVersion =
        options.cloneWith(tableKey.get(0), versionContext.asVersionContext());
    DremioTable table =
        getTableFromNessieCatalog(new NamespaceKey(tableKey), plugin, optionsWithVersion);
    if (table != null) {
      // check for ContentId . If someone has dropped and recreated the table with the same key
      // in the same VersionContext , the ContentId will be different
      VersionedDatasetId returnedVersionedDatasetId = null;
      try {
        returnedVersionedDatasetId =
            VersionedDatasetId.fromString(table.getDatasetConfig().getId().getId());
      } catch (JsonProcessingException e) {
        logger.debug(
            "Could not parse VersionedDatasetId from string : {}",
            table.getDatasetConfig().getId().getId(),
            e);
        return null;
      }
      if (!returnedVersionedDatasetId.getContentId().equals(versionedDatasetId.getContentId())) {
        logger.debug(
            "ContentId mismatch. VersionedDatasetId in : {} : VersionedDatasetId out : {}",
            versionedDatasetId.asString(),
            returnedVersionedDatasetId.asString());
        return null;
      }
    }
    return table;
  }

  @WithSpan
  private NamespaceTable getTableFromNamespace(
      NamespaceKey key,
      DatasetConfig datasetConfig,
      ManagedStoragePlugin plugin,
      MetadataRequestOptions options) {
    final String accessUserName = getAccessUserName(plugin, options.getSchemaConfig());

    Span.current().setAttribute("dremio.namespace.key.schemapath", key.getSchemaPath());
    plugin.checkAccess(key, datasetConfig, accessUserName, options);

    if (plugin.getPlugin() instanceof FileSystemPlugin) {
      // DX-84516: Need to verify here and not just when accessing the path from FS plugin because
      // the user may have
      // already created/promoted a table they should not have access to - so we need to block the
      // direct NS access too
      List<String> pathComponents = key.getPathComponents();
      PathUtils.verifyNoDirectoryTraversal(
          pathComponents,
          () ->
              UserException.permissionError()
                  .message("Not allowed to perform directory traversal")
                  .addContext("Path", pathComponents.toString())
                  .buildSilently());
    }

    final TableMetadata tableMetadata =
        new TableMetadataImpl(
            plugin.getId(),
            datasetConfig,
            accessUserName,
            DatasetSplitsPointer.of(userNamespaceService, datasetConfig),
            getPrimaryKey(plugin.getPlugin(), datasetConfig, options.getSchemaConfig(), key, true));
    return new NamespaceTable(
        tableMetadata,
        plugin.getDatasetMetadataState(datasetConfig),
        optionManager.getOption(FULL_NESTED_SCHEMA_SUPPORT));
  }

  private List<String> getPrimaryKey(
      StoragePlugin plugin,
      DatasetConfig datasetConfig,
      SchemaConfig config,
      NamespaceKey key,
      boolean saveInKvStore) {
    List<String> primaryKey = null;
    if (plugin instanceof MutablePlugin) {
      MutablePlugin mutablePlugin = (MutablePlugin) plugin;
      try {
        primaryKey = mutablePlugin.getPrimaryKey(key, datasetConfig, config, null, saveInKvStore);
      } catch (Exception ex) {
        logger.debug("Failed to get primary key", ex);
      }
    }
    return primaryKey;
  }

  /** Retrieves a source table, checking that things are up to date. */
  @WithSpan
  private DremioTable getTableFromPlugin(
      NamespaceKey key,
      DatasetConfig datasetConfig,
      ManagedStoragePlugin plugin,
      MetadataRequestOptions options,
      boolean ignoreColumnCount) {
    // Special case 1: versioned plug-in.
    final StoragePlugin underlyingPlugin = plugin.getPlugin();
    if (underlyingPlugin != null && underlyingPlugin.isWrapperFor(VersionedPlugin.class)) {
      return getTableFromNessieCatalog(key, plugin, options);
    }

    // Special case 2: key is for a view.
    // TODO: move views to namespace and out of filesystem.
    if (datasetConfig == null) {
      try {
        ViewTable view = plugin.getView(key, options);
        if (view != null) {
          return view;
        }
      } catch (UserException ex) {
        throw ex;
      } catch (Exception ex) {
        logger.warn("Exception while trying to read view.", ex);
        return null;
      }
    }

    // Check completeness and validity of table metadata and get it.
    final Stopwatch stopwatch = Stopwatch.createStarted();
    if (plugin.checkValidity(datasetConfig, options)) {
      NamespaceTable table = getTableFromNamespace(key, datasetConfig, plugin, options);
      options
          .getStatsCollector()
          .addDatasetStat(
              new NamespaceKey(datasetConfig.getFullPathList()).getSchemaPath(),
              MetadataAccessType.CACHED_METADATA.name(),
              stopwatch.elapsed(TimeUnit.MILLISECONDS));
      return table;
    }

    // If only the cached version is needed, check and return when no entry is found
    if (options.neverPromote()) {
      return null;
    }

    // Get dataset handle from the plugin.
    if (datasetConfig != null) {
      // canonicalize key if we can.
      key = new NamespaceKey(datasetConfig.getFullPathList());
    }
    final Optional<DatasetHandle> handle;
    try {
      handle =
          plugin.getDatasetHandle(
              key, datasetConfig, getDatasetRetrievalOptions(plugin, options, ignoreColumnCount));
    } catch (ConnectorException e) {
      throw UserException.validationError(e)
          .message("Failure while retrieving dataset [%s].", key)
          .build(logger);
    }
    if (!handle.isPresent()) {
      return null;
    }

    final NamespaceKey canonicalKey =
        MetadataObjectsUtils.toNamespaceKey(handle.get().getDatasetPath());
    if (datasetConfig == null && !canonicalKey.equals(key)) {
      // before we do anything with this accessor, we should reprobe namespace as it is possible
      // that the request the
      // user made was not the canonical key and therefore we missed when trying to retrieve data
      // from the namespace.

      try {
        datasetConfig = userNamespaceService.getDataset(canonicalKey);
        if (plugin.checkValidity(datasetConfig, options)) {
          NamespaceTable table =
              getTableFromNamespace(canonicalKey, datasetConfig, plugin, options);
          options
              .getStatsCollector()
              .addDatasetStat(
                  new NamespaceKey(datasetConfig.getFullPathList()).getSchemaPath(),
                  MetadataAccessType.CACHED_METADATA.name(),
                  stopwatch.elapsed(TimeUnit.MILLISECONDS));
          return table;
        }
      } catch (NamespaceException e) {
        // ignore, we'll fall through.
      }
    }

    // arriving here means that the metadata for the table is either incomplete, missing or out of
    // date. We need
    // to save it and return the updated data.
    return refreshMetadataAndGetTable(
        stopwatch, canonicalKey, datasetConfig, handle.get(), plugin, options, ignoreColumnCount);
  }

  /**
   * The method reads metadata (schema and other) from source via {@link ManagedStoragePlugin} and
   * creates and instance of {@link DremioTable} from result.
   *
   * <p>This could be expensive as it runs SQL command, which may spin up new executors.
   */
  @WithSpan
  private DremioTable refreshMetadataAndGetTable(
      Stopwatch stopwatch,
      NamespaceKey key,
      DatasetConfig datasetConfig,
      DatasetHandle handle,
      ManagedStoragePlugin plugin,
      MetadataRequestOptions options,
      boolean ignoreColumnCount) {

    final NamespaceKey canonicalKey = MetadataObjectsUtils.toNamespaceKey(handle.getDatasetPath());

    boolean opportunisticSave = (datasetConfig == null);
    if (opportunisticSave) {
      datasetConfig = MetadataObjectsUtils.newShallowConfig(handle);
    }
    logger.debug("Attempting inline refresh for  key : {} , canonicalKey : {} ", key, canonicalKey);
    try {
      plugin
          .getSaver()
          .save(
              datasetConfig,
              handle,
              plugin.unwrap(StoragePlugin.class),
              opportunisticSave,
              getDatasetRetrievalOptions(plugin, options, ignoreColumnCount),
              userName);
    } catch (ConcurrentModificationException cme) {
      // Some other query, or perhaps the metadata refresh, must have already created this dataset.
      // Re-obtain it
      // from the namespace
      assert opportunisticSave : "Non-opportunistic saves should have already handled a CME";
      try {
        datasetConfig = userNamespaceService.getDataset(canonicalKey);
      } catch (NamespaceException e) {
        // We got a concurrent modification exception because a dataset existed. It shouldn't be the
        // case that it
        // no longer exists. In the very rare case of this code racing with both another update
        // *and* a dataset deletion
        // we should act as if the delete won
        logger.warn("Unable to obtain dataset {}. Likely race with dataset deletion", canonicalKey);
        return null;
      }
      final NamespaceTable namespaceTable =
          getTableFromNamespace(key, datasetConfig, plugin, options);
      options
          .getStatsCollector()
          .addDatasetStat(
              canonicalKey.getSchemaPath(),
              MetadataAccessType.CACHED_METADATA.name(),
              stopwatch.elapsed(TimeUnit.MILLISECONDS));
      return namespaceTable;
    } catch (AccessControlException ignored) {
      return null;
    }

    options
        .getStatsCollector()
        .addDatasetStat(
            canonicalKey.getSchemaPath(),
            MetadataAccessType.PARTIAL_METADATA.name(),
            stopwatch.elapsed(TimeUnit.MILLISECONDS));

    final SchemaConfig schemaConfig = options.getSchemaConfig();
    final String accessUserName = getAccessUserName(plugin, schemaConfig);

    plugin.checkAccess(canonicalKey, datasetConfig, accessUserName, options);

    // TODO: use MaterializedSplitsPointer if metadata is not too big!
    final TableMetadata tableMetadata =
        new TableMetadataImpl(
            plugin.getId(),
            datasetConfig,
            accessUserName,
            DatasetSplitsPointer.of(userNamespaceService, datasetConfig),
            getPrimaryKey(
                plugin.getPlugin(),
                datasetConfig,
                schemaConfig,
                key,
                false /* The metadata for the table is either incomplete, missing or out of date.
                      Storing in namespace can cause issues if the metadata is missing. Don't save here.
                      */));
    return new NamespaceTable(
        tableMetadata,
        plugin.getDatasetMetadataState(datasetConfig),
        optionManager.getOption(FULL_NESTED_SCHEMA_SUPPORT));
  }

  private static DatasetRetrievalOptions getDatasetRetrievalOptions(
      ManagedStoragePlugin plugin, MetadataRequestOptions options, boolean ignoreColumnCount) {
    return plugin.getDefaultRetrievalOptions().toBuilder()
        .setIgnoreAuthzErrors(options.getSchemaConfig().getIgnoreAuthErrors())
        .setMaxMetadataLeafColumns(
            (ignoreColumnCount)
                ? Integer.MAX_VALUE
                : plugin.getDefaultRetrievalOptions().maxMetadataLeafColumns())
        .setMaxNestedLevel(plugin.getDefaultRetrievalOptions().maxNestedLevel())
        .build();
  }

  /** Return whether or not saves are allowed */
  protected boolean checkCanSave(NamespaceKey key) {
    return true;
  }

  // Figure out the user we want to access the source with.  If the source supports impersonation we
  // allow it to
  // override the delegated username.
  private String getAccessUserName(ManagedStoragePlugin plugin, SchemaConfig schemaConfig) {
    final String accessUserName;

    ConnectionConf<?, ?> conf = plugin.getConnectionConf();
    if (conf instanceof ImpersonationConf) {
      if (plugin.getPlugin() instanceof SupportsImpersonation
          && !(schemaConfig.getAuthContext().getSubject() instanceof CatalogUser)) {
        if (((SupportsImpersonation) plugin.getPlugin()).isImpersonationEnabled()) {
          throw UserException.unsupportedError(
                  new InvalidImpersonationTargetException(
                      "Only users can be used to connect to impersonation enabled sources."))
              .buildSilently();
        }
      }

      String queryUser = null;
      if (schemaConfig.getViewExpansionContext() != null) {
        queryUser = schemaConfig.getViewExpansionContext().getQueryUser().getName();
      }
      accessUserName =
          ((ImpersonationConf) conf).getAccessUserName(schemaConfig.getUserName(), queryUser);
    } else {
      accessUserName = schemaConfig.getUserName();
    }

    return accessUserName;
  }

  @WithSpan("create-table-from-view")
  private ViewTable createTableFromVirtualDataset(
      DatasetConfig datasetConfig, MetadataRequestOptions options) {
    try {
      // 1.4.0 and earlier didn't correctly save virtual dataset schema information.
      BatchSchema schema =
          DatasetHelper.getSchemaBytes(datasetConfig) != null
              ? CalciteArrowHelper.fromDataset(datasetConfig)
              : null;

      View view =
          Views.fieldTypesToView(
              Iterables.getLast(datasetConfig.getFullPathList()),
              datasetConfig.getVirtualDataset().getSql(),
              ViewFieldsHelper.getViewFields(datasetConfig),
              datasetConfig.getVirtualDataset().getContextList(),
              options.getSchemaConfig().getOptions() != null
                      && options
                          .getSchemaConfig()
                          .getOptions()
                          .getOption(FULL_NESTED_SCHEMA_SUPPORT)
                  ? schema
                  : null);

      return new ViewTable(
          new NamespaceKey(datasetConfig.getFullPathList()),
          view,
          identityProvider.getOwner(datasetConfig.getFullPathList()),
          datasetConfig,
          schema);
    } catch (Exception e) {
      throw new RuntimeException(
          String.format(
              "Failure while constructing the ViewTable from datasetConfig for key %s with datasetId %s",
              String.join(".", datasetConfig.getFullPathList()), datasetConfig.getId().getId()),
          e);
    }
  }

  @WithSpan
  private DremioTable getTableFromNessieCatalog(
      NamespaceKey key, ManagedStoragePlugin plugin, MetadataRequestOptions options) {
    final String accessUserName = options.getSchemaConfig().getUserName();
    final StoragePlugin underlyingPlugin = plugin.getPlugin();
    VersionedDatasetAdapter versionedDatasetAdapter;
    if (!permittedNessieKey(key)) {
      return null;
    }
    try {
      versionedDatasetAdapter =
          versionedDatasetAdapterFactory.newInstance(
              key.getPathComponents(),
              versionContextResolver.resolveVersionContext(
                  plugin.getName().getRoot(),
                  options.getVersionForSource(plugin.getName().getRoot(), key)),
              underlyingPlugin,
              plugin.getId(),
              optionManager);
    } catch (NessieReferenceException e) {
      logger.debug("Unable to retrieve table metadata for {} ", key, e);
      // Any error in resolving the version context returns a NessieReferenceException with a
      // detailed error.
      // We log that and return null to indicate that the table cannot be found.
      return null;
    }

    if (versionedDatasetAdapter == null) {
      return null;
    }
    return versionedDatasetAdapter.getTable(accessUserName);
  }

  private static boolean isFSBasedDataset(DatasetConfig datasetConfig) {
    return datasetConfig.getType() == DatasetType.PHYSICAL_DATASET_HOME_FILE
        || datasetConfig.getType() == DatasetType.PHYSICAL_DATASET_SOURCE_FILE
        || datasetConfig.getType() == DatasetType.PHYSICAL_DATASET_SOURCE_FOLDER
        || datasetConfig.getType() == DatasetType.PHYSICAL_DATASET_HOME_FOLDER;
  }

  public boolean createOrUpdateDataset(
      ManagedStoragePlugin plugin,
      NamespaceKey datasetPath,
      DatasetConfig newConfig,
      NamespaceAttribute... attributes)
      throws NamespaceException {

    if (!isFSBasedDataset(newConfig)) {
      return userNamespaceService.tryCreatePhysicalDataset(datasetPath, newConfig, attributes);
    }

    DatasetConfig currentConfig = null;
    try {
      currentConfig = userNamespaceService.getDataset(datasetPath);
    } catch (NamespaceNotFoundException nfe) {
      // ignore
    }

    // if format settings did not change fall back to namespace based update
    if (currentConfig != null
        && currentConfig
            .getPhysicalDataset()
            .getFormatSettings()
            .equals(newConfig.getPhysicalDataset().getFormatSettings())) {
      return userNamespaceService.tryCreatePhysicalDataset(datasetPath, newConfig, attributes);
    }

    final DatasetRetrievalOptions retrievalOptions = plugin.getDefaultRetrievalOptions();
    try {
      // for home files dataset path is location of file not path in namespace.
      if (newConfig.getType() == DatasetType.PHYSICAL_DATASET_HOME_FILE) {
        final NamespaceKey pathInHome =
            new NamespaceKey(
                ImmutableList.<String>builder()
                    .add("__home") // TODO (AH) hack.
                    .addAll(
                        PathUtils.toPathComponents(
                            newConfig.getPhysicalDataset().getFormatSettings().getLocation()))
                    .build());

        final Optional<DatasetHandle> handle =
            getHandle(plugin, pathInHome, newConfig, retrievalOptions);
        if (!handle.isPresent()) {
          // setting format setting on empty folders?
          return userNamespaceService.tryCreatePhysicalDataset(datasetPath, newConfig, attributes);
        }

        saveInHomeSpace(
            userNamespaceService,
            plugin.unwrap(StoragePlugin.class),
            handle.get(),
            retrievalOptions,
            newConfig);
        return true;
      }

      final Optional<DatasetHandle> handle =
          getHandle(plugin, datasetPath, newConfig, retrievalOptions);
      if (!handle.isPresent()) {
        // setting format setting on empty folders?
        return userNamespaceService.tryCreatePhysicalDataset(datasetPath, newConfig, attributes);
      }

      NamespaceUtils.copyFromOldConfig(currentConfig, newConfig);
      plugin
          .getSaver()
          .save(
              newConfig,
              handle.get(),
              plugin.unwrap(StoragePlugin.class),
              false,
              retrievalOptions,
              userName,
              attributes);
    } catch (Exception e) {
      Throwables.propagateIfPossible(e, NamespaceException.class);
      throw new RuntimeException("Failed to get new dataset ", e);
    }
    return true;
  }

  private static Optional<DatasetHandle> getHandle(
      ManagedStoragePlugin plugin,
      NamespaceKey key,
      DatasetConfig datasetConfig,
      DatasetRetrievalOptions retrievalOptions) {
    try {
      return plugin.getDatasetHandle(key, datasetConfig, retrievalOptions);
    } catch (ConnectorException e) {
      throw UserException.validationError(e)
          .message("Failure while retrieving dataset [%s].", key)
          .build(logger);
    }
  }

  public void createDataset(
      NamespaceKey key,
      ManagedStoragePlugin plugin,
      Function<DatasetConfig, DatasetConfig> datasetMutator) {
    DatasetConfig config;
    try {
      config = userNamespaceService.getDataset(key);

      if (config != null) {
        throw UserException.validationError()
            .message("Table already exists %s", key.getRoot())
            .build(logger);
      }
    } catch (NamespaceException ex) {
      logger.debug(
          "Failure while trying to retrieve dataset for key {}. Exception: {}",
          key,
          ex.getLocalizedMessage());
    }

    final DatasetRetrievalOptions retrievalOptions = plugin.getDefaultRetrievalOptions();
    final Optional<DatasetHandle> handle;
    try {
      handle = plugin.getDatasetHandle(key, null, retrievalOptions);
    } catch (ConnectorException e) {
      throw UserException.validationError(e)
          .message("Failure while retrieving dataset [%s].", key)
          .build(logger);
    }

    if (!handle.isPresent()) {
      throw UserException.validationError()
          .message("Unable to find requested dataset %s.", key)
          .build(logger);
    }

    config = MetadataObjectsUtils.newShallowConfig(handle.get());

    plugin
        .getSaver()
        .save(
            config,
            handle.get(),
            plugin.unwrap(StoragePlugin.class),
            false,
            retrievalOptions,
            datasetMutator::apply);
  }

  private void saveInHomeSpace(
      NamespaceService userNamespace,
      SourceMetadata sourceMetadata,
      DatasetHandle handle,
      DatasetRetrievalOptions options,
      DatasetConfig nsConfig) {
    Preconditions.checkNotNull(nsConfig);
    final NamespaceKey key = new NamespaceKey(nsConfig.getFullPathList());

    if (nsConfig.getId() == null) {
      nsConfig.setId(new EntityId(UUID.randomUUID().toString()));
    }

    NamespaceService.SplitCompression splitCompression =
        NamespaceService.SplitCompression.valueOf(
            optionManager.getOption(CatalogOptions.SPLIT_COMPRESSION_TYPE).toUpperCase());
    try (DatasetMetadataSaver saver =
        userNamespace.newDatasetMetadataSaver(
            key,
            nsConfig.getId(),
            splitCompression,
            optionManager.getOption(CatalogOptions.SINGLE_SPLIT_PARTITION_MAX),
            optionManager.getOption(NamespaceOptions.DATASET_METADATA_CONSISTENCY_VALIDATE))) {
      final PartitionChunkListing chunkListing =
          sourceMetadata.listPartitionChunks(handle, options.asListPartitionChunkOptions(nsConfig));

      final long recordCountFromSplits =
          saver == null || chunkListing == null
              ? 0
              : CatalogUtil.savePartitionChunksInSplitsStores(saver, chunkListing);
      final DatasetMetadata datasetMetadata =
          sourceMetadata.getDatasetMetadata(
              handle, chunkListing, options.asGetMetadataOptions(nsConfig));
      MetadataObjectsUtils.overrideExtended(
          nsConfig,
          datasetMetadata,
          Optional.empty(),
          recordCountFromSplits,
          options.maxMetadataLeafColumns());
      saver.saveDataset(nsConfig, false);
    } catch (DatasetMetadataTooLargeException e) {
      nsConfig.setRecordSchema(null);
      nsConfig.setReadDefinition(null);
      try {
        userNamespaceService.addOrUpdateDataset(key, nsConfig);
      } catch (NamespaceException ignored) {
      }
      throw UserException.validationError(e).build(logger);
    } catch (Exception e) {
      logger.warn("Failure while retrieving and saving dataset {}.", key, e);
    }
  }
}
