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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerCreation;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUpdate;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.CheckersUpdate;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;

/**
 * A representation of a checker in NoteDb.
 *
 * <p>Checkers in NoteDb can be created by following the descriptions of {@link
 * #createForNewChecker(Project.NameKey, Repository, CheckerCreation)}. For reading checkers from
 * NoteDb or updating them, refer to {@link #loadForChecker(Project.NameKey, Repository,
 * CheckerUuid)}.
 *
 * <p><strong>Note:</strong> Any modification (checker creation or update) only becomes permanent
 * (and hence written to NoteDb) if {@link #commit(MetaDataUpdate)} is called.
 *
 * <p><strong>Warning:</strong> This class is a low-level API for checkers in NoteDb. Most code
 * which deals with checkers should use {@link Checkers} or {@link CheckersUpdate} instead.
 *
 * <h2>Internal details</h2>
 *
 * <p>Each checker is represented by a commit on a branch as defined by {@link
 * CheckerUuid#toRefName()}. Previous versions of the checker exist as older commits on the same
 * branch and can be reached by following along the parent references. New commits for updates are
 * only created if a real modification occurs.
 *
 * <p>Within each commit, the properties of a checker are stored in <em>checker.config</em> file
 * (further specified by {@link CheckerConfigEntry}). The <em>checker.config</em> file is formatted
 * as a JGit {@link Config} file.
 */
@VisibleForTesting
public class CheckerConfig extends VersionedMetaData {
  @VisibleForTesting public static final String CHECKER_CONFIG_FILE = "checker.config";

  /**
   * Creates a {@code CheckerConfig} for a new checker from the {@code CheckerCreation} blueprint.
   * Further, optional properties can be specified by setting an {@code CheckerUpdate} via {@link
   * #setCheckerUpdate(CheckerUpdate)} on the returned {@code CheckerConfig}.
   *
   * <p><strong>Note:</strong> The returned {@code CheckerConfig} has to be committed via {@link
   * #commit(MetaDataUpdate)} in order to create the checker for real.
   *
   * @param projectName the name of the project which holds the NoteDb commits for checkers
   * @param repository the repository which holds the NoteDb commits for checkers
   * @param checkerCreation a {@code CheckerCreation} specifying all properties which are required
   *     for a new checker
   * @return a {@code CheckerConfig} for a checker creation
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException if a checker with the same UUID already exists but can't be read
   *     due to an invalid format
   * @throws DuplicateKeyException if a checker with the same UUID already exists
   */
  public static CheckerConfig createForNewChecker(
      Project.NameKey projectName, Repository repository, CheckerCreation checkerCreation)
      throws IOException, ConfigInvalidException, DuplicateKeyException {
    CheckerConfig checkerConfig =
        loadForChecker(projectName, repository, checkerCreation.getCheckerUuid());
    checkerConfig.setCheckerCreation(checkerCreation);
    return checkerConfig;
  }

  /**
   * Creates a {@code CheckerConfig} for an existing checker.
   *
   * <p>The checker is automatically loaded within this method and can be accessed via {@link
   * #getLoadedChecker()}.
   *
   * <p>It's safe to call this method for non-existing checkers. In that case, {@link
   * #getLoadedChecker()} won't return any checker. Thus, the existence of a checker can be easily
   * tested.
   *
   * <p>The checker represented by the returned {@code CheckerConfig} can be updated by setting an
   * {@code CheckerUpdate} via {@link #setCheckerUpdate(CheckerUpdate)} and committing the {@code
   * CheckerConfig} via {@link #commit(MetaDataUpdate)}.
   *
   * @param projectName the name of the project which holds the NoteDb commits for checkers
   * @param repository the repository which holds the NoteDb commits for checkers
   * @param checkerUuid the UUID of the checker
   * @return a {@code CheckerConfig} for the checker with the specified UUID
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException if the checker exists but can't be read due to an invalid format
   */
  public static CheckerConfig loadForChecker(
      Project.NameKey projectName, Repository repository, CheckerUuid checkerUuid)
      throws IOException, ConfigInvalidException {
    CheckerConfig checkerConfig = new CheckerConfig(checkerUuid);
    checkerConfig.load(projectName, repository);
    return checkerConfig;
  }

  /**
   * Creates a {@code CheckerConfig} for a known checker ref.
   *
   * <p>The checker is automatically loaded within this method and can be accessed via {@link
   * #getLoadedChecker()}.
   *
   * <p>It's safe to call this method for non-existing checkers. In that case, {@link
   * #getLoadedChecker()} won't return any checker. Thus, the existence of a checker can be easily
   * tested.
   *
   * <p>The checker represented by the returned {@code CheckerConfig} can be updated by setting an
   * {@code CheckerUpdate} via {@link #setCheckerUpdate(CheckerUpdate)} and committing the {@code
   * CheckerConfig} via {@link #commit(MetaDataUpdate)}.
   *
   * @param projectName the name of the project which holds the NoteDb commits for checkers
   * @param repository the repository which holds the NoteDb commits for checkers
   * @param ref the checker ref; the refname must pass {@link CheckerRef#isRefsCheckers(String)}.
   * @return a {@code CheckerConfig} for the checker with the specified UUID
   * @throws IOException if the repository can't be accessed for some reason
   * @throws ConfigInvalidException if the checker exists but can't be read due to an invalid format
   */
  public static CheckerConfig loadForChecker(
      Project.NameKey projectName, Repository repository, Ref ref)
      throws IOException, ConfigInvalidException {
    CheckerConfig checkerConfig = new CheckerConfig(ref.getName());
    checkerConfig.load(projectName, repository);
    return checkerConfig;
  }

