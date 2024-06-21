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
package com.dremio.exec.store.hive;

import org.apache.hadoop.conf.Configuration;

import com.dremio.exec.store.dfs.FileSystemConfigurationAdapter;

/**
 * A FileSystemConfigurationAdapter that exposes Hadoop configuration using the Hive plugin's version of Hadoop.
 */
public class HiveFileSystemConfigurationAdapter implements FileSystemConfigurationAdapter {

  private final Configuration conf;

  public HiveFileSystemConfigurationAdapter(Configuration conf) {
    this.conf = conf;
  }

  @Override
  public String get(String name) {
    return conf.get(name);
  }

  @Override
  public String get(String name, String defaultValue) {
    return conf.get(name, defaultValue);
  }
}
