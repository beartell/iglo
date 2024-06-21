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
import { useIntl } from "react-intl";
import FinderNav from "@app/components/FinderNav";
import ViewStateWrapper from "@app/components/ViewStateWrapper";
import SourceBranchPicker from "../SourceBranchPicker/SourceBranchPicker";
import { spacesSourcesListSpinnerStyleFinderNav } from "@app/pages/HomePage/HomePageConstants";
import * as commonPaths from "dremio-ui-common/paths/common.js";
import { getSonarContext } from "dremio-ui-common/contexts/SonarContext.js";

type DataPlaneSectionProps = {
  dataPlaneSources: any;
  sourcesViewState: any;
  addHref: () => void;
  height?: string;
  location?: any;
  onToggle?: any;
  isCollapsed?: boolean;
  isCollapsible?: boolean;
};

function DataPlaneSection({
  dataPlaneSources,
  sourcesViewState,
  addHref,
  height,
  location,
  onToggle = null,
  isCollapsed = false,
  isCollapsible = false,
}: DataPlaneSectionProps) {
  const intl = useIntl();
  const hasProjectId = getSonarContext()?.getSelectedProjectId?.();
  const linkTo = hasProjectId
    ? commonPaths.arctic.link({
        projectId: getSonarContext()?.getSelectedProjectId?.(),
      })
    : commonPaths.nessie.link({});
  return (
    <div
      className="left-tree-wrap"
      style={{
        height: dataPlaneSources.size ? height : "auto",
        overflow: "hidden",
        marginTop: "-4px",
      }}
    >
      <ViewStateWrapper
        viewState={sourcesViewState}
        spinnerStyle={spacesSourcesListSpinnerStyleFinderNav}
      >
        <FinderNav
          isCollapsed={isCollapsed}
          isCollapsible={isCollapsible}
          onToggle={onToggle}
          location={location}
          navItems={dataPlaneSources}
          title={intl.formatMessage({
            id: hasProjectId
              ? "Source.ArcticCatalogs"
              : "Source.NessieCatalogs",
          })}
          addTooltip={intl.formatMessage({
            id: hasProjectId
              ? "Source.AddArcticCatalog"
              : "Source.AddNessieCatalog",
          })}
          isInProgress={sourcesViewState.get("isInProgress")}
          addHref={addHref}
          listHref={linkTo}
          renderExtra={(item: any, targetRef: any) => (
            <SourceBranchPicker
              source={item}
              getAnchorEl={() => targetRef.current}
            />
          )}
        />
      </ViewStateWrapper>
    </div>
  );
}
export default DataPlaneSection;
