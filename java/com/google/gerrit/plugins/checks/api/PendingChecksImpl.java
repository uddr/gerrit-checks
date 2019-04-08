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
// limitations under the License

package com.google.gerrit.plugins.checks.api;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class PendingChecksImpl implements PendingChecks {
  private final Provider<QueryPendingChecks> queryPendingChecksProvider;

  @Inject
  PendingChecksImpl(Provider<QueryPendingChecks> queryPendingChecksProvider) {
    this.queryPendingChecksProvider = queryPendingChecksProvider;
  }

  @Override
  public QueryRequest query() {
    return new QueryRequest() {
      @Override
      public List<PendingChecksInfo> get() throws RestApiException {
        return PendingChecksImpl.this.query(this);
      }
    };
  }

  private List<PendingChecksInfo> query(QueryRequest queryRequest) throws RestApiException {
    try {
      QueryPendingChecks queryPendingChecks = queryPendingChecksProvider.get();
      queryPendingChecks.setQuery(queryRequest.getQuery());
      return queryPendingChecks.apply(TopLevelResource.INSTANCE);
    } catch (Exception e) {
      throw asRestApiException("Cannot query pending checks", e);
    }
  }
}
