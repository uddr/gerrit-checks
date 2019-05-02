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

package com.google.gerrit.plugins.checks;

import static com.google.common.truth.OptionalSubject.optionals;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.plugins.checks.CheckerUuid.MAX_SCHEME_LENGTH;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.testing.GerritBaseTests;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class CheckerUuidTest extends GerritBaseTests {
  private static final ImmutableSet<String> VALID_CHECKER_UUIDS =
      ImmutableSet.of(
          "test:my-checker-123",
          "TEST:MY-checker",
          "-t-e.s_t-:m.y-ch_ecker",
          "test:my-checker.",
          "test.:my-checker",
          repeat('x', MAX_SCHEME_LENGTH) + ":my-checker",
          "test:" + repeat('x', 2 * MAX_SCHEME_LENGTH),

          // The ID portion does not have to satisfy git-check-ref-format(1).
          "test:my..checker",
          "test:.my-checker",
          "test:my-checker.lock");

  private static final ImmutableSet<String> INVALID_CHECKER_UUIDS =
      ImmutableSet.of(
          "",
          "test",
          ":",
          "test:",
          ":my-checker",
          " test:my-checker",
          "test:my-checker ",
          "test:my checker",
          "test:my!checker",
          "test:my\nchecker",
          "test:my\\nchecker",
          "test:my%0Achecker",
          "test:my\0checker",
          "test:my\\0checker",
          "test:my%00checker",
          repeat('x', MAX_SCHEME_LENGTH + 1) + ":my-checker",

          // The ID portion has to be a valid path component according to git-check-ref-format(1).
          "te..st:my-checker",
          ".test:my-checker",
          "test.lock:my-checker");

  private static String repeat(char c, int len) {
    return Stream.generate(() -> Character.toString(c)).limit(len).collect(joining());
  }

  @Test
  public void isUuid() {
    for (String checkerUuid : VALID_CHECKER_UUIDS) {
      assertWithMessage(checkerUuid).that(CheckerUuid.isUuid(checkerUuid)).isTrue();
    }

    assertThat(CheckerUuid.isUuid(null)).isFalse();
    for (String invalidCheckerUuid : INVALID_CHECKER_UUIDS) {
      assertWithMessage(invalidCheckerUuid).that(CheckerUuid.isUuid(invalidCheckerUuid)).isFalse();
    }
  }

  @Test
  public void parseValidUuids() {
    for (String uuidString : VALID_CHECKER_UUIDS) {
      assertWithMessage(uuidString)
          .about(optionals())
          .that(CheckerUuid.tryParse(uuidString).map(CheckerUuid::get))
          .hasValue(uuidString);
      CheckerUuid checkerUuid;
      try {
        checkerUuid = CheckerUuid.parse(uuidString);
      } catch (RuntimeException e) {
        throw new AssertionError("failed to parse " + uuidString, e);
      }
      assertWithMessage(uuidString).that(checkerUuid.get()).isEqualTo(uuidString);
      assertWithMessage("valid ref name: %s", checkerUuid.toRefName())
          .that(Repository.isValidRefName(checkerUuid.toRefName()))
          .isTrue();
    }
  }

  @Test
  public void parseInvalidUuids() {
    assertInvalid(null);
    for (String uuidString : INVALID_CHECKER_UUIDS) {
      assertInvalid(uuidString);
    }
  }

  @Test
  public void getters() {
    CheckerUuid checkerUuid = CheckerUuid.parse("test:my-checker");
    assertThat(checkerUuid.scheme()).isEqualTo("test");
    assertThat(checkerUuid.id()).isEqualTo("my-checker");
    assertThat(checkerUuid.get()).isEqualTo("test:my-checker");

    // $ echo -n my-checker | sha1sum
    // f4bf6f9d65a2069e1b23de004b626b9a08e58daa  -
    assertThat(checkerUuid.toRefName())
        .isEqualTo("refs/checkers/test/f4/f4bf6f9d65a2069e1b23de004b626b9a08e58daa");
  }

  @Test
  public void sort() {
    assertThat(
            ImmutableSortedSet.of(
                CheckerUuid.parse("fo:a"), CheckerUuid.parse("foo:a"), CheckerUuid.parse("fo-o:a")))
        .containsExactly(
            CheckerUuid.parse("fo-o:a"), CheckerUuid.parse("fo:a"), CheckerUuid.parse("foo:a"))
        .inOrder();
  }

  private static void assertInvalid(@Nullable String uuidString) {
    assertWithMessage(String.valueOf(uuidString))
        .about(optionals())
        .that(CheckerUuid.tryParse(uuidString))
        .isEmpty();
  }
}
