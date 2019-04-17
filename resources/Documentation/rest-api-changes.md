# /changes REST API

This page describes additions to the Gerrit change-related REST endpoints that
are added by the @PLUGIN@ plugin.

Please also take note of the general information on the
[changes REST API](../../../Documentation/rest-api-changes.html).

### <a id="get-change"> Get Change options
_'GET /changes/[\{change-id\}](../../../Documentation/rest-api-changes.html#change-id)?@PLUGIN@--combined'_

Adding the query parameter `@PLUGIN@--combined` adds a `CheckChangeInfo` entity
to the `plugins` list in
[`ChangeInfo`](../../../Documentation/rest-api-changes.html#change-info). All
fields reflect up-to-date information read from primary storage, not a secondary
index.

### <a id="check-change-info"> CheckChangeInfo

The `CheckChangeInfo` describes check information on a change.

| Field Name             | Description |
| ---------------------- | ----------- |
| `combined_check_state` | The [combined state](#combined-check-state) of all checks on the change.

### <a id="combined-check-state"> CombinedCheckState (enum)

The `CheckState` enum can have the following values:

* `FAILED`: At least one required check failed; other checks may have passed, or
  still be running.

* `WARNING`: All relevant checks terminated, and at least one optional check
  failed, but no required checks failed.

* `IN_PROGRESS`: At least one relevant check is in a non-terminated
  [state](rest-api-checks.md#check-state) (`NOT_STARTED`, `SCHEDULED`,
  `RUNNING`), and no required checks failed. Some optional checks may have
  failed.

* `SUCCESSFUL`: All relevant checks terminated successfully.

* `NOT_RELEVANT:` No checks are relevant to this change.
