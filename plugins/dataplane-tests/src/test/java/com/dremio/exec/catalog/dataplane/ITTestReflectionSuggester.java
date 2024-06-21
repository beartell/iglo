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
package com.dremio.exec.catalog.dataplane;

import static com.dremio.exec.ExecConstants.LAYOUT_REFRESH_MAX_ATTEMPTS;
import static com.dremio.exec.ExecConstants.PARQUET_MAXIMUM_PARTITIONS_VALIDATOR;
import static com.dremio.exec.catalog.dataplane.test.DataplaneTestDefines.DATAPLANE_PLUGIN_NAME;
import static com.dremio.exec.catalog.dataplane.test.DataplaneTestDefines.DEFAULT_BRANCH_NAME;
import static com.dremio.exec.catalog.dataplane.test.DataplaneTestDefines.createTableAsQuery;
import static com.dremio.exec.catalog.dataplane.test.DataplaneTestDefines.createTagQuery;
import static com.dremio.exec.catalog.dataplane.test.DataplaneTestDefines.generateUniqueTableName;
import static com.dremio.exec.catalog.dataplane.test.DataplaneTestDefines.tablePathWithSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dremio.catalog.model.CatalogEntityKey;
import com.dremio.catalog.model.dataset.TableVersionContext;
import com.dremio.catalog.model.dataset.TableVersionType;
import com.dremio.dac.server.JobsServiceTestUtils;
import com.dremio.exec.catalog.CatalogUtil;
import com.dremio.exec.catalog.DremioTable;
import com.dremio.service.jobs.JobStatusListener;
import com.dremio.service.jobs.JobsService;
import com.dremio.service.namespace.NamespaceKey;
import com.dremio.service.namespace.dataset.proto.DatasetConfig;
import com.dremio.service.reflection.analysis.ReflectionAnalyzer;
import com.dremio.service.reflection.analysis.ReflectionSuggester;
import com.dremio.service.reflection.proto.ReflectionGoal;
import com.dremio.service.reflection.proto.ReflectionType;
import java.util.Collections;
import java.util.List;
import org.apache.arrow.memory.BufferAllocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ITTestReflectionSuggester extends ITBaseTestReflection {

  private BufferAllocator allocator;
  private static final String tableName = generateUniqueTableName();
  private static final List<String> tablePath =
      tablePathWithSource(DATAPLANE_PLUGIN_NAME, Collections.singletonList(tableName));

  @BeforeAll
  public static void testSetup() {
    JobsServiceTestUtils.submitJobAndWaitUntilCompletion(
        l(JobsService.class),
        createNewJobRequestFromSql(createTableAsQuery(Collections.singletonList(tableName), 1000)),
        JobStatusListener.NO_OP);

    JobsServiceTestUtils.submitJobAndWaitUntilCompletion(
        l(JobsService.class),
        createNewJobRequestFromSql(createTagQuery("dev", DEFAULT_BRANCH_NAME)),
        JobStatusListener.NO_OP);
  }

  @BeforeEach
  public void setUp() {
    allocator =
        getSabotContext().getAllocator().newChildAllocator(getClass().getName(), 0, Long.MAX_VALUE);
  }

  @AfterEach
  public void cleanUp() throws Exception {
    resetSystemOption(LAYOUT_REFRESH_MAX_ATTEMPTS.getOptionName());
    resetSystemOption(PARQUET_MAXIMUM_PARTITIONS_VALIDATOR.getOptionName());
    allocator.close();
  }

  /** Verifies reflection suggestions on a versioned table at branch */
  @Test
  public void testBranchSuggestions() {
    // Analyze the table and collect stats
    ReflectionAnalyzer analyzer =
        new ReflectionAnalyzer(getJobsService(), getSabotContext().getCatalogService(), allocator);
    DremioTable table =
        getCatalog()
            .getTable(
                CatalogEntityKey.newBuilder()
                    .keyComponents(tablePath)
                    .tableVersionContext(
                        new TableVersionContext(TableVersionType.BRANCH, DEFAULT_BRANCH_NAME))
                    .build());
    ReflectionAnalyzer.TableStats tableStats =
        analyzer.analyze(table.getDatasetConfig().getId().getId());
    assertEquals(25, tableStats.getCount().longValue());
    assertEquals(2, tableStats.getColumns().size());
    verifyNationKeyColumnStat(tableStats.getColumns().get(0));

    // Verify the agg reflection suggestion
    ReflectionSuggester suggester = new ReflectionSuggester(table.getDatasetConfig(), tableStats);
    List<ReflectionGoal> goals = suggester.getReflectionGoals();
    assertEquals(2, goals.size());
    verifyAggGoal(goals.get(1));
  }

  /** Verifies reflection suggestions on a versioned table at snapshot */
  @Test
  public void testSnapshotSuggestions() {
    // Get the latest snapshot id from the dataset config
    DatasetConfig config = CatalogUtil.getDatasetConfig(getCatalog(), new NamespaceKey(tablePath));
    String snapshotId =
        String.valueOf(config.getPhysicalDataset().getIcebergMetadata().getSnapshotId());

    // Analyze the table and collect stats
    ReflectionAnalyzer analyzer =
        new ReflectionAnalyzer(getJobsService(), getSabotContext().getCatalogService(), allocator);
    DremioTable table =
        getCatalog()
            .getTable(
                CatalogEntityKey.newBuilder()
                    .keyComponents(tablePath)
                    .tableVersionContext(
                        new TableVersionContext(TableVersionType.SNAPSHOT_ID, snapshotId))
                    .build());
    ReflectionAnalyzer.TableStats tableStats =
        analyzer.analyze(table.getDatasetConfig().getId().getId());
    assertEquals(25, tableStats.getCount().longValue());
    assertEquals(2, tableStats.getColumns().size());
    verifyNationKeyColumnStat(tableStats.getColumns().get(0));

    // Verify the agg reflection suggestion
    ReflectionSuggester suggester = new ReflectionSuggester(table.getDatasetConfig(), tableStats);
    List<ReflectionGoal> goals = suggester.getReflectionGoals();
    assertEquals(2, goals.size());
    verifyAggGoal(goals.get(1));
  }

  /** Verifies reflection suggestions on a versioned table at tag */
  @Test
  public void testTagSuggestions() {
    // Analyze the table and collect stats
    ReflectionAnalyzer analyzer =
        new ReflectionAnalyzer(getJobsService(), getSabotContext().getCatalogService(), allocator);
    DremioTable table =
        getCatalog()
            .getTable(
                CatalogEntityKey.newBuilder()
                    .keyComponents(tablePath)
                    .tableVersionContext(new TableVersionContext(TableVersionType.TAG, "dev"))
                    .build());
    ReflectionAnalyzer.TableStats tableStats =
        analyzer.analyze(table.getDatasetConfig().getId().getId());
    assertEquals(25, tableStats.getCount().longValue());
    assertEquals(2, tableStats.getColumns().size());
    verifyNationKeyColumnStat(tableStats.getColumns().get(0));

    // Verify the agg reflection suggestion
    ReflectionSuggester suggester = new ReflectionSuggester(table.getDatasetConfig(), tableStats);
    List<ReflectionGoal> goals = suggester.getReflectionGoals();
    assertEquals(2, goals.size());
    verifyAggGoal(goals.get(1));
  }

  private void verifyNationKeyColumnStat(ReflectionAnalyzer.ColumnStats stat) {
    assertEquals("n_nationkey", stat.getField().getName());
    assertEquals(25, stat.getCardinality());
    assertEquals(25, stat.getCount());
    assertEquals(0.0, stat.getMinValue());
    assertEquals(24.0, stat.getMaxValue());
    assertEquals(12.0, stat.getAverageValue());
  }

  private void verifyAggGoal(ReflectionGoal goal) {
    assertEquals(ReflectionType.AGGREGATION, goal.getType());
    assertEquals("n_regionkey", goal.getDetails().getDimensionFieldList().get(0).getName());
    assertEquals("n_nationkey", goal.getDetails().getMeasureFieldList().get(0).getName());
  }
}
