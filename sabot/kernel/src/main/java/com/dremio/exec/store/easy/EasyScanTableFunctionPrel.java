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
package com.dremio.exec.store.easy;

import com.dremio.exec.physical.config.TableFunctionConfig;
import com.dremio.exec.planner.physical.TableFunctionPrel;
import com.dremio.exec.store.TableMetadata;
import java.util.List;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;

public class EasyScanTableFunctionPrel extends TableFunctionPrel {

  public EasyScanTableFunctionPrel(
      RelOptCluster cluster,
      RelTraitSet traitSet,
      RelOptTable table,
      RelNode child,
      TableMetadata tableMetadata,
      TableFunctionConfig functionConfig,
      RelDataType rowType,
      Long survivingRowCount) {
    super(
        cluster, traitSet, table, child, tableMetadata, functionConfig, rowType, survivingRowCount);
  }

  @Override
  public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    return new EasyScanTableFunctionPrel(
        getCluster(),
        getTraitSet(),
        getTable(),
        sole(inputs),
        getTableMetadata(),
        getTableFunctionConfig(),
        getRowType(),
        getSurvivingRecords());
  }

  @Override
  protected double defaultEstimateRowCount(
      TableFunctionConfig functionConfig, RelMetadataQuery mq) {
    if (getSurvivingRecords() == null) {
      // if an estimate isn't provided, assume we don't have many delete files to aggregate and use
      // the row count
      // of the input
      return mq.getRowCount(input);
    }

    return (double) getSurvivingRecords();
  }
}
