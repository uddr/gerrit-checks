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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UUID of a checker.
 *
 * <p>UUIDs are of the form {@code SCHEME ':' ID}, where:
 *
 * <ul>
 *   <li>The allowed characters within either part are {@code [a-zA-Z0-9._-]}.
 *   <li>Scheme is a short string that, by convention, is associated with the external system that
 *       created the checker (e.g. {@code jenkins}).
 *   <li>ID is an arbitrary string provided by the external system.
 * </ul>
 */
@AutoValue
public abstract class CheckerUuid implements Comparable<CheckerUuid> {
  private static final Pattern UUID_PATTERN;

  static {
    // The set of allowed characters is somewhat arbitrarily small and may be expanded in the future
    // if there is a concrete use case. Some considerations:
    //  * It's nice for UUIDs to be URL-safe.
    //  * UUIDs are currently stored one per line in the CheckersByRepositoryNotes format, so
    //    newlines would be problematic.
    //  * UUIDs are stored in git config values.
    String part = "[a-zA-Z0-9._-]+";
    UUID_PATTERN = Pattern.compile(String.format("^(?<scheme>%s):(?<id>%s)$", part, part));
  }

  /**
   * Attempts to parse the given UUID string into a {@code CheckerUuid}.
   *
   * @param uuid UUID string.
   * @return new UUID if {@code uuid} is a valid UUID, or empty otherwise.
   */
  public static Optional<CheckerUuid> tryParse(@Nullable String uuid) {
    if (uuid == null) {
      return Optional.empty();
    }
    Matcher m = UUID_PATTERN.matcher(uuid);
    if (!m.find()) {
      return Optional.empty();
    }
    return Optional.of(new AutoValue_CheckerUuid(m.group("scheme"), m.group("id")));
  }

  /**
   * Returns whether the given input is a valid UUID string.
   *
   * @param uuid UUID string.
   * @return true if {@code uuid} is a valid UUID, false otherwise.
   */
  public static boolean isUuid(@Nullable String uuid) {
    return uuid != null && UUID_PATTERN.matcher(uuid).matches();
  }

  /**
   * Parses the given UUID string into a {@code CheckerUuid}, throwing an unchecked exception if it
   * is not in the proper format.
   *
   * @param uuid UUID string.
   * @return new UUID.
   */
  public static CheckerUuid parse(String uuid) {
    return tryParse(uuid)
        .orElseThrow(() -> new IllegalArgumentException("invalid checker UUID: " + uuid));
  }

  /**
   * Scheme portion of the UUID.
   *
   * @return the scheme.
   */
  public abstract String scheme();

  /**
   * ID portion of the UUID.
   *
   * @return the ID.
   */
  public abstract String id();

  /**
   * Computes the SHA-1 of the UUID, for use in the Git storage layer where SHA-1s are used as keys.
   *
   * @return hex SHA-1 of this UUID's string representation.
   */
  @SuppressWarnings("deprecation") // SHA-1 used where Git object IDs are required.
  public String sha1() {
    return Hashing.sha1().hashString(toString(), UTF_8).toString();
  }

  @Override
  public String toString() {
    return scheme() + ":" + id();
  }

  @Override
  public int compareTo(CheckerUuid o) {
    return toString().compareTo(o.toString());
  }
}