  private final String ref;

  private Optional<CheckerUuid> checkerUuid;
  private Optional<Checker> loadedChecker = Optional.empty();
  private Optional<CheckerCreation> checkerCreation = Optional.empty();
  private Optional<CheckerUpdate> checkerUpdate = Optional.empty();
  private Optional<Checker.Builder> updatedCheckerBuilder = Optional.empty();
  private Optional<Config> loadedConfig = Optional.empty();
  private boolean isLoaded = false;

  private CheckerConfig(String ref) {
    checkArgument(CheckerRef.isRefsCheckers(ref), "expected checker ref: %s", ref);
    this.ref = ref;
    this.checkerUuid = Optional.empty();
  }

  private CheckerConfig(CheckerUuid checkerUuid) {
    this(checkerUuid.toRefName());
    this.checkerUuid = Optional.of(checkerUuid);
  }

  /**
   * Returns the checker loaded from NoteDb.
   *
   * <p>If not any NoteDb commits exist for the checker represented by this {@code CheckerConfig},
   * no checker is returned.
   *
   * <p>After {@link #commit(MetaDataUpdate)} was called on this {@code CheckerConfig}, this method
   * returns a checker which is in line with the latest NoteDb commit for this checker. So, after
   * creating a {@code CheckerConfig} for a new checker and committing it, this method can be used
   * to retrieve a representation of the created checker. The same holds for the representation of
   * an updated checker.
   *
   * @return the loaded checker, or an empty {@code Optional} if the checker doesn't exist
   */
  public Optional<Checker> getLoadedChecker() {
    checkLoaded();

    if (updatedCheckerBuilder.isPresent()) {
      // There have been updates to the checker that have not been applied to the loaded checker
      // yet, apply them now. This has to be done here because in the onSave(CommitBuilder) method
      // where the checker updates are committed we do not know the new SHA1 for the ref state
      // yet.
      loadedChecker = Optional.of(updatedCheckerBuilder.get().setRefState(revision).build());
      updatedCheckerBuilder = Optional.empty();
    }

    return loadedChecker;
  }

  /**
   * Specifies how the current checker should be updated.
   *
   * <p>If the checker is newly created, the {@code CheckerUpdate} can be used to specify optional
   * properties.
   *
   * <p><strong>Note:</strong> This method doesn't perform the update. It only contains the
   * instructions for the update. To apply the update for real and write the result back to NoteDb,
   * call {@link #commit(MetaDataUpdate)} on this {@code CheckerConfig}.
   *
   * @param checkerUpdate an {@code CheckerUpdate} outlining the modifications which should be
   *     applied
   */
  public void setCheckerUpdate(CheckerUpdate checkerUpdate) {
    this.checkerUpdate = Optional.of(checkerUpdate);
  }

