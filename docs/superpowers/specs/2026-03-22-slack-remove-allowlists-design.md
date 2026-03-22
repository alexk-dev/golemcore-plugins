# Slack Remove Allowlists Design

## Goal

Remove `allowedUserIds` and `allowedChannelIds` from the Slack plugin's public configuration and runtime behavior so Slack message intake depends only on Slack event type, bot installation, and existing thread/session rules, and ship that change as Slack plugin version `1.0.4`.

## Scope

- Remove both allowlist fields from the Slack config model.
- Remove both fields from the Slack settings UI and save flow.
- Remove inbound user/channel filtering from the Slack adapter.
- Continue tolerating legacy persisted config keys silently.
- Publish the change as patch release `1.0.4` with refreshed registry metadata and checksum.

## Non-Goals

- No storage migration job.
- No change to mention-only behavior for top-level channel messages.
- No change to thread/session routing.

## Design

The plugin will keep `@JsonIgnoreProperties(ignoreUnknown = true)` on the config model, which lets older saved configs still deserialize cleanly even after the Java fields are removed. The settings contributor will stop exposing and saving the legacy keys, so any subsequent save naturally rewrites the stored config without them.

Inbound Slack handling will no longer call allowlist-based user/channel filters. The adapter will continue to enforce the existing event-shape rules:

- DMs are accepted.
- `app_mention` events are accepted.
- thread replies are accepted only when they belong to an active conversation.

## Tests

- Update config-service tests to verify legacy allowlist keys are ignored on read and are not persisted on save.
- Update settings-contributor tests to verify the section no longer exposes allowlist fields and save keeps only supported values.
- Update adapter tests to verify inbound messages are not dropped because of prior allowlist configuration.
- Verify the release metadata points to `1.0.4` and matches the locally built artifact checksum.
