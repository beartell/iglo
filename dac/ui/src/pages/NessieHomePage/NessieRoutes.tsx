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
import { Route, IndexRedirect } from "react-router";
import RepoView from "./components/RepoView/RepoView";
import ArcticSourceWithNessie from "@app/exports/pages/ArcticSource/ArcticSource";
import { ArcticSourceRoutes } from "@inject/additionalRequiredRoutes";
import ArcticCatalogCommits from "@app/exports/pages/ArcticCatalog/components/ArcticCatalogCommits/ArcticCatalogCommits";
import ArcticCatalogTags from "@app/exports/pages/ArcticCatalog/components/ArcticCatalogTags/ArcticCatalogTags";
import ArcticCommitDetails from "@app/exports/pages/ArcticCatalog/components/ArcticCommitDetails/ArcticCommitDetails";

import * as PATHS from "@app/exports/paths";

export const NessieHistorySourceRoutes = [
  <Route
    key="commits"
    path={PATHS.nessieSourceCommitsNonBase()}
    component={ArcticCatalogCommits}
  />,
  <Route
    key="commits-branch-namespace"
    path={PATHS.nessieSourceCommits({ branchId: ":branchName" })}
    component={ArcticCatalogCommits}
  />,
  <Route
    key="commits-namespace"
    path={PATHS.nessieSourceCommits({
      branchId: ":branchName",
      namespace: "*",
    })}
    component={ArcticCatalogCommits}
  />,
  <Route
    key="commitDetails"
    path={PATHS.nessieSourceCommit({
      branchId: ":branchName",
      commitId: ":commitId",
    })}
    component={ArcticCommitDetails}
  />,
  <Route
    key="tags"
    path={PATHS.nessieSourceTagsNonBase()}
    component={ArcticCatalogTags}
  />,
  <Route
    key="branches"
    path={PATHS.nessieSourceBranchesNonBase()}
    component={() => <RepoView showHeader={false} />}
  />,
];

export function nessieSourceRoutes() {
  return [
    <Route
      key="nessieSourceHomePage"
      path={PATHS.nessieSourceBase({
        sourceId: ":sourceId",
        projectId: ":projectId",
      })}
      component={ArcticSourceWithNessie}
    >
      <IndexRedirect
        to={PATHS.nessieSourceCommitsBase({
          sourceId: ":sourceId",
          projectId: ":projectId",
        })}
      />
      {NessieHistorySourceRoutes}
      <Route
        key="nessie-not-found"
        path={`${PATHS.nessieSourceBase({
          sourceId: ":sourceId",
          projectId: ":projectId",
        })}/*`}
        component={() => null}
      />
    </Route>,
  ];
}

export function arcticSourceRoutes() {
  return [
    <Route
      key="arcticSourceHomePage"
      path={PATHS.arcticSourceBase({
        sourceId: ":sourceId",
        projectId: ":projectId",
      })}
      component={ArcticSourceWithNessie}
    >
      <IndexRedirect
        to={PATHS.arcticSourceCommitsBase({
          sourceId: ":sourceId",
          projectId: ":projectId",
        })}
      />
      {ArcticSourceRoutes}
      <Route
        key="arctic-not-found"
        path={`${PATHS.arcticSourceBase({
          sourceId: ":sourceId",
          projectId: ":projectId",
        })}/*`}
        component={() => null}
      />
    </Route>,
  ];
}
