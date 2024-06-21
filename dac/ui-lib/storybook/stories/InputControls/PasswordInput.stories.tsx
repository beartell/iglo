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

import { Meta, StoryFn } from "@storybook/react";

import { PasswordInput } from "../../../components";

export default {
  title: "Input Controls/PasswordInput",
  component: PasswordInput,
} as Meta<typeof PasswordInput>;

export const Default: StoryFn<typeof PasswordInput> = (args: any) => {
  return (
    <div className="dremio-prose">
      <div className="form-group">
        <label htmlFor="password">Password</label>
        <PasswordInput {...args} id="password" name="password" />
      </div>
    </div>
  );
};

Default.storyName = "PasswordInput";
