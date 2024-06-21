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
package com.dremio.exec.store.dfs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dremio.exec.hadoop.HadoopFileSystem;
import com.dremio.io.file.FileAttributes;
import com.dremio.io.file.FileSystem;
import com.dremio.io.file.Path;
import java.io.File;
import java.util.Optional;
import org.apache.hadoop.conf.Configuration;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestFileSelection {
  @ClassRule public static final TemporaryFolder tempDir = new TemporaryFolder();

  @Test
  public void testGetFileWithRegularHiddenFile() throws Exception {
    FileSystem fs = HadoopFileSystem.getLocal(new Configuration());
    File hiddenFile = tempDir.newFile(".abc");
    Optional<FileAttributes> fileAttributes =
        FileSelection.getFirstFileIteratively(fs, Path.of(hiddenFile.toURI()));
    assertFalse(fileAttributes.isPresent());
  }

  @Test
  public void testGetFileWithRegularNoHiddenFile() throws Exception {
    FileSystem fs = HadoopFileSystem.getLocal(new Configuration());
    File hiddenFile = tempDir.newFile("test.parquet");
    Optional<FileAttributes> fileAttributes =
        FileSelection.getFirstFileIteratively(fs, Path.of(hiddenFile.toURI()));
    assertTrue(fileAttributes.isPresent());
  }
}