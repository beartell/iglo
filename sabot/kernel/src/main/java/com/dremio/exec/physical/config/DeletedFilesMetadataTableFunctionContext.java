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
package com.dremio.exec.physical.config;

import com.dremio.common.expression.SchemaPath;
import com.dremio.exec.catalog.StoragePluginId;
import com.dremio.exec.record.BatchSchema;
import com.dremio.exec.store.OperationType;
import com.dremio.exec.store.ScanFilter;
import com.dremio.service.namespace.dataset.proto.UserDefinedSchemaSettings;
import com.dremio.service.namespace.file.proto.FileConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.protostuff.ByteString;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("deleted-files-metadata-table-function")
public class DeletedFilesMetadataTableFunctionContext extends TableFunctionContext {

  private final OperationType operationType;

  public DeletedFilesMetadataTableFunctionContext(
      @JsonProperty("operationType") OperationType operationType,
      @JsonProperty("formatSettings") FileConfig formatSettings,
      @JsonProperty("schema") BatchSchema fullSchema,
      @JsonProperty("tableschema") BatchSchema tableSchema,
      @JsonProperty("referencedTables") List<List<String>> tablePath,
      @JsonProperty("scanFilter") ScanFilter scanFilter,
      @JsonProperty("pluginId") StoragePluginId pluginId,
      @JsonProperty("internalTablePluginId") StoragePluginId internalTablePluginId,
      @JsonProperty("columns") List<SchemaPath> columns,
      @JsonProperty("partitionColumns") List<String> partitionColumns,
      @JsonProperty("extendedProperty") ByteString extendedProperty,
      @JsonProperty("arrowCachingEnabled") boolean arrowCachingEnabled,
      @JsonProperty("convertedIcebergDataset") boolean isConvertedIcebergDataset,
      @JsonProperty("icebergMetadata") boolean isIcebergMetadata,
      @JsonProperty("userDefinedSchemaSettings")
          UserDefinedSchemaSettings userDefinedSchemaSettings) {
    super(
        formatSettings,
        fullSchema,
        tableSchema,
        tablePath,
        scanFilter,
        null,
        pluginId,
        internalTablePluginId,
        columns,
        partitionColumns,
        extendedProperty,
        arrowCachingEnabled,
        isConvertedIcebergDataset,
        isIcebergMetadata,
        userDefinedSchemaSettings);
    this.operationType = operationType;
  }

  public OperationType getOperationType() {
    return operationType;
  }
}
