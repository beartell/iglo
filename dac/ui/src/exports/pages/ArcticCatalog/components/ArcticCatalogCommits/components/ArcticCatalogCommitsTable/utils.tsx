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

import { FormattedMessage } from "react-intl";
import { Avatar } from "dremio-ui-lib/components";
import { LogEntryV2 as LogEntry } from "@app/services/nessie/client";
import { nameToInitials } from "@app/exports/utilities/nameToInitials";
// @ts-ignore
import { IconButton } from "dremio-ui-lib";
import SettingsBtn from "@app/components/Buttons/SettingsBtn";
import ArcticGitActionsMenu from "../../../ArcticGitActionsMenu/ArcticGitActionsMenu";
import { ArcticCatalogTabsType } from "@app/exports/pages/ArcticCatalog/ArcticCatalog";
import { convertISOStringWithTooltip } from "@app/pages/NessieHomePage/components/RepoView/components/RepoViewBody/components/RepoViewBranchList/utils";
import { getShortHash } from "@app/utils/nessieUtils";
import { isNotSoftware } from "dyn-load/utils/versionUtils";
import CopyButton from "components/Buttons/CopyButton";

export const getCommitsTableColumns = () => {
  return [
    {
      key: "author",
      label: <FormattedMessage id="Common.Author" />,
      disableSort: true,
    },
    {
      key: "commitID",
      label: <FormattedMessage id="ArcticCatalog.Commits.CommitID" />,
      disableSort: true,
      disabledClick: true,
    },
    {
      key: "commitMessage",
      label: <FormattedMessage id="ArcticCatalog.Commits.CommitMessage" />,
      disableSort: true,
    },
    {
      key: "commitTime",
      label: <FormattedMessage id="Common.CommitTime" />,
    },
  ];
};

export const generateTableRows = (
  data: LogEntry[],
  goToDataTab: (tab: ArcticCatalogTabsType, item: LogEntry) => void,
  handleOpenDialog: (type: "TAG" | "BRANCH", dialogState: any) => void,
  reference: any,
  privileges: Record<string, any> | null
) => {
  const tableData: any[] = [];
  data.forEach((entry) => {
    const commitData = entry?.commitMeta;
    if (!commitData) return;

    tableData.push({
      id: commitData?.hash,
      rowClassName: commitData?.hash,
      data: {
        author: {
          node: () => {
            const author = commitData?.authors?.[0];
            return (
              <div className="author">
                <Avatar initials={nameToInitials(author ?? "")} />
                <span className="author__name">{author}</span>
              </div>
            );
          },
        },
        commitID: {
          node: () => (
            <div className="commit-id">
              <dremio-icon name="vcs/commit" class="commit-id__icon" />
              <span className="commit-id__id">
                {getShortHash(commitData.hash || "")}
              </span>
              <span>
                <CopyButton
                  text={commitData.hash || ""}
                  title={
                    <FormattedMessage id="ArcticCatalog.Commits.CommitID.Copy" />
                  }
                />
              </span>
            </div>
          ),
        },
        commitMessage: {
          node: () => (
            <div className="commit-message">{commitData?.message}</div>
          ),
        },
        commitTime: {
          node: () => (
            <div className="commit-time">
              <span className="commit-time__time">
                {convertISOStringWithTooltip(
                  commitData?.commitTime?.toString() ?? "",
                  { isRelative: true }
                )}
              </span>
              <span className="commit-time__buttons">
                <IconButton
                  tooltip="ArcticCatalog.Commits.GoToData"
                  onClick={(e: any) => {
                    e.stopPropagation();
                    goToDataTab("data", entry);
                  }}
                  className="commit-time__buttons--data"
                >
                  <dremio-icon name="interface/goto-dataset" alt="" />
                </IconButton>
                {(!isNotSoftware?.() ||
                  privileges?.branch.canCreate ||
                  privileges?.tag.canCreate) && (
                  <SettingsBtn
                    classStr="commit-time__buttons--more"
                    menu={
                      <ArcticGitActionsMenu
                        fromItem={{
                          type: "BRANCH",
                          name: reference?.name,
                          hash: commitData?.hash,
                        }}
                        handleOpenDialog={handleOpenDialog}
                      />
                    }
                    hideArrowIcon
                    stopPropagation
                  >
                    <dremio-icon name="interface/more" class="more-icon" />
                  </SettingsBtn>
                )}
              </span>
            </div>
          ),
        },
      },
    });
  });

  return tableData;
};
