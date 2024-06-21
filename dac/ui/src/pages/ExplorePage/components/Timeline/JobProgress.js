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
import { createRef, PureComponent } from "react";
import { intl } from "@app/utils/intl";
import PropTypes from "prop-types";

import Spinner from "components/Spinner";

import "./JobProgress.less";

export default class JobProgress extends PureComponent {
  static propTypes = {
    start: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    end: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    hideSpinner: PropTypes.bool,
  };

  constructor(props) {
    super(props);
    this.totalRef = createRef();
  }

  getCurrentProgress() {
    const { formatMessage } = intl;
    if (!this.props.end) {
      return (
        <div>
          <span className="progress-message">
            {formatMessage({ id: "Job.JobInProgress" })}
          </span>
          <span className="progress-view-jobs">
            {formatMessage({ id: "Job.ViewRunningJobs" })} (3) »
          </span>
        </div>
      );
    }

    const { current: { offsetWidth: totalWidth = 180 } = {} } = this.totalRef;
    const currentProgress = {
      width: (this.props.start / this.props.end) * totalWidth,
    };

    return (
      <div>
        <span className="result">
          <span className="end-items">{this.props.start}</span>
          {formatMessage({ id: "Common.Of" })}
          <span className="all-items">{this.props.end}</span>
          {formatMessage({ id: "Common.Rows.LowerCase" })}
        </span>
        <div className="progress-line" ref={this.totalRef} />
        <div className="current-progress" style={currentProgress} />
      </div>
    );
  }

  render() {
    return (
      <div className="job-progress" onClick={this.toogleDropdown}>
        <div className="spinner-part">
          <Spinner
            style={{ display: this.props.hideSpinner ? "none" : "block" }}
          />
          {intl.formatMessage({ id: "Common.Processing" })}
        </div>
        <div className="progress-part">{this.getCurrentProgress()}</div>
      </div>
    );
  }
}
