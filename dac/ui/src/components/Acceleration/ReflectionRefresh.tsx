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

import { intl } from "@app/utils/intl";
import { Button } from "dremio-ui-lib/components";
import Radio from "@app/components/Fields/Radio";
import FieldWithError from "@app/components/Fields/FieldWithError";
import DurationField from "@app/components/Fields/DurationField";
import { ScheduleRefresh } from "@app/components/Forms/ScheduleRefresh";
import Checkbox from "@app/components/Fields/Checkbox";
import { formDefault } from "uiTheme/radium/typography";
import { SCHEDULE_POLICIES } from "@app/components/Forms/DataFreshnessSection";

type ReflectionRefreshProps = {
  fields: {
    accelerationRefreshPeriod: any;
    accelerationGracePeriod: any;
    accelerationNeverExpire: any;
    accelerationNeverRefresh: any;
    accelerationRefreshSchedule: any;
    accelerationActivePolicyType: any;
  };
  entityType: string;
  refreshingReflections: boolean;
  isRefreshAllowed: boolean;
  minDuration: number;
  refreshAll: () => void;
};

const ReflectionRefresh = ({
  entityType,
  fields,
  refreshingReflections,
  isRefreshAllowed,
  refreshAll,
  minDuration,
}: ReflectionRefreshProps) => {
  const {
    accelerationRefreshPeriod,
    accelerationGracePeriod,
    accelerationNeverExpire,
    accelerationRefreshSchedule,
    accelerationActivePolicyType,
  } = fields;

  return (
    <div className="flex flex-col">
      {isRefreshAllowed && entityType === "dataset" && (
        <div>
          <p className="text-semibold text-base pb-1">
            {intl.formatMessage({
              id: "Reflection.Refresh.Once",
            })}
          </p>

          <Button
            disabled={refreshingReflections}
            onClick={refreshAll}
            variant="secondary"
            style={{ marginBottom: 24 }}
          >
            {intl.formatMessage({
              id: "Reflection.Refresh.Now",
            })}
          </Button>
        </div>
      )}
      <div>
        <p className="text-semibold text-base pb-05">
          {intl.formatMessage({
            id: "Reflection.Refresh.Settings",
          })}
        </p>
      </div>
      <div>
        <Radio
          {...accelerationActivePolicyType}
          radioValue={SCHEDULE_POLICIES.NEVER}
          label={intl.formatMessage({
            id: "Reflection.Refresh.Never",
          })}
        />
      </div>
      <div>
        <Radio
          {...accelerationActivePolicyType}
          radioValue={SCHEDULE_POLICIES.PERIOD}
          label={
            <>
              <td>
                <div style={styles.inputLabel}>
                  {intl.formatMessage({
                    id: "Reflection.Refresh.Every",
                  })}
                </div>
              </td>
              <td>
                <FieldWithError
                  errorPlacement="right"
                  {...accelerationRefreshPeriod}
                >
                  <div style={{ display: "flex" }}>
                    <DurationField
                      {...accelerationRefreshPeriod}
                      min={minDuration}
                      style={styles.durationField}
                      disabled={
                        accelerationActivePolicyType.value !==
                        SCHEDULE_POLICIES.PERIOD
                      }
                    />
                  </div>
                </FieldWithError>
              </td>
            </>
          }
        />
      </div>
      <div>
        <Radio
          {...accelerationActivePolicyType}
          style={{
            alignItems: "flex-start",
            marginBottom: 24,
            marginTop: 10,
          }}
          radioValue={SCHEDULE_POLICIES.SCHEDULE}
          label={
            <div className="flex flex-col">
              <td>
                <div style={styles.inputLabel}>
                  {intl.formatMessage({
                    id: "Reflection.Refresh.SetSchedule",
                  })}
                </div>
              </td>
              {SCHEDULE_POLICIES.SCHEDULE ===
                accelerationActivePolicyType.value && (
                <td>
                  <FieldWithError
                    errorPlacement="right"
                    {...accelerationRefreshSchedule}
                  >
                    <div
                      style={{
                        display: "flex",
                        flexDirection: "column",
                      }}
                    >
                      <ScheduleRefresh
                        accelerationRefreshSchedule={
                          accelerationRefreshSchedule
                        }
                      />
                    </div>
                  </FieldWithError>
                </td>
              )}
            </div>
          }
        />
      </div>
      <div>
        <p className="text-semibold text-base pb-05">
          {intl.formatMessage({
            id: "Reflection.Expire.Settings",
          })}
        </p>
      </div>
      <div>
        <div style={styles.inputLabelMargin}>
          <Checkbox
            {...accelerationNeverExpire}
            label={intl.formatMessage({
              id: "Reflection.Expire.Never",
            })}
          />
        </div>
      </div>
      <div className="flex flex-row items-center">
        <div style={styles.inputLabelMargin}>
          {intl.formatMessage({
            id: "Reflection.Expire.After",
          })}
        </div>
        <FieldWithError errorPlacement="right" {...accelerationGracePeriod}>
          <DurationField
            {...accelerationGracePeriod}
            min={minDuration}
            style={styles.durationField}
            disabled={!!accelerationNeverExpire.value}
          />
        </FieldWithError>
      </div>
    </div>
  );
};

const styles = {
  container: {
    marginTop: 6,
  },
  info: {
    maxWidth: 525,
    marginBottom: 26,
  },
  section: {
    display: "flex",
    marginBottom: 6,
    alignItems: "center",
  },
  select: {
    width: 164,
    marginTop: 3,
  },
  label: {
    fontSize: "18px",
    fontWeight: 600,
    margin: "0 0 8px 0px",
    display: "flex",
    alignItems: "center",
    color: "var(--color--neutral--900)",
  },
  inputLabel: {
    ...formDefault,
    marginRight: 10,
    fontSize: 14,
  },
  inputLabelMargin: {
    ...formDefault,
    marginRight: 10,
    marginBottom: 6,
    fontSize: 14,
  },
  durationField: {
    width: 250,
  },
};

export default ReflectionRefresh;
