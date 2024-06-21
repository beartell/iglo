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
import { injectIntl } from "react-intl";
import PropTypes from "prop-types";
import Immutable from "immutable";
import ScanItem from "./ScanItem";
import "./Scans.less";

const Scans = ({ scans, scansForFilter, intl: { formatMessage } }) => {
  return (
    <div className="scans">
      {scans.size > 0 && (
        <div className="scans-title">{formatMessage({ id: "Scans" })}</div>
      )}

      {scans.map((scan, index) => {
        return (
          <ScanItem
            key={`scans-${index}`}
            scan={scan}
            scansForFilter={scansForFilter}
          />
        );
      })}
    </div>
  );
};

Scans.propTypes = {
  intl: PropTypes.object.isRequired,
  scansForFilter: PropTypes.array,
  scans: PropTypes.instanceOf(Immutable.List).isRequired,
};
export default injectIntl(Scans);
