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
package com.dremio.dac.explore;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dremio.catalog.model.CatalogEntityKey;
import com.dremio.catalog.model.ResolvedVersionContext;
import com.dremio.catalog.model.VersionContext;
import com.dremio.dac.explore.model.DatasetPath;
import com.dremio.dac.server.BufferAllocatorFactory;
import com.dremio.dac.service.datasets.DatasetVersionMutator;
import com.dremio.exec.catalog.Catalog;
import com.dremio.exec.catalog.VersionedPlugin;
import com.dremio.exec.planner.sql.parser.SqlGrant;
import com.dremio.plugins.dataplane.store.DataplanePlugin;
import com.dremio.service.namespace.NamespaceKey;
import com.dremio.service.namespace.dataset.proto.DatasetType;
import java.util.Arrays;
import javax.ws.rs.core.SecurityContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

/** Unit Tests for {@link DatasetResource} */
public class TestDatasetResource {

  @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock private SecurityContext securityContext;

  @Mock private Catalog catalog;

  @Mock private DatasetVersionMutator datasetService;

  @Mock private BufferAllocatorFactory bufferAllocatorFactory;

  @Mock private DataplanePlugin dataplanePlugin;

  private DatasetResource datasetResource;

  private DatasetPath datasetPath;

  @Before
  public void setup() {
    datasetPath = new DatasetPath(Arrays.asList("source", "v1"));
    datasetResource =
        new DatasetResource(
            catalog,
            null,
            datasetService,
            null,
            securityContext,
            null,
            null,
            datasetPath,
            bufferAllocatorFactory);
  }

  @Test
  public void testValidatePrivilegeWithinDroppingViewForVersionedSource() {
    when(catalog.getSource(Mockito.anyString())).thenReturn(dataplanePlugin);
    when(dataplanePlugin.isWrapperFor(VersionedPlugin.class)).thenReturn(true);
    when(catalog.resolveVersionContext(Mockito.anyString(), Mockito.any(VersionContext.class)))
        .thenReturn(ResolvedVersionContext.ofBranch("main", "abc123"));
    when(catalog.getDatasetType(Mockito.any(CatalogEntityKey.class)))
        .thenReturn(DatasetType.VIRTUAL_DATASET);
    try {
      datasetResource.deleteDataset(null, "BRANCH", "main");
    } catch (Exception ex) {
      // ignoring this exception as the test is to verify the catalog.validatePrivilege call
    }
    verify(catalog)
        .validatePrivilege(new NamespaceKey(datasetPath.toPathList()), SqlGrant.Privilege.ALTER);
  }

  @Test
  public void testValidatePrivilegeWithinDroppingTableForVersionedSource() {
    when(catalog.getSource(Mockito.anyString())).thenReturn(dataplanePlugin);
    when(dataplanePlugin.isWrapperFor(VersionedPlugin.class)).thenReturn(true);
    when(catalog.resolveVersionContext(Mockito.anyString(), Mockito.any(VersionContext.class)))
        .thenReturn(ResolvedVersionContext.ofBranch("main", "abc123"));
    when(catalog.getDatasetType(Mockito.any(CatalogEntityKey.class)))
        .thenReturn(DatasetType.PHYSICAL_DATASET);
    try {
      datasetResource.deleteDataset(null, "BRANCH", "main");
    } catch (Exception ex) {
      // ignoring this exception as the test is to verify the catalog.validatePrivilege call
    }
    verify(catalog)
        .validatePrivilege(new NamespaceKey(datasetPath.toPathList()), SqlGrant.Privilege.DROP);
  }
}
