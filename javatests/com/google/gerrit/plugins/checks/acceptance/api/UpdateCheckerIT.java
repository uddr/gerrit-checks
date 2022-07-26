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

package com.google.gerrit.plugins.checks.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.git.testing.CommitSubject.assertCommit;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerQuery;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerOperations.PerCheckerOperations;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerTestData;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.gerrit.plugins.checks.api.CheckerInput;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.db.CheckersByRepositoryNotes;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

@SkipProjectClone
@UseClockStep(startAtEpoch = true)
public class UpdateCheckerIT extends AbstractCheckersTest {
  private static final int MAX_INDEX_TERMS = 10;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setInt("index", null, "maxTerms", MAX_INDEX_TERMS);
    return cfg;
  }

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Test
  public void updateMultipleCheckerPropertiesAtOnce() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().name("my-checker").repository(allProjects).create();
    Checker checker = checkerOperations.checker(checkerUuid).get();

    Project.NameKey repositoryName = projectOperations.newProject().create();

    CheckerInput input = new CheckerInput();
    input.name = "my-renamed-checker";
    input.description = "A description.";
    input.url = "http://example.com/my-checker";
    input.repository = repositoryName.get();

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.uuid).isEqualTo(checkerUuid.get());
    assertThat(info.name).isEqualTo(input.name);
    assertThat(info.description).isEqualTo(input.description);
    assertThat(info.url).isEqualTo(input.url);
    assertThat(info.repository).isEqualTo(input.repository);
    assertThat(info.created).isEqualTo(checker.getCreated());
    assertThat(info.created).isLessThan(info.updated);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(CheckersByRepositoryNotes.computeRepositorySha1(repositoryName));
    assertThat(checkerOperations.checkersOf(repositoryName))
        .containsExactly(CheckerUuid.parse(info.uuid));
  }

  @Test
  public void uuidCannotBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput input = new CheckerInput();
    input.uuid = "some:id";

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.id(checkerUuid).update(input));
    assertThat(thrown).hasMessageThat().contains("uuid cannot be updated");
  }

  @Test
  public void uuidIsAllowedIfItMatchesCurrentUuid() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput input = new CheckerInput();
    input.uuid = checkerUuid.get();
    input.name = "some-name";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.uuid).isEqualTo(input.uuid);
    assertThat(info.name).isEqualTo(input.name);
  }

  @Test
  public void updateCheckerName() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.name = "my-renamed-checker";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.name).isEqualTo(input.name);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
  }

  @Test
  public void cannotSetCheckerNameToEmptyString() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.name = "";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> checkersApi.id(checkerUuid).update(checkerInput));
    assertThat(thrown).hasMessageThat().contains("name cannot be unset");
  }

  @Test
  public void cannotSetCheckerNameToStringWhichIsEmptyAfterTrim() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.name = " ";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> checkersApi.id(checkerUuid).update(checkerInput));
    assertThat(thrown).hasMessageThat().contains("name cannot be unset");
  }

  @Test
  public void updateCheckerNameToNameThatIsAlreadyUsed() throws Exception {
    checkerOperations.newChecker().name("other-checker").create();

    CheckerUuid checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.name = "other-checker";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.name).isEqualTo(input.name);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
  }

  @Test
  public void addCheckerDescription() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput input = new CheckerInput();
    input.description = "A description.";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.description).isEqualTo(input.description);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
  }

  @Test
  public void updateCheckerDescription() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().description("A description.").create();

    CheckerInput input = new CheckerInput();
    input.description = "A new description.";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.description).isEqualTo(input.description);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
  }

  @Test
  public void unsetCheckerDescription() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().description("A description.").create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.description = "";

    CheckerInfo info = checkersApi.id(checkerUuid).update(checkerInput);
    assertThat(info.description).isNull();

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
  }

  @Test
  public void checkerDescriptionIsTrimmed() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput input = new CheckerInput();
    input.description = " A description. ";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.description).isEqualTo("A description.");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
  }

  @Test
  public void addCheckerUrl() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput input = new CheckerInput();
    input.url = "http://example.com/my-checker";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.url).isEqualTo(input.url);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
  }

  @Test
  public void updateCheckerUrl() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().url("http://example.com/my-checker").create();

    CheckerInput input = new CheckerInput();
    input.url = "http://example.com/my-checker-foo";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.url).isEqualTo(input.url);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
  }

  @Test
  public void unsetCheckerUrl() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().url("http://example.com/my-checker").create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.url = "";

    CheckerInfo info = checkersApi.id(checkerUuid).update(checkerInput);
    assertThat(info.url).isNull();

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
  }

  @Test
  public void checkerUrlIsTrimmed() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput input = new CheckerInput();
    input.url = " http://example.com/my-checker ";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.url).isEqualTo("http://example.com/my-checker");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
  }

  @Test
  public void updateRepository() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(allProjects).create();

    Project.NameKey repositoryName = projectOperations.newProject().create();

    CheckerInput input = new CheckerInput();
    input.repository = repositoryName.get();

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.repository).isEqualTo(input.repository);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(checkerUuid);
    assertCommit(
        perCheckerOps.commit(), "Update checker", info.updated, perCheckerOps.get().getRefState());
    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(CheckersByRepositoryNotes.computeRepositorySha1(repositoryName));
    assertThat(checkerOperations.checkersOf(repositoryName))
        .containsExactly(CheckerUuid.parse(info.uuid));
  }

  @Test
  public void cannotSetRepositoryToEmptyString() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.repository = "";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> checkersApi.id(checkerUuid).update(checkerInput));
    assertThat(thrown).hasMessageThat().contains("repository cannot be unset");
  }

  @Test
  public void cannotSetRepositoryToStringWhichIsEmptyAfterTrim() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.repository = " ";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> checkersApi.id(checkerUuid).update(checkerInput));
    assertThat(thrown).hasMessageThat().contains("repository cannot be unset");
  }

  @Test
  public void cannotSetNonExistingRepository() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput checkerInput = new CheckerInput();
    checkerInput.repository = "non-existing";

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> checkersApi.id(checkerUuid).update(checkerInput));
    assertThat(thrown).hasMessageThat().contains("repository non-existing not found");
  }

  @Test
  public void cannotSetUrlToInvalidUrl() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput input = new CheckerInput();
    input.url = CheckerTestData.INVALID_URL;
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.id(checkerUuid).update(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("only http/https URLs supported: ftp://example.com/my-checker");
  }

  @Test
  public void disableAndReenable() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(allProjects).create();
    assertThat(checkerOperations.checkersOf(allProjects)).containsExactly(checkerUuid);

    CheckerInput input = new CheckerInput();
    input.status = CheckerStatus.DISABLED;

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.status).isEqualTo(CheckerStatus.DISABLED);
    assertThat(checkerOperations.checkersOf(allProjects)).isEmpty();

    input = new CheckerInput();
    input.status = CheckerStatus.ENABLED;
    info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.status).isEqualTo(CheckerStatus.ENABLED);
    assertThat(checkerOperations.checkersOf(allProjects)).containsExactly(checkerUuid);
  }

  @Test
  public void updateRepositoryDuringDisable() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(allProjects).create();

    Project.NameKey repositoryName = projectOperations.newProject().create();

    CheckerInput input = new CheckerInput();
    input.repository = repositoryName.get();
    input.status = CheckerStatus.DISABLED;

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.repository).isEqualTo(input.repository);
    assertThat(info.status).isEqualTo(CheckerStatus.DISABLED);
    assertThat(checkerOperations.checkersOf(allProjects)).isEmpty();
  }

  @Test
  public void updateRepositoryDuringEnable() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(allProjects).create();

    Project.NameKey repositoryName = projectOperations.newProject().create();
    assertThat(checkerOperations.checkersOf(allProjects)).containsExactly(checkerUuid);
    assertThat(checkerOperations.checkersOf(repositoryName)).isEmpty();

    CheckerInput input = new CheckerInput();
    input.status = CheckerStatus.DISABLED;

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.status).isEqualTo(CheckerStatus.DISABLED);
    assertThat(checkerOperations.checkersOf(allProjects)).isEmpty();
    assertThat(checkerOperations.checkersOf(repositoryName)).isEmpty();

    input = new CheckerInput();
    input.status = CheckerStatus.ENABLED;
    input.repository = repositoryName.get();
    info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.status).isEqualTo(CheckerStatus.ENABLED);
    assertThat(checkerOperations.checkersOf(allProjects)).isEmpty();
    assertThat(checkerOperations.checkersOf(repositoryName)).containsExactly(checkerUuid);
  }

  @Test
  public void updateCheckerWithBlockingConditions() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput input = new CheckerInput();
    input.blocking = ImmutableSet.of(BlockingCondition.STATE_NOT_PASSING);
    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.blocking).containsExactly(BlockingCondition.STATE_NOT_PASSING);
  }

  @Test
  public void updateQuery() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput input = new CheckerInput();
    input.query = "f:foo";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.query).isEqualTo("f:foo");

    input = new CheckerInput();
    input.query = "";

    info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.query).isNull();
  }

  @Test
  public void updateWithEmptyQueryAfterTrimClearsQuery() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    CheckerInput input = new CheckerInput();
    input.query = "f:foo";

    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.query).isEqualTo("f:foo");

    input = new CheckerInput();
    input.query = " ";

    info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.query).isNull();
  }

  @Test
  public void updateWithUnsupportedOperatorInQuery() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    Optional<String> oldQuery = checkerOperations.checker(checkerUuid).get().getQuery();

    CheckerInput input = new CheckerInput();
    input.query = CheckerTestData.QUERY_WITH_UNSUPPORTED_OPERATOR;
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.id(checkerUuid).update(input));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Unsupported operator: " + CheckerTestData.UNSUPPORTED_OPERATOR);

    assertThat(checkerOperations.checker(checkerUuid).get().getQuery()).isEqualTo(oldQuery);
  }

  @Test
  public void updateWithInvalidQuery() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    Optional<String> oldQuery = checkerOperations.checker(checkerUuid).get().getQuery();

    CheckerInput input = new CheckerInput();
    input.query = CheckerTestData.INVALID_QUERY;
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.id(checkerUuid).update(input));
    assertThat(thrown).hasMessageThat().contains("Invalid query: " + input.query);

    assertThat(checkerOperations.checker(checkerUuid).get().getQuery()).isEqualTo(oldQuery);
  }

  @Test
  public void updateWithTooLongQuery() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    Optional<String> oldQuery = checkerOperations.checker(checkerUuid).get().getQuery();

    CheckerInput input = new CheckerInput();
    input.query = CheckerTestData.longQueryWithSupportedOperators(MAX_INDEX_TERMS * 2);
    assertThat(CheckerQuery.clean(input.query)).isEqualTo(input.query);
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.id(checkerUuid).update(input));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            "change query of checker "
                + checkerUuid
                + " is invalid: "
                + input.query
                + " (too many terms in query: 42 terms (max = 10))");

    assertThat(checkerOperations.checker(checkerUuid).get().getQuery()).isEqualTo(oldQuery);
  }

  @Test
  public void updateResultsInNewUpdatedTimestamp() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    Timestamp expectedUpdateTimestamp = TestTimeUtil.getCurrentTimestamp();
    CheckerInput input = new CheckerInput();
    input.name = "My Checker";
    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.updated).isEqualTo(expectedUpdateTimestamp);
  }

  @Test
  public void noOpUpdateDoesntResultInNewUpdatedTimestamp() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().name("My Checker").create();

    Timestamp expectedUpdateTimestamp = checkerOperations.checker(checkerUuid).get().getUpdated();

    CheckerInput input = new CheckerInput();
    input.name = "My Checker";
    CheckerInfo info = checkersApi.id(checkerUuid).update(input);
    assertThat(info.updated).isEqualTo(expectedUpdateTimestamp);
  }

  @Test
  public void updateCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    requestScopeOperations.setApiUser(user.id());

    CheckerInput input = new CheckerInput();
    input.name = "my-renamed-checker";

    AuthException thrown =
        assertThrows(AuthException.class, () -> checkersApi.id(checkerUuid).update(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("administrateCheckers for plugin checks not permitted");
  }

  @Test
  public void updateCheckerAnonymouslyFails() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    requestScopeOperations.setApiUserAnonymous();

    CheckerInput input = new CheckerInput();
    input.name = "my-renamed-checker";

    AuthException thrown =
        assertThrows(AuthException.class, () -> checkersApi.id(checkerUuid).update(input));
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }
}
