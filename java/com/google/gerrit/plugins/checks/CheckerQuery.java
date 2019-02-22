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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.index.query.QueryParser.AND;
import static com.google.gerrit.index.query.QueryParser.DEFAULT_FIELD;
import static com.google.gerrit.index.query.QueryParser.FIELD_NAME;
import static com.google.gerrit.index.query.QueryParser.NOT;
import static com.google.gerrit.index.query.QueryParser.OR;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryParser;
import org.antlr.runtime.tree.Tree;

public class CheckerQuery {
  // Note that this list contains *operators*, not predicates. If there are multiple operators
  // aliased together to the same predicate ("f:", "file:"), they all need to be listed explicitly.
  //
  // We chose to list operators instead of predicates because:
  //  * It insulates us from changes in implementation details of the query system, such as
  //    predicate classes being renamed, or additional predicates being ORed into existing
  //    operators.
  //  * It's easier to keep in sync with the documentation.
  //
  // This doesn't rule out switching to predicates in the future, particularly if the predicate
  // classes gain some informative methods like "boolean matchMethodNeedsToQueryIndex()".
  //
  // Predicates that definitely cannot be allowed:
  //  * Anything where match() needs to query the index, i.e. any full-text fields. This includes
  //    the default field.
  //  * Anything where post-filtering is unreasonably expensive.
  //  * Any predicate over projects, since that may conflict with the projects field.
  //
  // Beyond that, this set is mostly based on what we subjectively consider useful for limiting the
  // changes that a checker runs on. It will probably grow, based on user feedback.
  private static final ImmutableSortedSet<String> ALLOWED_OPERATORS =
      ImmutableSortedSet.of(
          "added",
          "after",
          "age",
          "assignee",
          "author",
          "before",
          "branch",
          "committer",
          "deleted",
          "delta",
          "destination",
          "dir",
          "directory",
          "ext",
          "extension",
          "f",
          "file",
          "footer",
          "hashtag",
          "intopic",
          // TODO(dborowitz): Support some is: operators but not others. Definitely not is:starred.
          "label",
          "onlyextensions",
          "onlyexts",
          "ownerin",
          "path",
          "r",
          "ref",
          "reviewer",
          "reviewerin",
          "size",
          "status",
          "submittable",
          "topic",
          "unresolved",
          "wip");

  /**
   * Cleans a query string for storage in the checker configuration.
   *
   * <p>The query string is interpreted as a change query. Only a subset of query operators are
   * supported, as listed in the REST API documentation and {@link #ALLOWED_OPERATORS}.
   *
   * @param query a change query string.
   * @return the query string, trimmed. May be empty, which indicates all changes match.
   * @throws BadRequestException if the query is not a valid query, or it uses operators outside of
   *     the allowed set.
   */
  public static String clean(String query) throws BadRequestException {
    String trimmed = requireNonNull(query).trim();
    if (trimmed.isEmpty()) {
      return trimmed;
    }
    try {
      checkOperators(QueryParser.parse(query));
    } catch (QueryParseException e) {
      throw new BadRequestException("Invalid query: " + query + "\n" + e.getMessage(), e);
    }

    return trimmed;
  }

  private static void checkOperators(Tree node) throws BadRequestException {
    switch (node.getType()) {
      case AND:
      case OR:
      case NOT:
        for (int i = 0; i < node.getChildCount(); i++) {
          checkOperators(node.getChild(i));
        }
        break;

      case FIELD_NAME:
        if (!ALLOWED_OPERATORS.contains(node.getText())) {
          throw new BadRequestException("Unsupported operator: " + node);
        }
        break;

      case DEFAULT_FIELD:
        throw new BadRequestException("Specific search operator required: " + getOnlyChild(node));

      default:
        throw new BadRequestException("Unsupported query: " + node);
    }
  }

  private static Tree getOnlyChild(Tree node) {
    checkState(node.getChildCount() == 1, "expected 1 child: %s", node);
    return node.getChild(0);
  }

  private CheckerQuery() {}
}
