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

package com.google.gerrit.plugins.checks.db;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Class to read checks from NoteDb. */
@Singleton
class NoteDbChecks implements Checks {
  private final ChangeNotes.Factory changeNotesFactory;
  private final PatchSetUtil psUtil;
  private final CheckNotes.Factory checkNotesFactory;
  private final Checkers checkers;
  private final CheckBackfiller checkBackfiller;

  @Inject
  NoteDbChecks(
      ChangeNotes.Factory changeNotesFactory,
      PatchSetUtil psUtil,
      CheckNotes.Factory checkNotesFactory,
      Checkers checkers,
      CheckBackfiller checkBackfiller) {
    this.changeNotesFactory = changeNotesFactory;
    this.psUtil = psUtil;
    this.checkNotesFactory = checkNotesFactory;
    this.checkers = checkers;
    this.checkBackfiller = checkBackfiller;
  }

  @Override
  public ImmutableList<Check> getChecks(
      Project.NameKey projectName, PatchSet.Id psId, GetCheckOptions options)
      throws IOException, OrmException {
    return getChecksFromNoteDb(projectName, psId, options);
  }

  @Override
  public Optional<Check> getCheck(CheckKey checkKey) throws OrmException, IOException {
    // TODO(gerrit-team): Instead of reading the complete notes map, read just one note.
    return getChecksFromNoteDb(checkKey.project(), checkKey.patchSet(), GetCheckOptions.defaults())
        .stream()
        .filter(c -> c.key().checkerUuid().equals(checkKey.checkerUuid()))
        .findAny();
  }

  private ImmutableList<Check> getChecksFromNoteDb(
      Project.NameKey projectName, PatchSet.Id psId, GetCheckOptions options)
      throws OrmException, IOException {
    // TODO(gerrit-team): Instead of reading the complete notes map, read just one note.
    ChangeNotes notes = changeNotesFactory.create(projectName, psId.getParentKey());
    PatchSet patchSet = psUtil.get(notes, psId);
    CheckNotes checkNotes = checkNotesFactory.create(notes.getChange());
    checkNotes.load();

    ImmutableList<Check> existingChecks =
        checkNotes.getChecks().getOrDefault(patchSet.getRevision(), NoteDbCheckMap.empty()).checks
            .entrySet().stream()
            .map(e -> e.getValue().toCheck(projectName, psId, CheckerUuid.parse(e.getKey())))
            .collect(toImmutableList());

    if (!options.backfillChecks()) {
      return existingChecks;
    }

    ImmutableList<Checker> checkersForBackfiller =
        getCheckersForBackfiller(projectName, existingChecks);
    ImmutableList<Check> backfilledChecks =
        checkBackfiller.getBackfilledChecksForRelevantCheckers(checkersForBackfiller, notes, psId);

    return Stream.concat(existingChecks.stream(), backfilledChecks.stream())
        .collect(toImmutableList());
  }

  private ImmutableList<Checker> getCheckersForBackfiller(
      Project.NameKey projectName, List<Check> existingChecks) throws IOException {
    ImmutableSet<CheckerUuid> checkersWithExistingChecks =
        existingChecks.stream().map(c -> c.key().checkerUuid()).collect(toImmutableSet());
    return checkers.checkersOf(projectName).stream()
        .filter(c -> !checkersWithExistingChecks.contains(c.getUuid()))
        .collect(toImmutableList());
  }
}
