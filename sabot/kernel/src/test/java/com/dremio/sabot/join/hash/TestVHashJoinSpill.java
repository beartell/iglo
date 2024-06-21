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
package com.dremio.sabot.join.hash;

import com.dremio.common.logical.data.JoinCondition;
import com.dremio.exec.ExecConstants;
import com.dremio.exec.physical.config.HashJoinPOP;
import com.dremio.options.OptionManager;
import com.dremio.options.OptionValue;
import com.dremio.options.OptionValue.OptionType;
import com.dremio.sabot.join.BaseTestJoin;
import com.dremio.sabot.op.join.hash.HashJoinOperator;
import com.dremio.sabot.op.join.vhash.spill.VectorizedSpillingHashJoinOperator;
import java.util.List;
import org.apache.calcite.rel.core.JoinRelType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestVHashJoinSpill extends BaseTestJoin {
  private final OptionManager options = testContext.getOptions();
  private final int minReserve = VectorizedSpillingHashJoinOperator.MIN_RESERVE;

  @Before
  public void before() {
    options.setOption(
        OptionValue.createBoolean(
            OptionType.SYSTEM, HashJoinOperator.ENABLE_SPILL.getOptionName(), true));
    options.setOption(
        OptionValue.createLong(
            OptionType.SYSTEM, ExecConstants.TARGET_BATCH_RECORDS_MAX.getOptionName(), 65535));
    VectorizedSpillingHashJoinOperator.MIN_RESERVE = 9 * 1024 * 1024;
  }

  @After
  public void after() {
    options.setOption(HashJoinOperator.ENABLE_SPILL.getDefault());
    options.setOption(ExecConstants.TARGET_BATCH_RECORDS_MAX.getDefault());
    VectorizedSpillingHashJoinOperator.MIN_RESERVE = minReserve;
  }

  @Override
  protected JoinInfo getJoinInfo(List<JoinCondition> conditions, JoinRelType type) {
    return new JoinInfo(
        VectorizedSpillingHashJoinOperator.class,
        new HashJoinPOP(PROPS, null, null, conditions, null, type, true, true, null));
  }

  @Test
  public void manyColumns() throws Exception {
    baseManyColumns();
  }

  @Test
  public void manyColumnsDecimal() throws Exception {
    baseManyColumnsDecimal();
  }
}
