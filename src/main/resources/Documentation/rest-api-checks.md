# /changes/<id>/revisions/<id>/checks/ REST API

This page describes the check-related REST endpoints that are added by the
@PLUGIN@ plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

## <a id="check-endpoints"> Check Endpoints

### <a id="list-checks"> List Checks
_'GET /changes/[\{change-id\}](../../../Documentation/rest-api-changes.html#change-id)/revisions/[\{revision-id\}](../../../Documentation/rest-api-changes.html#revision-id)/revisions/checks'_

Retrieves all checks for a given revision and change.

#### Request

```
  GET /changes/1/revisions/1/checks HTTP/1.0
```

As response a list of [CheckInfo](#check-info) entities is returned.
Returns checks for checkers regardless of their state (also for `DISABLED`
checkers).

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8
  )]}'
  [
    {
      "project": "test-project",
      "change_number": 1,
      "patch_set_id": 1,
      "checker_uuid": "test:my-checker",
      "state": "NOT_STARTED",
      "url": "https://foo.corp.com/test-checker/results/123",
      "created": "2019-01-31 09:59:32.126000000"
      "updated": "2019-01-31 09:59:32.126000000"
    },
    {
      "project": "test-project",
      "change_number": 1,
      "patch_set_id": 1,
      "checker_uuid": "foo:foo-checker",
      "state": "FINISHED",
      "created": "2019-01-31 09:59:32.126000000"
      "updated": "2019-01-31 09:59:32.126000000"
    }
   ]
```

### <a id="get-check"> Get Check
_'GET /changes/[\{change-id\}](../../../Documentation/rest-api-changes.html#change-id)/revisions/[\{revision-id\}](../../../Documentation/rest-api-changes.html#revision-id)/revisions/checks/[\{checker-id\}](./rest-api-checkers.md#checker-id)'_

Retrieves a check.

Returns a check regardless of the state that the checker is in (also for
`DISABLED` checkers).

#### Request

```
  GET /changes/1/revisions/1/checks/test:my-checker HTTP/1.0
```

As response a [CheckInfo](#check-info) entity is returned that describes the
check.

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8
  )]}'
  {
    "project": "test-project",
    "change_number": 1,
    "patch_set_id": 1,
    "checker_uuid": "test:my-checker",
    "state": "NOT_STARTED",
    "url": "https://foo.corp.com/test-checker/results/123",
    "created": "2019-01-31 09:59:32.126000000"
    "updated": "2019-01-31 09:59:32.126000000"
  }
```

### <a id="create-check"> Create Check
_'POST /changes/1/revisions/1/checks/'_

Creates a new check.

In the request body the data for the checker must be provided as a
[CheckInput](#check-input) entity.

Note that only users with the [Administrate
Checkers](./rest-api-checkers.md#access-control.md#capability_administrateCheckers)
global capability are permitted to create check.

#### Request

```
  POST /changes/1/revisions/1/checks/ HTTP/1.0
  Content-Type: application/json; charset=UTF-8
  {
    "checker_uuid": "test:my-checker",
    "state": "RUNNING",
    "url": "https://foo.corp.com/test-checker/results/123",
    "started": "2019-01-31 09:59:32.126000000",
  }
```

As response the [CheckInfo](#check-info) entity is returned that describes
the created check.

#### Response

```
  HTTP/1.1 201 CREATED
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8
  )]}'
  {
    "project": "test-project",
    "change_number": 1,
    "patch_set_id": 1,
    "checker_uuid": "test:my-checker",
    "state": "RUNNING",
    "url": "https://foo.corp.com/test-checker/results/123",
    "started": "2019-01-31 09:59:32.126000000",
    "created": "2019-01-31 09:59:32.126000000"
    "updated": "2019-01-31 09:59:32.126000000"
  }
```

### <a id="update-check"> Update Check
_'POST /changes/1/revisions/1/checks/'_
_'POST /changes/1/revisions/1/checks/test:my-checker'_

Updates a check. The semantics are the same as for [CreateCheck](#create-check).

This REST endpoint supports partial updates of the checker property set. Only
properties that are set in the [CheckInput](#check-input) entity are updated.
Properties that are not set in the input (or that have `null` as value) are not
touched.

If the [checker-id](./rest-api-checkers.md#checker-id) is provided as part of
the URL, it must either match the value provided in the request body via
[CheckInput](#check-input) or the value in the request body is omitted.

### <a id="json-entities"> JSON Entities

### <a id="check-info"> CheckInfo
The `CheckInfo` entity describes a check.

| Field Name        |          | Description |
| ----------------- | -------- | ----------- |
| `project`         |          | The project name that this check applies to.
| `change_number`   |          | The change number that this check applies to.
| `patch_set_id`    |          | The path set that this check applies to.
| `checker_uuid`    |          | The [UUID](./rest-api-checkers.md#checker-id) of the checker that reported this check.
| `state`           |          | The state as string-serialized form of [CheckState](#check-state)
| `url`             | optional | A fully-qualified URL pointing to the result of the check on the checker's infrastructure.
| `started`         | optional | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check started processing.
| `finished`        | optional | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check finished processing.
| `created`         |          | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check was created.
| `updated`         |          | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check was last updated.

### <a id="check-input"> CheckInput
The `CheckInput` entity contains information for creating or updating a check.

| Field Name      |          | Description |
| --------------- | -------- | ----------- |
| `checker_uuid`  | optional | The name of the checker. Must be specified for checker creation. Optional only if updating a check and referencing the checker using the [UUID](./rest-api-checkers.md#checker-id) in the URL.
| `state`         | optional | The state as string-serialized form of [CheckState](#check-state)
| `url`           | optional | A fully-qualified URL pointing to the result of the check on the checker's infrastructure.
| `started`       | optional | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check started processing.
| `finished`      | optional | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check finished processing.

### <a id="check-state"> CheckState (enum)
The `CheckState` enum can have the following values: `NOT_STARTED`, `FAILED`,
`SCHEDULED`, `RUNNING`, `SUCCESSFUL` and `NOT_RELEVANT`.
