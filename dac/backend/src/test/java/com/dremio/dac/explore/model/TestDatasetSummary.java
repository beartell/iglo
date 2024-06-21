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
package com.dremio.dac.explore.model;

import com.dremio.service.namespace.dataset.proto.DatasetConfig;
import com.dremio.service.namespace.dataset.proto.DatasetType;
import java.util.Collections;
import org.junit.Test;

public class TestDatasetSummary {

  @Test
  public void testCreateNewInstance() {
    DatasetSummary.newInstance(
        new DatasetConfig().setType(DatasetType.PHYSICAL_DATASET),
        0,
        0,
        Collections.emptyMap(),
        Collections.emptyList(),
        false,
        null,
        null);
  }
}
