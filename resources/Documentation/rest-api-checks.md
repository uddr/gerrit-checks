# /changes/`id`/revisions/`id`/checks/ REST API

This page describes the check-related REST endpoints that are added by the
@PLUGIN@ plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

## <a id="check-endpoints"> Check Endpoints

### <a id="list-checks"> List Checks
_'GET /changes/[\{change-id\}](../../../Documentation/rest-api-changes.html#change-id)/revisions/[\{revision-id\}](../../../Documentation/rest-api-changes.html#revision-id)/checks'_

Retrieves all checks for a given revision and change.

Additional fields can be obtained by adding [`o` parameters](#query-options).

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
      "repository": "test-repo",
      "change_number": 1,
      "patch_set_id": 1,
      "checker_uuid": "test:my-checker",
      "state": "NOT_STARTED",
      "url": "https://foo.corp.com/test-checker/results/123",
      "created": "2019-01-31 09:59:32.126000000"
      "updated": "2019-01-31 09:59:32.126000000"
    },
    {
      "repository": "test-repo",
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
_'GET /changes/[\{change-id\}](../../../Documentation/rest-api-changes.html#change-id)/revisions/[\{revision-id\}](../../../Documentation/rest-api-changes.html#revision-id)/checks/[\{checker-id\}](./rest-api-checkers.md#checker-id)'_

Retrieves a check.

Returns a check regardless of the state that the checker is in (also for
`DISABLED` checkers).

Additional fields can be obtained by adding [`o` parameters](#query-options).

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
    "repository": "test-repo",
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
    "repository": "test-repo",
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

### <a id="rerun-check"> Rerun Check

_'POST /changes/1/revisions/1/checks/test:my-checker/rerun'_

Reruns a check. As response the [CheckInfo](#check-info) entity is returned that
describes the created check.

Notification options may be specified as [RerunInput](#rerun-input) entity in
the request body.

This REST endpoint supports rerunning a check. It also resets all relevant check
fields such as `message`, `url`, `started` and `finished`.

## <a id="json-entities"> JSON Entities

### <a id="check-info"> CheckInfo
The `CheckInfo` entity describes a check.

| Field Name            |          | Description |
| --------------------- | -------- | ----------- |
| `repository`          |          | The repository name that this check applies to.
| `change_number`       |          | The change number that this check applies to.
| `patch_set_id`        |          | The patch set that this check applies to.
| `checker_uuid`        |          | The [UUID](./rest-api-checkers.md#checker-id) of the checker that reported this check.
| `state`               |          | The state as string-serialized form of [CheckState](#check-state)
| `message`             | optional | Short message explaining the check state. Size limit is 10k by default, configured via plugin.checks.messageSizeLimit.
| `url`                 | optional | A fully-qualified URL pointing to the result of the check on the checker's infrastructure.
| `started`             | optional | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check started processing.
| `finished`            | optional | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check finished processing.
| `created`             |          | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check was created.
| `updated`             |          | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check was last updated.
| `checker_name`        | optional | The name of the checker that produced this check.<br />Only set if [checker details](#option-checker) are requested.
| `checker_status`      | optional | The [status](rest-api-checkers.md#checker-info) of the checker that produced this check.<br />Only set if [checker details](#option-checker) are requested.
| `blocking`            | optional | Set of [blocking conditions](rest-api-checkers.md#blocking-conditions) that apply to this checker.<br />Only set if [checker details](#option-checker) are requested.
| `checker_description` | optional | The description of the checker that reported this check.

### <a id="check-input"> CheckInput
The `CheckInput` entity contains information for creating or updating a check.

| Field Name      |          | Description |
| --------------- | -------- | ----------- |
| `checker_uuid`  | optional | The [UUID](#checker-id) of the checker. Must be specified for check creation. Optional only if updating a check and referencing the checker using the [UUID](./rest-api-checkers.md#checker-id) in the URL.
| `state`         | optional | The state as string-serialized form of [CheckState](#check-state)
| `message`       | optional | Short message explaining the check state.
| `url`           | optional | A fully-qualified URL pointing to the result of the check on the checker's infrastructure.
| `started`       | optional | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check started processing.
| `finished`      | optional | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the check finished processing.
| `notify`        | optional | Notify handling that defines to whom email notifications should be sent when the combined check state changes due to posting this check. Allowed values are `NONE`, `OWNER`, `OWNER_REVIEWERS` and `ALL`. If not set, the default is `ALL` if the combined check state is updated to either `SUCCESSFUL` or `NOT_RELEVANT`, otherwise the default is `OWNER`. Regardless of this setting there are no email notifications for posting checks on non-current patch sets.
| `notify_details`| optional | Additional information about whom to notify when the combined check state changes due to posting this check as a map of recipient type to [NotifyInfo](../../../Documentation/rest-api-changes.html#notify-info) entity. Regardless of this setting there are no email notifications for posting checks on non-current patch sets.

### <a id="rerun-input"> RerunInput
The `RerunInput` entity contains information for rerunning a check.

| Field Name      |          | Description |
| --------------- | -------- | ----------- |
| `notify`        | optional | Notify handling that defines to whom email notifications should be sent when the combined check state changes due to rerunning this check. Allowed values are `NONE`, `OWNER`, `OWNER_REVIEWERS` and `ALL`. If not set, the default is `OWNER`. Regardless of this setting there are no email notifications for rerunning checks on non-current patch sets.
| `notify_details`| optional | Additional information about whom to notify when the combined check state changes due to rerunning this check as a map of recipient type to [NotifyInfo](../../../Documentation/rest-api-changes.html#notify-info) entity. Regardless of this setting there are no email notifications for rerunning checks on non-current patch sets.

### <a id="check-state"> CheckState (enum)
The `CheckState` enum can have the following values: `NOT_STARTED`, `FAILED`,
`SCHEDULED`, `RUNNING`, `SUCCESSFUL` and `NOT_RELEVANT`.

## <a id="query-options"> Options

The following query options are supported in the `o` field of certain requests:

* <a id="option-checker"></a> `CHECKER`: Include details from the configuration of
  the checker that produced this check.
