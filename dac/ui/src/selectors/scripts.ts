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

import { getExploreState } from "./explore";

export const getScripts = (state: any): any[] => {
  return state.resources.scripts.all;
};

export const getMineScripts = (state: any): any[] => {
  const mineScripts = state.resources.scripts.mine;
  return mineScripts || [];
};

export const getNumberOfMineScripts = (state: any): any[] => {
  const mineScripts = state.resources.scripts.mine;
  return mineScripts.length || 0;
};

export const getActiveScript = (state: any): any[] => {
  const exploreState = getExploreState(state);
  return exploreState ? exploreState.view.activeScript : {};
};

export const getActiveScriptPermissions = (state: any): any[] => {
  const exploreState = getExploreState(state);
  return exploreState ? exploreState.view.activeScript.permissions : [];
};
