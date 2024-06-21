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

import { useMemo, useState, useRef, useEffect, MutableRefObject } from "react";
import { connect } from "react-redux";
import classNames from "clsx";
import { debounce, isEqual } from "lodash";
import moment from "@app/utils/dayjs";
import { injectIntl } from "react-intl";
import { withRouter } from "react-router";
import { useResourceSnapshot } from "smart-resource/react";
import TextHighlight from "../TextHighlight";
import SubHeaderTabs from "@app/components/SubHeaderTabs";
import SortDropDownMenu from "@app/components/SortDropDownMenu";
import { showConfirmationDialog } from "actions/confirmation";
import {
  getScripts,
  getMineScripts,
  getActiveScript,
} from "@app/selectors/scripts";
import { getExploreState } from "@app/selectors/explore";
import {
  fetchScripts,
  deleteScript,
  setActiveScript,
} from "@app/actions/resources/scripts";
import { Avatar } from "dremio-ui-lib/components";
import { usePrevious } from "@app/utils/jobsUtils";
import { resetQueryState } from "@app/actions/explore/view";

import { loadPrivilegesListData } from "@app/actions/resources/privilegesModalApiActions";
import Menu from "../Menus/Menu";
import MenuItem from "../Menus/MenuItem";
import SettingsBtn from "../Buttons/SettingsBtn";
import SearchDatasetsComponent from "../DatasetList/SearchDatasetsComponent";
import {
  SCRIPT_SORT_MENU,
  DATETIME_FORMAT,
  handleDeleteScript,
  handleOpenScript as baseHandleOpenScript,
  ALL_MINE_SCRIPTS_TABS,
  filterAndSortScripts,
  INITIAL_CALL_VALUE,
  SEARCH_CALL_VALUE,
  prepareScriptsFromList,
  openPrivilegesModalForScript,
} from "./sqlScriptsUtils";

import SQLScriptRenameDialog from "./components/SQLScriptRenameDialog/SQLScriptRenameDialog";
import { ScriptsResource } from "dremio-ui-common/sonar/scripts/resources/ScriptsResource.js";
import "./SQLScripts.less";
import { useSqlRunnerSession } from "dremio-ui-common/sonar/SqlRunnerSession/providers/useSqlRunnerSession.js";

import { useMultiTabIsEnabled } from "./useMultiTabIsEnabled";

export const VIEW_ID = "ScriptsPrivileges";

export type SQLScriptsProps = {
  scripts: any[];
  mineScripts: any[];
  user: any;
  activeScript: any;
  currentSql: string | null;
  intl: any;
  router: any;
  location: any;
  fetchSQLScripts: (arg: {
    maxResults: number;
    searchTerm: string | null;
    createdBy: string | null;
  }) => Promise<any>;
  updateSQLScript: (payload: any, scriptId?: string) => void;
  deleteScript: (scriptId: string) => any;
  setActiveScript: (script: any) => void;
  showConfirmationDialog: (content: any) => void;
  resetQueryState: (exclude?: any) => void;
  dispatchLoadPrivilegesListData: (fullPathList: any[], viewId: string) => void;
};

