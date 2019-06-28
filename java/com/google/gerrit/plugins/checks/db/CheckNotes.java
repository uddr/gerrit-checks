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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.notedb.AbstractChangeNotes;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;

public class CheckNotes extends AbstractChangeNotes<CheckRevisionNote> {
  public interface Factory {
    CheckNotes create(Change change);
  }

  private final String pluginName;
  private final Change change;

  private ImmutableMap<ObjectId, NoteDbCheckMap> entities;
  private CheckRevisionNoteMap revisionNoteMap;
  private ObjectId metaId;

  @Inject
  CheckNotes(Args args, @PluginName String pluginName, @Assisted Change change) {
    super(args, change.getId());
    this.pluginName = pluginName;
    this.change = change;
  }

  public ImmutableMap<ObjectId, NoteDbCheckMap> getChecks() {
    return entities;
  }

  @Override
  public String getRefName() {
    return CheckerRef.checksRef(getChangeId());
  }

  @Nullable
  public ObjectId getMetaId() {
    return metaId;
  }

  @Override
  protected void onLoad(LoadHandle handle) throws IOException, ConfigInvalidException {
    metaId = handle.id();
    if (metaId == null) {
      loadDefaults();
      return;
    }
    metaId = metaId.copy();

    try (TraceTimer ignored =
        TraceContext.newTimer(
            "Load check notes",
            Metadata.builder()
                .pluginName(pluginName)
                .projectName(getProjectName().get())
                .changeId(getChangeId().get())
                .build())) {
      RevCommit tipCommit = handle.walk().parseCommit(metaId);
      ObjectReader reader = handle.walk().getObjectReader();
      revisionNoteMap =
          CheckRevisionNoteMap.parseChecks(
              args.changeNoteJson, reader, NoteMap.read(reader, tipCommit));
    }

    ImmutableMap.Builder<ObjectId, NoteDbCheckMap> cs = ImmutableMap.builder();
    for (Map.Entry<ObjectId, CheckRevisionNote> rn : revisionNoteMap.revisionNotes.entrySet()) {
      cs.put(rn.getKey(), rn.getValue().getOnlyEntity());
    }
    entities = cs.build();
  }

  @Override
  protected void loadDefaults() {
    entities = ImmutableMap.of();
  }

  @Override
  public Project.NameKey getProjectName() {
    return change.getProject();
  }
}
