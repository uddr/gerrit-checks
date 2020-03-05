// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.checks.api;

import com.google.common.base.MoreObjects;
import java.util.Objects;

/**
 * Summary of the impact the check has on the submission of the change. Besides a general
 * indication, this may also include related details which help users to better understand that
 * impact.
 */
public class CheckSubmitImpactInfo {

  /**
   * Indicates whether this check is included in submit considerations of the change. This depends
   * on various factors and can change over the lifetime of a check.
   */
  public Boolean required;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CheckSubmitImpactInfo)) {
      return false;
    }
    CheckSubmitImpactInfo other = (CheckSubmitImpactInfo) o;
    return Objects.equals(other.required, required);
  }

  @Override
  public int hashCode() {
    return Objects.hash(required);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("required", required).toString();
  }
}