  private void setCheckerCreation(CheckerCreation checkerCreation) throws DuplicateKeyException {
    checkLoaded();
    if (loadedChecker.isPresent()) {
      throw new DuplicateKeyException(
          String.format("Checker %s already exists", loadedChecker.get().getUuid()));
    }

    this.checkerCreation = Optional.of(checkerCreation);
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  @VisibleForTesting
  public Optional<Config> getConfigForTesting() {
    return loadedConfig;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    if (revision != null) {
      rw.reset();
      rw.markStart(revision);
      rw.sort(RevSort.REVERSE);
      RevCommit earliestCommit = rw.next();
      Timestamp created = new Timestamp(earliestCommit.getCommitTime() * 1000L);
      Timestamp updated = new Timestamp(rw.parseCommit(revision).getCommitTime() * 1000L);

      Config checkerConfig = readConfig(CHECKER_CONFIG_FILE);
      Checker checker = createFrom(checkerConfig, created, updated, revision.toObjectId());
      loadedChecker = Optional.of(checker);
      loadedConfig = Optional.of(checkerConfig);
      checkerUuid = Optional.of(checker.getUuid());
    }

    isLoaded = true;
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    checkLoaded();
    if (!checkerCreation.isPresent() && !checkerUpdate.isPresent()) {
      // Checker was neither created nor changed. -> A new commit isn't necessary.
      return false;
    }

    ensureThatMandatoryPropertiesAreSet();

    // Commit timestamps are internally truncated to seconds. To return the correct 'created' time
    // for new checkers, we explicitly need to truncate the timestamp here.
    Timestamp commitTimestamp =
        Timestamp.from(TimeUtil.truncateToSecond(commit.getCommitter().getWhen().toInstant()));
    commit.setAuthor(new PersonIdent(commit.getAuthor(), commitTimestamp));
    commit.setCommitter(new PersonIdent(commit.getCommitter(), commitTimestamp));

    Config oldConfig = copyOrElseNew(loadedConfig);
    Config newConfig = copyOrElseNew(loadedConfig);

    Checker.Builder checker = updateChecker(newConfig, commitTimestamp);

    if (oldConfig.toText().equals(newConfig.toText())) {
      // Don't create a new commit if nothing was changed.
      return false;
    }

    updatedCheckerBuilder = Optional.of(checker);
    checkerUuid = Optional.of(checker.getUuid());
    loadedConfig = Optional.of(newConfig);

    String commitMessage = createCommitMessage(loadedChecker);
    commit.setMessage(commitMessage);

    checkerCreation = Optional.empty();
    checkerUpdate = Optional.empty();

    return true;
  }

  private String describeForError() {
    return checkerUuid.map(CheckerUuid::get).orElse(ref);
  }

  private void ensureThatMandatoryPropertiesAreSet() throws ConfigInvalidException {
    if (getNewName().equals(Optional.of(""))) {
      throw new ConfigInvalidException(
          String.format("Name of the checker %s must be defined", describeForError()));
    }

    if (getNewRepository().equals(Optional.of(""))) {
      throw new ConfigInvalidException(
          String.format("Repository of the checker %s must be defined", describeForError()));
    }
  }

  private void checkLoaded() {
    checkState(isLoaded, "Checker %s not loaded yet", describeForError());
  }

  private Optional<String> getNewName() {
    if (checkerUpdate.isPresent()) {
      return checkerUpdate.get().getName().map(Strings::nullToEmpty).map(String::trim);
    }
    if (checkerCreation.isPresent()) {
      return Optional.of(Strings.nullToEmpty(checkerCreation.get().getName()).trim());
    }
    return Optional.empty();
  }

  private Optional<String> getNewRepository() {
    if (checkerUpdate.isPresent()) {
      return checkerUpdate
          .get()
          .getRepository()
          .map(Project.NameKey::get)
          .map(Strings::nullToEmpty)
          .map(String::trim);
    }
    if (checkerCreation.isPresent()) {
      return Optional.of(Strings.nullToEmpty(checkerCreation.get().getRepository().get()).trim());
    }
    return Optional.empty();
  }

  private Checker.Builder updateChecker(Config config, Timestamp commitTimestamp)
      throws IOException, ConfigInvalidException {
    updateCheckerProperties(config);
    Timestamp created = loadedChecker.map(Checker::getCreated).orElse(commitTimestamp);
    return createBuilderFrom(config, created, commitTimestamp);
  }

  private void updateCheckerProperties(Config config) throws IOException {
    checkerCreation.ifPresent(
        checkerCreation ->
            Arrays.stream(CheckerConfigEntry.values())
                .forEach(configEntry -> configEntry.initNewConfig(config, checkerCreation)));
    checkerUpdate.ifPresent(
        checkerUpdate ->
            Arrays.stream(CheckerConfigEntry.values())
                .forEach(configEntry -> configEntry.updateConfigValue(config, checkerUpdate)));
    saveConfig(CHECKER_CONFIG_FILE, config);
  }

  private Checker.Builder createBuilderFrom(Config config, Timestamp created, Timestamp updated)
      throws ConfigInvalidException {
    Checker.Builder checker = Checker.builder();

    // Populate UUID first so it can be used while reading other fields. The checkerUuid field may
    // or may not be present at this point; passing it in causes readFromConfig to double-check
    // equality.
    CheckerConfigEntry.UUID.readFromConfig(checkerUuid.orElse(null), checker, config);

    for (CheckerConfigEntry configEntry : CheckerConfigEntry.values()) {
      if (configEntry == CheckerConfigEntry.UUID) {
        continue;
      }
      configEntry.readFromConfig(checker.getUuid(), checker, config);
    }
    return checker.setCreated(created).setUpdated(updated);
  }

  private Checker createFrom(Config config, Timestamp created, Timestamp updated, ObjectId refState)
      throws ConfigInvalidException {
    return createBuilderFrom(config, created, updated).setRefState(refState).build();
  }

  private static String createCommitMessage(Optional<Checker> originalChecker) {
    return originalChecker.isPresent() ? "Update checker" : "Create checker";
  }

  private static Config copyOrElseNew(Optional<Config> config) throws ConfigInvalidException {
    return config.isPresent() ? copyConfig(config.get()) : new Config();
  }

  /**
   * Copies the provided config.
   *
   * <p><strong>Note:</strong> This method only works for configs that have no base config.
   * Unfortunately we cannot check that the provided config has no base config, so callers must take
   * care to use this method only for configs without base config.
   *
   * @param config the config that should be copied
   * @return the copy of the config
   */
  private static Config copyConfig(Config config) throws ConfigInvalidException {
    Config copiedConfig = new Config();
    copiedConfig.fromText(config.toText());
    return copiedConfig;
  }
}
