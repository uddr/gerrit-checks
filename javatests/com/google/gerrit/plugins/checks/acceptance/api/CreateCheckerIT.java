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
import static com.google.gerrit.git.testing.CommitSubject.assertCommit;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
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
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

@UseClockStep(startAtEpoch = true)
public class CreateCheckerIT extends AbstractCheckersTest {
  private static final int MAX_INDEX_TERMS = 10;

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setInt("index", null, "maxTerms", MAX_INDEX_TERMS);
    return cfg;
  }

  @Test
  public void createChecker() throws Exception {
    Project.NameKey repositoryName = projectOperations.newProject().create();

    Timestamp expectedCreationTimestamp = TestTimeUtil.getCurrentTimestamp();
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = repositoryName.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.uuid).isEqualTo("test:my-checker");
    assertThat(info.name).isEqualTo("My Checker");
    assertThat(info.description).isNull();
    assertThat(info.url).isNull();
    assertThat(info.repository).isEqualTo(input.repository);
    assertThat(info.status).isEqualTo(CheckerStatus.ENABLED);
    assertThat(info.blocking).isEmpty();
    assertThat(info.query).isEqualTo("status:open");
    assertThat(info.created).isEqualTo(expectedCreationTimestamp);
    assertThat(info.updated).isEqualTo(info.created);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(CheckersByRepositoryNotes.computeRepositorySha1(repositoryName));
    assertThat(checkerOperations.checkersOf(repositoryName))
        .containsExactly(CheckerUuid.parse(info.uuid));
  }

  @Test
  public void createCheckerWithDescription() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.description = "some description";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.description).isEqualTo(input.description);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerWithUrl() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.url = "http://example.com/my-checker";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.url).isEqualTo(input.url);

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerWithoutNameFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.create(input));
    assertThat(thrown).hasMessageThat().contains("name is required");
  }

  @Test
  public void createCheckerNameIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = " My Checker ";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.name).isEqualTo("My Checker");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerDescriptionIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.description = " some description ";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.description).isEqualTo("some description");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerUrlIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.url = " http://example.com/my-checker ";
    input.repository = allProjects.get();
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.url).isEqualTo("http://example.com/my-checker");

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerRepositoryIsTrimmed() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = " " + allProjects.get() + " ";
    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.repository).isEqualTo(allProjects.get());

    PerCheckerOperations perCheckerOps = checkerOperations.checker(info.uuid);
    assertCommit(
        perCheckerOps.commit(), "Create checker", info.created, perCheckerOps.get().getRefState());
  }

  @Test
  public void createCheckerWithInvalidUrlFails() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().name("my-checker").create();

    CheckerInput input = new CheckerInput();
    input.url = CheckerTestData.INVALID_URL;
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.id(checkerUuid).update(input));
    assertThat(thrown).hasMessageThat().contains("only http/https URLs supported: " + input.url);
  }

  @Test
  public void createCheckersWithSameName() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = allProjects.get();
    CheckerInfo info1 = checkersApi.create(input).get();
    assertThat(info1.name).isEqualTo(input.name);

    input.uuid = "test:another-checker";
    CheckerInfo info2 = checkersApi.create(input).get();
    assertThat(info2.name).isEqualTo(input.name);

    assertThat(info2.uuid).isNotEqualTo(info1.uuid);
  }

  @Test
  public void createCheckerWithExistingUuidFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = allProjects.get();
    checkersApi.create(input).get();

    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> checkersApi.create(input));
    assertThat(thrown).hasMessageThat().contains("Checker test:my-checker already exists");
  }

  @Test
  public void createCheckerWithoutUuidFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "My Checker";
    input.repository = allProjects.get();

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.create(input));
    assertThat(thrown).hasMessageThat().contains("uuid is required");
  }

  @Test
  public void createCheckerWithEmptyUuidFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "";
    input.name = "My Checker";
    input.repository = allProjects.get();

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.create(input));
    assertThat(thrown).hasMessageThat().contains("uuid is required");
  }

  @Test
  public void createCheckerWithEmptyUuidAfterTrimFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = " ";
    input.name = "My Checker";
    input.repository = allProjects.get();

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.create(input));
    assertThat(thrown).hasMessageThat().contains("invalid uuid:  ");
  }

  @Test
  public void createCheckerWithInvalidUuidFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = CheckerTestData.INVALID_UUID;
    input.name = "My Checker";
    input.repository = allProjects.get();

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.create(input));
    assertThat(thrown).hasMessageThat().contains("invalid uuid: " + input.uuid);
  }

  @Test
  public void createCheckerWithoutRepositoryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.create(input));
    assertThat(thrown).hasMessageThat().contains("repository is required");
  }

  @Test
  public void createCheckerWithEmptyRepositoryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = "";

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.create(input));
    assertThat(thrown).hasMessageThat().contains("repository is required");
  }

  @Test
  public void createCheckerWithEmptyRepositoryAfterTrimFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = " ";

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.create(input));
    assertThat(thrown).hasMessageThat().contains("repository is required");
  }

  @Test
  public void createCheckerWithNonExistingRepositoryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = "non-existing";

    UnprocessableEntityException thrown =
        assertThrows(UnprocessableEntityException.class, () -> checkersApi.create(input));
    assertThat(thrown).hasMessageThat().contains("repository non-existing not found");
  }

  @Test
  public void createDisabledChecker() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = allProjects.get();
    input.status = CheckerStatus.DISABLED;

    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.status).isEqualTo(CheckerStatus.DISABLED);
  }

  @Test
  public void createCheckerWithBlockingConditions() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = allProjects.get();
    input.blocking = ImmutableSet.of(BlockingCondition.STATE_NOT_PASSING);

    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.blocking).containsExactly(BlockingCondition.STATE_NOT_PASSING);
  }

  @Test
  public void createCheckerWithQuery() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = allProjects.get();
    input.query = "f:foo";

    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.query).isEqualTo("f:foo");
  }

  @Test
  public void createCheckerWithEmptyQuery() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = allProjects.get();
    input.query = "";

    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.query).isNull();
  }

  @Test
  public void createCheckerWithEmptyQueryAfterTrim() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = allProjects.get();
    input.query = " ";

    CheckerInfo info = checkersApi.create(input).get();
    assertThat(info.query).isNull();
  }

  @Test
  public void createCheckerWithUnsupportedOperatorInQueryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = allProjects.get();
    input.query = CheckerTestData.QUERY_WITH_UNSUPPORTED_OPERATOR;

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.create(input).get());
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Unsupported operator: " + CheckerTestData.UNSUPPORTED_OPERATOR);
  }

  @Test
  public void createCheckerWithInvalidQueryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = allProjects.get();
    input.query = CheckerTestData.INVALID_QUERY;

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.create(input).get());
    assertThat(thrown).hasMessageThat().contains("Invalid query: " + input.query);
  }

  @Test
  public void createCheckerWithTooLongQueryFails() throws Exception {
    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.name = "My Checker";
    input.repository = allProjects.get();
    input.query = CheckerTestData.longQueryWithSupportedOperators(MAX_INDEX_TERMS * 2);
    assertThat(CheckerQuery.clean(input.query)).isEqualTo(input.query);
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> checkersApi.create(input).get());
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            "change query of checker "
                + input.uuid
                + " is invalid: "
                + input.query
                + " (too many terms in query)");
  }

  @Test
  public void createMultipleCheckers() throws Exception {
    Project.NameKey repositoryName1 = projectOperations.newProject().create();
    Project.NameKey repositoryName2 = projectOperations.newProject().create();

    CheckerUuid checkerUuid1 = checkerOperations.newChecker().repository(repositoryName1).create();
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(repositoryName1).create();
    CheckerUuid checkerUuid3 = checkerOperations.newChecker().repository(repositoryName1).create();
    CheckerUuid checkerUuid4 = checkerOperations.newChecker().repository(repositoryName2).create();
    CheckerUuid checkerUuid5 = checkerOperations.newChecker().repository(repositoryName2).create();

    assertThat(checkerOperations.sha1sOfRepositoriesWithCheckers())
        .containsExactly(
            CheckersByRepositoryNotes.computeRepositorySha1(repositoryName1),
            CheckersByRepositoryNotes.computeRepositorySha1(repositoryName2));
    assertThat(checkerOperations.checkersOf(repositoryName1))
        .containsExactly(checkerUuid1, checkerUuid2, checkerUuid3);
    assertThat(checkerOperations.checkersOf(repositoryName2))
        .containsExactly(checkerUuid4, checkerUuid5);
  }

  @Test
  public void createCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    requestScopeOperations.setApiUser(user.id());

    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();

    AuthException thrown = assertThrows(AuthException.class, () -> checkersApi.create(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("administrateCheckers for plugin checks not permitted");
  }

  @Test
  public void createCheckerAnonymouslyFails() throws Exception {
    requestScopeOperations.setApiUserAnonymous();

    CheckerInput input = new CheckerInput();
    input.uuid = "test:my-checker";
    input.repository = allProjects.get();

    AuthException thrown = assertThrows(AuthException.class, () -> checkersApi.create(input));
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }
}
