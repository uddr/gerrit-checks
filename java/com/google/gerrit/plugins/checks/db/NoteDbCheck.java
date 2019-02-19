package com.google.gerrit.plugins.checks.db;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import java.sql.Timestamp;

/** Representation of {@link Check} that can be serialized with GSON. */
class NoteDbCheck {

  private NoteDbCheck() {}

  public CheckState state;
  @Nullable public String url;
  @Nullable public Timestamp started;
  @Nullable public Timestamp finished;

  public Timestamp created;
  public Timestamp updated;

  Check toCheck(CheckKey key) {
    Check.Builder newCheck =
        Check.builder(key).setState(state).setCreated(created).setUpdated(updated);
    if (url != null) {
      newCheck.setUrl(url);
    }
    if (started != null) {
      newCheck.setStarted(started);
    }
    if (finished != null) {
      newCheck.setFinished(finished);
    }
    return newCheck.build();
  }

  Check toCheck(Project.NameKey project, PatchSet.Id patchSetId, String checkerUuid) {
    CheckKey key = CheckKey.create(project, patchSetId, checkerUuid);
    return toCheck(key);
  }

  static NoteDbCheck fromCheckUpdate(CheckUpdate checkUpdate) {
    NoteDbCheck noteDbCheck = new NoteDbCheck();
    noteDbCheck.applyUpdate(checkUpdate);
    return noteDbCheck;
  }

  /**
   * Applies the given update and returns {@code true} if at least a single fields value was changed
   * to a different value, {@code false} otherwise. Does not update timestamps.
   */
  boolean applyUpdate(CheckUpdate update) {
    boolean modified = false;
    if (update.state().isPresent() && !update.state().get().equals(state)) {
      state = update.state().get();
      modified = true;
    }
    if (update.url().isPresent() && !update.url().get().equals(url)) {
      url = update.url().get();
      modified = true;
    }
    if (update.started().isPresent() && !update.started().get().equals(started)) {
      started = update.started().get();
      modified = true;
    }
    if (update.finished().isPresent() && update.finished().get().equals(finished)) {
      finished = update.finished().get();
      modified = true;
    }
    return modified;
  }
}
