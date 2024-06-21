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
package com.dremio.exec.planner.logical;

import com.dremio.exec.catalog.VacuumOptions;
import com.dremio.exec.planner.common.VacuumTableRelBase;
import java.util.List;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;

/** Drel for 'VACUUM' query. */
public class VacuumTableRel extends VacuumTableRelBase implements Rel {

  public VacuumTableRel(
      RelOptCluster cluster,
      RelTraitSet traitSet,
      RelNode input,
      RelOptTable table,
      CreateTableEntry createTableEntry,
      VacuumOptions vacuumOptions) {
    super(LOGICAL, cluster, traitSet, input, table, createTableEntry, vacuumOptions);
  }

  @Override
  public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    return new VacuumTableRel(
        getCluster(),
        traitSet,
        sole(inputs),
        getTable(),
        getCreateTableEntry(),
        getVacuumOptions());
  }
}
