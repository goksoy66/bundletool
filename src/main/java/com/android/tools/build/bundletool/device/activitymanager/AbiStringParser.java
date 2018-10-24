/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.tools.build.bundletool.device.activitymanager;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.AbiName;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Optional;

/**
 * Parsing utilities of the ABI line output of ActivityManager shell command.
 *
 * <p>The ABI line is simply a line starting with "abi: " and containing a comma separated values of
 * ABI architectures ordered by device preference.
 */
public class AbiStringParser {

  /** Parses the ABI line generated by the ActivityManager shell command. */
  public static ImmutableList<String> parseAbiLine(String abiLine) {
    checkArgument(abiLine.startsWith("abi:"), "Expected ABI output to start with 'abi:'.");

    String abiString = abiLine.substring("abi: ".length());

    ImmutableList<String> abiNames =
        Arrays.stream(abiString.split(",")).map(String::trim).collect(toImmutableList());

    // Validate all discovered ABI strings.
    for (String abiName : abiNames) {
      Optional<AbiName> abi = AbiName.fromPlatformName(abiName);
      if (!abi.isPresent()) {
        throw CommandExecutionException.builder()
            .withMessage(
                "Unknown ABI '%s' encountered while parsing activity manager config.", abiName)
            .build();
      }
    }
    return abiNames;
  }
}
