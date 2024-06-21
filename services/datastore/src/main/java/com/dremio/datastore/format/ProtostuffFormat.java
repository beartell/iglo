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
package com.dremio.datastore.format;

import com.dremio.datastore.DatastoreFatalException;
import com.dremio.datastore.FormatVisitor;

final class ProtostuffFormat<P extends io.protostuff.Message<P>> implements Format<P> {
  private final Class<P> clazz;

  ProtostuffFormat(Class<P> pClass) {
    this.clazz = pClass;
  }

  @Override
  public Class<P> getRepresentedClass() {
    return clazz;
  }

  @Override
  public <RET> RET apply(FormatVisitor<RET> visitor) throws DatastoreFatalException {
    return visitor.visitProtostuffFormat(clazz);
  }
}
