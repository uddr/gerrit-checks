// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.MapSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.checks.api.CheckablePatchSetInfo;
import com.google.gerrit.plugins.checks.api.PendingCheckInfo;
import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import java.util.Map;

public class PendingChecksInfoSubject extends Subject {
  public static PendingChecksInfoSubject assertThat(PendingChecksInfo pendingChecksInfo) {
    return assertAbout(PendingChecksInfoSubject::new).that(pendingChecksInfo);
  }

  private final PendingChecksInfo pendingChecksInfo;

  private PendingChecksInfoSubject(FailureMetadata metadata, PendingChecksInfo pendingChecksInfo) {
    super(metadata, pendingChecksInfo);
    this.pendingChecksInfo = pendingChecksInfo;
  }

  public void hasRepository(Project.NameKey expectedRepositoryName) {
    check("patchSet().repository()")
        .that(patchSet().repository)
        .isEqualTo(expectedRepositoryName.get());
  }

  public void hasPatchSet(PatchSet.Id expectedPatchSetId) {
    CheckablePatchSetInfo patchSet = patchSet();
    check("patchSet().changeNumber()")
        .that(patchSet.changeNumber)
        .isEqualTo(expectedPatchSetId.changeId().get());
    check("patchSet().id()").that(patchSet.patchSetId).isEqualTo(expectedPatchSetId.get());
  }

  public MapSubject hasPendingChecksMapThat() {
    return check("pendingChecks()").that(pendingChecks());
  }

  private CheckablePatchSetInfo patchSet() {
    isNotNull();
    CheckablePatchSetInfo patchSet = pendingChecksInfo.patchSet;
    check("patchSet()").that(patchSet).isNotNull();
    return patchSet;
  }

  private Map<String, PendingCheckInfo> pendingChecks() {
    isNotNull();
    Map<String, PendingCheckInfo> pendingChecks = pendingChecksInfo.pendingChecks;
    check("pendingChecks()").that(pendingChecks).isNotNull();
    return pendingChecks;
  }
}
