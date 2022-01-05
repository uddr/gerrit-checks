package com.google.gerrit.plugins.checks.db;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.api.CheckState;
import java.sql.Timestamp;
import java.time.Instant;

/** Representation of {@link Check} that can be serialized with GSON. */
class NoteDbCheck {

  private NoteDbCheck() {}

  public CheckState state = CheckState.NOT_STARTED;
  @Nullable public String message;
  @Nullable public String url;
  @Nullable public Timestamp started;
  @Nullable public Timestamp finished;

  public Timestamp created;
  public Timestamp updated;

  Check toCheck(CheckKey key) {
    Check.Builder newCheck =
        Check.builder(key).setState(state).setCreated(created).setUpdated(updated);
    if (message != null) {
      newCheck.setMessage(message);
    }
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

  Check toCheck(Project.NameKey repositoryName, PatchSet.Id patchSetId, CheckerUuid checkerUuid) {
    CheckKey key = CheckKey.create(repositoryName, patchSetId, checkerUuid);
    return toCheck(key);
  }

  static NoteDbCheck createInitialNoteDbCheck(CheckUpdate checkUpdate) {
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
    if (update.message().isPresent()
        && !update.message().get().equals(Strings.nullToEmpty(message))) {
      message = Strings.emptyToNull(update.message().get());
      modified = true;
    }
    if (update.url().isPresent() && !update.url().get().equals(Strings.nullToEmpty(url))) {
      url = Strings.emptyToNull(update.url().get());
      modified = true;
    }
    if (update.started().isPresent() && !update.started().get().equals(started)) {
      if (update.started().get().toInstant().equals(Instant.EPOCH)) {
        started = null;
      } else {
        started = update.started().get();
      }
      modified = true;
    }
    if (update.finished().isPresent() && !update.finished().get().equals(finished)) {
      if (update.finished().get().toInstant().equals(Instant.EPOCH)) {
        finished = null;
      } else {
        finished = update.finished().get();
      }
      modified = true;
    }
    return modified;
  }
}
