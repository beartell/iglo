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

import { NotFound } from "@app/exports/components/ErrorViews/NotFound";
import HomePage from "@app/pages/HomePage/HomePage";
import { HomePageContent } from "@app/pages/NessieHomePage/NessieHomePage";
import { getSortedSources } from "@app/selectors/home";
import { getEndpointFromSource } from "@app/utils/nessieUtils";
import { useMemo } from "react";
import { connect } from "react-redux";
import { withRouter } from "react-router";
import { ArcticCatalog } from "../ArcticCatalog/ArcticCatalog";
import ViewStateWrapper from "@app/components/ViewStateWrapper";
import { fromJS } from "immutable";
import * as commonPaths from "dremio-ui-common/paths/common.js";
import { getSonarContext } from "dremio-ui-common/contexts/SonarContext.js";
import { NESSIE } from "@app/constants/sourceTypes";

import "@app/pages/NessieHomePage/components/NessieSourceHomePage/NessieSourceHomePage.less";

function ArcticSourceHomePage(props: any) {
  const sourceInfo = useMemo(() => {
    const source = (props.sources || []).find(
      (item: any) => item.get("name") === props.params.sourceId
    );
    if (!source) return null;

    return {
      name: source.get("name"),
      id: source.get("id"),
      endpoint: getEndpointFromSource(source.toJS()),
      type: source.get("type"),
    };
  }, [props.params.sourceId, props.sources]);

  const pathProps = {
    sourceName: sourceInfo?.name,
    projectId: getSonarContext().getSelectedProjectId?.(),
  };

  const baseUrl =
    sourceInfo?.type === NESSIE
      ? commonPaths.nessieSource.link(pathProps)
      : commonPaths.arcticSource.link(pathProps);

  return (
    // @ts-ignore
    <HomePage location={props.location}>
      {sourceInfo ? (
        <div className="nessieSourceHomePage">
          <HomePageContent
            key={JSON.stringify(sourceInfo)}
            source={sourceInfo}
            baseUrl={baseUrl}
            viewState={undefined}
            initialRef={{
              name: props.params?.branchName,
              hash: props.location?.query?.hash,
            }}
          >
            {props.children}
          </HomePageContent>
        </div>
      ) : (
        <ViewStateWrapper
          viewState={fromJS({ isInProgress: props.isInProgress })}
          hideChildrenWhenInProgress
          style={{ width: "100%", display: "flex" }}
        >
          <NotFound />
        </ViewStateWrapper>
      )}
    </HomePage>
  );
}

const mapStateToProps = (state: any) => {
  return {
    sources: getSortedSources(state),
    isInProgress: state?.resources?.sourceList?.get("isInProgress"),
  };
};
const ConnectedArcticSourceHomePage = connect(
  mapStateToProps,
  null
)(withRouter(ArcticSourceHomePage));

const ArcticSourceWithRoute = withRouter(ArcticCatalog);
const ArcticSourceWithNessie = ({ children }: any) => (
  <ConnectedArcticSourceHomePage>
    <ArcticSourceWithRoute>{children}</ArcticSourceWithRoute>
  </ConnectedArcticSourceHomePage>
);

export default ArcticSourceWithNessie;
