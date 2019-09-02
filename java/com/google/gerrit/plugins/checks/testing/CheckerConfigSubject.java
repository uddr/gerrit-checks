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

import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.git.testing.ObjectIdSubject.objectIds;
import static com.google.gerrit.truth.OptionalSubject.optionals;

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.StandardSubjectBuilder;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.git.testing.ObjectIdSubject;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.db.CheckerConfig;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.truth.OptionalSubject;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

public class CheckerConfigSubject extends Subject {
  public static CheckerConfigSubject assertThat(CheckerConfig checkerConfig) {
    return assertAbout(CheckerConfigSubject::new).that(checkerConfig);
  }

  private final CheckerConfig checkerConfig;

  private CheckerConfigSubject(FailureMetadata metadata, CheckerConfig checkerConfig) {
    super(metadata, checkerConfig);
    this.checkerConfig = checkerConfig;
  }

  public void hasUuid(CheckerUuid expectedUuid) {
    check("uuid()").that(checker().getUuid()).isEqualTo(expectedUuid);
  }

  public void hasName(String expectedName) {
    check("name()").that(checker().getName()).isEqualTo(expectedName);
  }

  public OptionalSubject<StringSubject, String> hasDescriptionThat() {
    return check("description()")
        .about(optionals())
        .that(checker().getDescription(), StandardSubjectBuilder::that);
  }

  public OptionalSubject<StringSubject, String> hasUrlThat() {
    return check("url()").about(optionals()).that(checker().getUrl(), StandardSubjectBuilder::that);
  }

  public void hasRepository(Project.NameKey expectedRepository) {
    check("repository()").that(checker().getRepository()).isEqualTo(expectedRepository);
  }

  public void hasStatus(CheckerStatus expectedStatus) {
    check("status()").that(checker().getStatus()).isEqualTo(expectedStatus);
  }

  public IterableSubject hasBlockingConditionSetThat() {
    return check("blockingConditions()").that(checker().getBlockingConditions());
  }

  public void hasQuery(String expectedQuery) {
    check("query()").about(optionals()).that(checker().getQuery()).value().isEqualTo(expectedQuery);
  }

  public void hasNoQuery() {
    check("query()").about(optionals()).that(checker().getQuery()).isEmpty();
  }

  public ComparableSubject<Timestamp> hasCreatedThat() {
    return check("created()").that(checker().getCreated());
  }

  public ObjectIdSubject hasRefStateThat() {
    return check("refState()").about(objectIds()).that(checker().getRefState());
  }

  public IterableSubject configStringList(String name) {
    isNotNull();
    Optional<Config> config = checkerConfig.getConfigForTesting();
    check("configValueOf(checker.%s)", name).about(optionals()).that(config).isPresent();
    return check("configValueOf(checker.%s)", name)
        .that(config.get().getStringList("checker", null, name))
        .asList();
  }

  private Checker checker() {
    isNotNull();
    Optional<Checker> checker = checkerConfig.getLoadedChecker();
    if (!checker.isPresent()) {
      failWithActual(simpleFact("expected checker to be loaded"));
    }
    return checker.get();
  }
}
