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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Test;

public class CheckerQueryTest extends GerritBaseTests {
  @Test
  public void emptyQuery() throws Exception {
    assertValidQuery("");
    assertValidQuery(" ");
    assertValidQuery(" \t ");
  }

  @Test
  public void invalidQueries() throws Exception {
    assertInvalidQuery("f", "Specific search operator required: f");
    assertInvalidQuery("\"foo bar\"", "Specific search operator required: foo bar");
    assertInvalidQuery("{foo bar}", "Specific search operator required: foo bar");
    assertInvalidQuery(
        "f:\"", "Invalid query: f:\"\nline 1:3 mismatched character '<EOF>' expecting '\"'");
  }

  @Test
  public void allowedOperators() throws Exception {
    assertValidQuery("f:foo");
    assertValidQuery("f:\"foo bar\"");
    assertValidQuery("f:{foo bar}");
    assertValidQuery("file:foo");
    assertValidQuery("hashtag:foo");
    assertValidQuery("status:foo");
  }

  @Test
  public void disallowedUnknownOperator() throws Exception {
    String query = "fhqwhgads:bar";
    try {
      newChangeQueryBuilder().parse(query);
      assert_().fail("expected QueryParseException");
    } catch (QueryParseException e) {
      // Expected.
    }
    assertInvalidQuery(query, "Unsupported operator: fhqwhgads");
  }

  @Test
  public void disallowedButValidOperator() throws Exception {
    String query = "project:foo";
    assertThat(newChangeQueryBuilder().parse(query)).isNotNull();
    assertInvalidQuery(query, "Unsupported operator: project");
  }

  @Test
  public void andOperator() throws Exception {
    assertValidQuery("f:foo f:bar");
    assertValidQuery("f:foo AND f:bar");
    assertInvalidQuery("f:foo q:bar", "Unsupported operator: q");
    assertInvalidQuery("f:foo AND q:bar", "Unsupported operator: q");
    assertInvalidQuery("q:foo f:bar", "Unsupported operator: q");
    assertInvalidQuery("q:foo AND f:bar", "Unsupported operator: q");
  }

  @Test
  public void orOperator() throws Exception {
    assertValidQuery("f:foo OR f:bar");
    assertInvalidQuery("f:foo OR q:bar", "Unsupported operator: q");
    assertInvalidQuery("q:foo OR f:bar", "Unsupported operator: q");
  }

  @Test
  public void notOperator() throws Exception {
    assertValidQuery("-f:foo");
    assertInvalidQuery("-q:bar", "Unsupported operator: q");
  }

  @Test
  public void deeplyNestedQuery() throws Exception {
    assertValidQuery("(((f:foo f:bar) OR f:baz) -f:qux) OR f:quux");
    assertInvalidQuery("(((z:foo f:bar) OR f:baz) -f:qux) OR f:quux", "Unsupported operator: z");
    assertInvalidQuery("(((f:foo z:bar) OR f:baz) -f:qux) OR f:quux", "Unsupported operator: z");
    assertInvalidQuery("(((f:foo f:bar) OR z:baz) -f:qux) OR f:quux", "Unsupported operator: z");
    assertInvalidQuery("(((f:foo f:bar) OR f:baz) -z:qux) OR f:quux", "Unsupported operator: z");
    assertInvalidQuery("(((f:foo f:bar) OR f:baz) -f:qux) OR z:quux", "Unsupported operator: z");
  }

  private static void assertInvalidQuery(String query, String expectedMessage) {
    try {
      CheckerQuery.clean(query);
      assert_().fail("expected ConfigInvalidException");
    } catch (ConfigInvalidException e) {
      assertThat(e).hasMessageThat().isEqualTo(expectedMessage);
    }
  }

  private static void assertValidQuery(String query) {
    try {
      assertThat(CheckerQuery.clean(query)).isEqualTo(query.trim());
    } catch (ConfigInvalidException e) {
      throw new AssertionError("expected valid query: " + query, e);
    }
  }

  private static ChangeQueryBuilder newChangeQueryBuilder() {
    return Guice.createInjector(new InMemoryModule()).getInstance(ChangeQueryBuilder.class);
  }
}
