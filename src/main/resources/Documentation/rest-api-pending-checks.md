# @PLUGIN@ - /checks.pending/ REST API

This page describes the pending-checks-related REST endpoints that are
added by the @PLUGIN@ plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

## <a id="pending-checks-endpoints"> Pending Checks Endpoints

### <a id="get-checker"> List Pending Checks
_'GET /checks.pending/'_

Lists pending checks for a checker or for all checkers of a scheme.

Checks are pending if they are in a non-final state and the external
checker system intends to post further updates on them.

By default this REST endpoint only returns checks that are in state
`NOT_STARTED` but callers may specify the states that they are
interested in (see [state](#state-param) request parameter).

Request parameters:

* <a id="checker-param"> `checker`: the UUID of the checker for which
  pending checks should be listed (optional, if not specified `scheme`
  must be set)
* <a id="scheme-param"> `scheme`: the scheme of the checkers for which
  pending checks should be listed (optional, if not specified `checker`
  must be set)
* <a id="state-param"> `state`: state that should be considered as
  pending (optional, by default the state `NOT_STARTED` is assumed,
  this option may be specified multiple times to request checks
  matching any of several states)

Note that only users with the [Administrate
Checkers](access-control.md#capability_administrateCheckers) global capability
are permitted to list pending checks.

#### Request by checker

```
  GET /checks.pending/?checker=test:my-checker&state=NOT_STARTED&state=SCHEDULED HTTP/1.0
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
        "project": "test-project",
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
        "project": "test-project",
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

#### Request by checker scheme

```
  GET /checks.pending/?scheme=test&state=NOT_STARTED&state=SCHEDULED HTTP/1.0
```

As response a list of [PendingChecksInfo](#pending-checks-info)
entities is returned that describes the pending checks.

#### Response by checker scheme

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8
  )]}'
  [
    {
      "patch_set": {
        "project": "test-project",
        "change_number": 1,
        "patch_set_id": 1,
      }
      "pending_checks": {
        "test:my-checker": {
          "state": "NOT_STARTED",
        },
        "test:my-other-checker": {
          "state": "SCHEDULED",
        }
      }
    },
    {
      "patch_set": {
        "project": "test-project",
        "change_number": 5,
        "patch_set_id": 2,
      }
      "pending_checks": {
        "test:my-checker": {
          "state": "NOT_STARTED",
        },
        "test:my-other-checker": {
          "state": "NOT_STARTED",
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
| `project`       | The project name that this pending check applies to.
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