function SQLScripts(props: SQLScriptsProps): React.ReactElement {
  const {
    scripts = [],
    mineScripts,
    activeScript,
    fetchSQLScripts,
    setActiveScript,
    user,
    router,
    location,
    intl,
  } = props;
  const [allScriptsResults] = useResourceSnapshot(ScriptsResource);
  const isMultiTabEnabled = useMultiTabIsEnabled();
  const inputRef = useRef<any>();
  const focusRef = useRef<any>();
  const [sort, setSort] = useState(SCRIPT_SORT_MENU[3]);
  const [search, setSearch] = useState("");
  const [scriptForRename, setScriptForRename] = useState<any>({});
  const [selectedScriptsTab, setSelectedScriptsTab] = useState(
    ALL_MINE_SCRIPTS_TABS.all
  );
  const scriptIdFromUrl = location?.query?.scriptId;
  const curScriptRef = useRef<any>(null);

  useEffect(() => {
    if (!curScriptRef.current) return;
    curScriptRef.current.scrollIntoView();
  }, []);

  const handleOpenScript = baseHandleOpenScript({
    sqlRunnerSession: useSqlRunnerSession(),
    multiTabEnabled: isMultiTabEnabled,
  });

  const prevSearch = usePrevious(search);

  const onFocus = () => {
    if (focusRef?.current)
      focusRef.current.className = "searchDatasetsPopover --focused";
  };

  const onBlur = () => {
    if (focusRef?.current) focusRef.current.className = "searchDatasetsPopover";
  };

  const onFocusRef = (div: MutableRefObject<any>) => {
    focusRef.current = div;
  };

  // Create scripts lists
  const [allScripts, myScripts] = useMemo(() => {
    return [
      prepareScriptsFromList({
        list: scripts,
        updateActiveScript: (script: { id: string }) => {
          if (
            activeScript &&
            activeScript.id === script.id &&
            !isEqual(script, activeScript) &&
            setActiveScript
          ) {
            setActiveScript({ script: script });
          }
        },
      }),
      prepareScriptsFromList({ list: mineScripts }),
    ];
  }, [scripts, mineScripts, activeScript, setActiveScript]);

  // -- Filter and sort based on the active list --
  // All: FE and BE filter for correct display number and correctly filtered list
  // Mine: FE filter only because only a max of 100 scripts possible
  const [allScriptsWithFilter, myScriptsWithFilter] = useMemo(() => {
    const filteredMineScripts = filterAndSortScripts({
      list: myScripts,
      search: search,
      sort: sort,
    });
    return [
      filterAndSortScripts({ list: allScripts, search: search, sort: sort }),
      filteredMineScripts,
    ];
  }, [allScripts, myScripts, sort, search]);

  const numberedScriptsTabs = [
    intl.formatMessage({ id: "Resource.Tree.All" }) +
      ` (${allScriptsWithFilter && allScriptsWithFilter.length})`,
    intl.formatMessage({ id: "Resource.Tree.Scripts.Mine" }) +
      ` (${myScriptsWithFilter.length})`,
  ];

  const onInputRef = (input: any): void => {
    inputRef.current = input;
  };

  // BE search for All list: user could have 100+ scripts in ALl tab so BE search
  // is needed
  useEffect(() => {
    if (search !== "" && search !== prevSearch) {
      fetchSQLScripts({
        maxResults: SEARCH_CALL_VALUE,
        searchTerm: search,
        createdBy: null,
      });
    } else if (search === "" && search !== prevSearch) {
      fetchSQLScripts({
        maxResults: INITIAL_CALL_VALUE,
        searchTerm: null,
        createdBy: null,
      });
    }
  }, [search, fetchSQLScripts, prevSearch, selectedScriptsTab, user]);

  const debouncedSearch = debounce(
    (e: any): void => setSearch((e.target as any).value),
    250
  );

  const clearSearch = (): void => {
    if (inputRef?.current) inputRef.current.value = "";
    setSearch("");
  };

  const SCRIPT_ACTIONS = [
    {
      id: "VIEW",
      label: intl.formatMessage({ id: "Common.Open" }),
      onClick: handleOpenScript,
    },
    {
      id: "MODIFY",
      label: intl.formatMessage({ id: "Common.Rename" }),
      onClick: (_: SQLScriptsProps, script: any) => setScriptForRename(script),
    },
    {
      id: "MANAGE_GRANTS",
      label: intl.formatMessage({ id: "Common.Privileges" }),
      onClick: (_: SQLScriptsProps, script: any) => {
        openPrivilegesModalForScript({
          router,
          location,
          script,
          VIEW_ID,
          noDataText: intl.formatMessage({
            id: "Privileges.Script.Not.Shared",
          }),
        });
      },
    },
    {
      id: "DELETE",
      label: intl.formatMessage({ id: "Common.Delete" }),
      onClick: (...args: any) => {
        const nextScript = args.pop();
        handleDeleteScript(
          ...args,
          () => {
            nextScript && handleOpenScript(args[0], nextScript);
          },
          isMultiTabEnabled
        );
      },
      disabled: isMultiTabEnabled && allScriptsResults?.value?.length === 1,
      className: "--delete",
    },
  ];

  const ScriptActionsMenu = (menuProps: any): React.ReactElement => {
    const {
      script: { permissions },
      script,
      nextScript,
      closeMenu,
    } = menuProps;
    const handleClick = (
      scriptAction: any,
      userId: string,
      searchTerm: string
    ): void => {
      scriptAction.onClick(props, script, userId, searchTerm, nextScript);
      closeMenu();
    };
    const CE_PERMISSIONS = ["VIEW", "MODIFY", "DELETE"];

    return (
      <Menu>
        {SCRIPT_ACTIONS.reduce((iterator: any[], script: any) => {
          if ((permissions ?? CE_PERMISSIONS).includes(script.id)) {
            iterator.push(
              <MenuItem
                key={script.label}
                onClick={(e: any): void => {
                  e.stopPropagation();
                  handleClick(script, user.get("userId"), search);
                }}
                disabled={script?.disabled}
                className={script.className ? script.className : ""}
              >
                {script.label}
              </MenuItem>
            );
          }
          return iterator;
        }, [])}
      </Menu>
    );
  };

  const currentScriptsList = selectedScriptsTab.startsWith(
    ALL_MINE_SCRIPTS_TABS.all
  )
    ? allScriptsWithFilter
    : myScriptsWithFilter;

  const RenderScripts = !currentScriptsList.length ? (
    <span className="sqlScripts__empty">
      {intl.formatMessage({ id: "Script.NoneFound" })}
    </span>
  ) : (
    <Menu>
      {currentScriptsList.map((script, i) => (
        <MenuItem
          key={script.id}
          className={`sqlScripts__menu-item ${
            script.id === activeScript.id ? "--selected" : ""
          }`}
          onClick={(): void => handleOpenScript(props, script)}
          {...(scriptIdFromUrl &&
            scriptIdFromUrl === script.id && {
              setRef: (ref: any) => (curScriptRef.current = ref),
            })}
        >
          <>
            <div className="sqlScripts__menu-item__leftContent">
              <div
                className={classNames(
                  "sideNav__user sideNav-item__dropdownIcon",
                  "--narrow"
                )}
              >
                <Avatar initials={script.initials} />
              </div>
              <div className="sqlScripts__menu-item__nameContent">
                <TextHighlight
                  className="scriptName"
                  text={script.name}
                  inputValue={search}
                  tooltipPlacement="top"
                  tooltipEnterDelay={500}
                  tooltipEnterNextDelay={500}
                />
                <div className="scriptCreator">
                  {moment(script.modifiedAt).format(DATETIME_FORMAT)}
                </div>
              </div>
            </div>
            <SettingsBtn
              classStr="sqlScripts__menu-item__actions"
              menu={
                <ScriptActionsMenu
                  script={script}
                  nextScript={
                    currentScriptsList?.[i + 1] || currentScriptsList?.[i + 1]
                  }
                />
              }
              hideArrowIcon
              stopPropagation
            >
              <dremio-icon
                name="interface/more"
                alt={intl.formatMessage({ id: "Common.More" })}
              />
            </SettingsBtn>
          </>
        </MenuItem>
      ))}
    </Menu>
  );

  return (
    <div className="sqlScripts" data-qa="sqlScripts">
      <div className="sqlScripts__subHeading">
        <SubHeaderTabs
          onClickFunc={setSelectedScriptsTab}
          tabArray={numberedScriptsTabs}
          selectedTab={selectedScriptsTab}
        />
        <SortDropDownMenu
          menuList={SCRIPT_SORT_MENU}
          sortValue={sort}
          disabled={scripts.length === 0}
          setSortValue={setSort}
          selectClass={"sqlScripts__dropdown__disabled"}
        />
      </div>
      <SearchDatasetsComponent
        onInput={debouncedSearch}
        clearFilter={clearSearch}
        closeVisible={search !== ""}
        onInputRef={onInputRef}
        placeholderText={intl.formatMessage({ id: "Script.Search" })}
        dataQa="sqlScripts__search"
        onFocus={onFocus}
        onBlur={onBlur}
        onFocusRef={onFocusRef}
      />
      {RenderScripts}
      {scriptForRename && scriptForRename.id && (
        <SQLScriptRenameDialog
          script={scriptForRename}
          isOpen={!!scriptForRename.id}
          onCancel={() => setScriptForRename({})}
        />
      )}
    </div>
  );
}

const mapStateToProps = (state: any, ownProps: any): any => {
  const { location } = ownProps;
  const explorePageState = getExploreState(state);
  return {
    location,
    user: state.account.get("user"),
    scripts: getScripts(state),
    mineScripts: getMineScripts(state),
    activeScript: getActiveScript(state),
    currentSql: explorePageState ? explorePageState.view.currentSql : null,
  } as any;
};

const reduxActions = {
  showConfirmationDialog,
  fetchSQLScripts: fetchScripts,
  deleteScript,
  setActiveScript,
  resetQueryState,
  dispatchLoadPrivilegesListData: loadPrivilegesListData,
};

export const TestSqlScripts = injectIntl(SQLScripts);

export default withRouter(
  connect(mapStateToProps, reduxActions)(TestSqlScripts)
);
