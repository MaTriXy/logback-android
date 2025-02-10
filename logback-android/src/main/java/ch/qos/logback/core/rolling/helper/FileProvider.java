/**
 * Copyright 2019 Anthony Trinh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.qos.logback.core.rolling.helper;

import java.io.File;
import java.io.FilenameFilter;

public interface FileProvider {
  File[] listFiles(File dir, FilenameFilter filter);
  String[] list(File dir, FilenameFilter filter);
  boolean deleteFile(File file);
  long length(File file);
  boolean exists(File file);
  boolean isDirectory(File file);
}
