# @PLUGIN@ - /plugins/@PLUGIN@/checks.pending/ REST API

This page describes the pending-checks-related REST endpoints that are
added by the @PLUGIN@ plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

## <a id="pending-checks-endpoints"> Pending Checks Endpoints

### <a id="query-pending-checks"> Query Pending Checks
_'GET /plugins/@PLUGIN@/checks.pending/'_

Queries pending checks for a checker.

Checks are pending if they are in a non-final state and the external
checker system intends to post further updates on them.

Request parameters:

* <a id="query-param"> `query`: Query that should be used to match
  pending checks (required). The query operators which can be used in
  this query are described in the [Query Operators](#query-operators)
  section below.

Limitations for the input query:

* Must contain exactly one [checker](#checker-operator) operator.
* The `checker` operator must either be the only operator in the query
  ('checker:<CHECKER_UUID>'), or appear at the root level as part of an
  `AND` expression (e.g. 'checker:<CHECKER_UUID> state:<state>',
  'checker:<CHECKER_UUID> (state:<state> OR state:<state>)').

If no [state](#state-operator) is used in the input query this REST
endpoint by default only returns checks that are in state
`NOT_STARTED`.

This REST endpoint only returns pending checks for current patch sets.

Note that all users are allowed to query pending checks but the result
includes only checks on changes that are visible to the calling user.
This means pending checks for non-visible changes are filtered out.

#### Request by checker

```
  GET /plugins/@PLUGIN@/checks.pending/?query=checker=test:my-checker+(state:NOT_STARTED+OR+state:SCHEDULED) HTTP/1.0
```

As response a list of [PendingChecksInfo](#pending-checks-info)
entities is returned that describes the pending checks.

#### Response by checker

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8
  )]}'
  [
    {
      "patch_set": {
        "repository": "test-repo",
        "change_number": 1,
        "patch_set_id": 1,
      }
      "pending_checks": {
        "test:my-checker": {
          "state": "NOT_STARTED",
        }
      }
    },
    {
      "patch_set": {
        "repository": "test-repo",
        "change_number": 5,
        "patch_set_id": 2,
      }
      "pending_checks": {
        "test:my-checker": {
          "state": "SCHEDULED",
        }
      }
    }
  ]
```

## <a id="json-entities"> JSON Entities

### <a id="checkable-patch-set-info"> CheckablePatchSetInfo
The `CheckablePatchSetInfo` entity describes a patch set for which
checks are pending.

| Field Name      | Description |
| --------------- | ----------- |
| `repository`    | The repository name that this pending check applies to.
| `change_number` | The change number that this pending check applies to.
| `patch_set_id`  | The ID of the patch set that this pending check applies to.

### <a id="pending-check-info"> PendingCheckInfo
The `PendingCheckInfo` entity describes a pending check.

| Field Name | Description |
| ---------- | ----------- |
| `state`    | The [state](./rest-api-checks.md#check-state) of the pending check

### <a id="pending-checks-info"> PendingChecksInfo
The `PendingChecksInfo` entity describes the pending checks on patch set.

| Field Name       | Description |
| ---------------- | ----------- |
| `patch_set`      | The patch set for checks are pending as [CheckablePatchSetInfo](#checkable-patch-set-info) entity.
| `pending_checks` | The checks that are pending for the patch set as [checker UUID](./rest-api-checkers.md#checker-id) to [PendingCheckInfo](#pending-check-info) entity.

## <a id="query-operators"> Query Operators

The following query operators are supported in the input
[query](#query-param) for the
[Query Pending Checks](#query-pending-checks) REST endpoint.

* <a id="checker-operator"></a> `checker:'CHECKER_UUID'`:
  Matches checks of the checker with the UUID 'CHECKER_UUID'.
* <a id="is-operator"></a> `is:'STATE'`:
  Matches checks with the state 'STATE'.
* <a id="state-operator"></a> `state:'STATE'`:
  Matches checks with the state 'STATE'.
