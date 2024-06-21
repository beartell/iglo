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
package com.dremio.jdbc;

import java.sql.Connection;

/** A factory used to get open {@link Connection} instances. */
public interface ConnectionFactory {

  /**
   * Gets an open {@link Connection} based on given {@link ConnectionInfo connection parameters}.
   *
   * @param info the connection parameters
   * @throws Exception if factory fails to get a connection.
   */
  Connection getConnection(ConnectionInfo info) throws Exception;
}