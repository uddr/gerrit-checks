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

package com.google.gerrit.plugins.checks.api;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/** Factory that instantiates Checks from change and revision resources. Mostly useful for tests. */
@Singleton
public class ChecksFactory {
  private final ChecksImpl.Factory checksFactory;
  private final PatchSetUtil psUtil;
  private final ChangeNotes.Factory changeNotesFactory;
  private final Provider<CurrentUser> user;
  private final ChangeResource.Factory changeResourceFactory;

  @Inject
  ChecksFactory(
      ChecksImpl.Factory checksFactory,
      PatchSetUtil psUtil,
      ChangeNotes.Factory changeNotesFactory,
      ChangeResource.Factory changeResourceFactory,
      Provider<CurrentUser> user) {
    this.checksFactory = checksFactory;
    this.psUtil = psUtil;
    this.changeNotesFactory = changeNotesFactory;
    this.user = user;
    this.changeResourceFactory = changeResourceFactory;
  }

  public Checks currentRevision(Change.Id changeId) throws RestApiException {
    ChangeResource changeResource = getChangeResource(changeId);
    PatchSet patchSet = getCurrentPatchSet(changeResource.getNotes());
    return getChecks(changeResource, patchSet);
  }

  public Checks revision(PatchSet.Id patchSetId) throws RestApiException {
    ChangeResource changeResource = getChangeResource(patchSetId.changeId());
    PatchSet patchSet = getPatchSet(changeResource.getNotes(), patchSetId);
    return getChecks(changeResource, patchSet);
  }

  private ChangeResource getChangeResource(Change.Id changeId) throws RestApiException {
    try {
      ChangeNotes notes = changeNotesFactory.createChecked(changeId);
      return changeResourceFactory.create(notes, user.get());
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(String.format("Change %d not found", changeId.get()), e);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve change", e);
    }
  }

  private PatchSet getCurrentPatchSet(ChangeNotes changeNotes) throws RestApiException {
    try {
      return psUtil.current(changeNotes);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve current revision", e);
    }
  }

  private PatchSet getPatchSet(ChangeNotes changeNotes, PatchSet.Id patchSetId)
      throws RestApiException {
    try {
      return psUtil.get(changeNotes, patchSetId);
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve revision", e);
    }
  }

  private Checks getChecks(ChangeResource changeResource, PatchSet patchSet) {
    RevisionResource revisionResource = new RevisionResource(changeResource, patchSet);
    return checksFactory.create(revisionResource);
  }
}
