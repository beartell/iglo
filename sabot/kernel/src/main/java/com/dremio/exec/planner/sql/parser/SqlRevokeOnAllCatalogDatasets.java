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
package com.dremio.exec.planner.sql.parser;

import com.dremio.exec.ops.QueryContext;
import com.dremio.exec.planner.sql.handlers.direct.SimpleDirectHandler;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlSpecialOperator;
import org.apache.calcite.sql.parser.SqlParserPos;

/*
 * Implements SQL to grant privileges on all datasets under a catalog entity
 * {CATALOG} for a given grantee.
 *
 * Represents statements like:
 * GRANT priv1 [,...] ON ALL DATASETS IN CATALOG TO granteeType grantee
 */
public class SqlRevokeOnAllCatalogDatasets extends SqlRevokeOnAllDatasets
    implements SimpleDirectHandler.Creator {
  public static final SqlSpecialOperator OPERATOR =
      new SqlSpecialOperator("GRANT", SqlKind.OTHER) {
        @Override
        public SqlCall createCall(
            SqlLiteral functionQualifier, SqlParserPos pos, SqlNode... operands) {
          Preconditions.checkArgument(
              operands.length == 5,
              "SqlRevokeOnAllCatalogDatasets.createCall() has to get 5 operands!");

          return new SqlGrantOnAllCatalogDatasets(
              pos,
              (SqlNodeList) operands[0],
              (SqlLiteral) operands[1],
              (SqlIdentifier) operands[2],
              (SqlLiteral) operands[3],
              (SqlIdentifier) operands[4]);
        }
      };

  public SqlRevokeOnAllCatalogDatasets(
      SqlParserPos pos,
      SqlNodeList privilegeList,
      SqlLiteral entityType,
      SqlIdentifier entity,
      SqlLiteral granteeType,
      SqlIdentifier grantee) {
    super(pos, privilegeList, entityType, entity, granteeType, grantee);
  }

  @Override
  public SimpleDirectHandler toDirectHandler(QueryContext context) {
    try {
      final Class<?> cl =
          Class.forName("com.dremio.exec.planner.sql.handlers.RevokeOnAllCatalogDatasetsHandler");
      Constructor<?> ctor = cl.getConstructor(QueryContext.class);
      return (SimpleDirectHandler) ctor.newInstance(context);
    } catch (InstantiationException
        | IllegalAccessException
        | ClassNotFoundException
        | NoSuchMethodException
        | InvocationTargetException e) {
      throw Throwables.propagate(e);
    }
  }
}
