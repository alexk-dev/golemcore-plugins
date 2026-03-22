# Slack Remove Allowlists Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Slack user/channel allowlists from config, UI, and runtime filtering while keeping legacy stored keys harmless.

**Architecture:** Keep the persisted-config compatibility boundary in `SlackPluginConfig` via unknown-field tolerance, simplify the settings surface, and remove runtime allowlist checks from `SlackAdapter`. The change is intentionally narrow and does not alter mention or thread routing semantics.

**Tech Stack:** Java 25, Spring, JUnit 5, Mockito, Maven

---

### Task 1: Update Tests For Removed Allowlists

**Files:**
- Modify: `golemcore/slack/src/test/java/me/golemcore/plugins/golemcore/slack/SlackPluginConfigServiceTest.java`
- Modify: `golemcore/slack/src/test/java/me/golemcore/plugins/golemcore/slack/SlackPluginSettingsContributorTest.java`
- Modify: `golemcore/slack/src/test/java/me/golemcore/plugins/golemcore/slack/adapter/inbound/slack/SlackAdapterTest.java`

- [ ] **Step 1: Write failing assertions for config-service behavior**

Remove expectations that `allowedUserIds` and `allowedChannelIds` exist in the runtime config or saved payload, while keeping the legacy input keys in the mocked stored config.

- [ ] **Step 2: Run the targeted config-service test to verify it fails**

Run: `mvn -B -ntp -f pom.xml -pl :golemcore-slack-plugin -Dtest=SlackPluginConfigServiceTest test`

- [ ] **Step 3: Write failing assertions for settings section exposure/save**

Update the settings contributor tests so the section no longer exposes the allowlist values and save payloads no longer accept or persist them.

- [ ] **Step 4: Run the targeted settings-contributor test to verify it fails**

Run: `mvn -B -ntp -f pom.xml -pl :golemcore-slack-plugin -Dtest=SlackPluginSettingsContributorTest test`

- [ ] **Step 5: Write failing assertions for inbound message handling**

Update the adapter tests so prior allowlist-style config does not cause inbound messages to be ignored.

- [ ] **Step 6: Run the targeted adapter test to verify it fails**

Run: `mvn -B -ntp -f pom.xml -pl :golemcore-slack-plugin -Dtest=SlackAdapterTest test`

### Task 2: Remove Allowlists From Slack Runtime And Settings

**Files:**
- Modify: `golemcore/slack/src/main/java/me/golemcore/plugins/golemcore/slack/SlackPluginConfig.java`
- Modify: `golemcore/slack/src/main/java/me/golemcore/plugins/golemcore/slack/SlackPluginSettingsContributor.java`
- Modify: `golemcore/slack/src/main/java/me/golemcore/plugins/golemcore/slack/adapter/inbound/slack/SlackAdapter.java`

- [ ] **Step 1: Remove allowlist fields and normalization from the config model**

Delete `allowedUserIds` and `allowedChannelIds` from `SlackPluginConfig` and keep normalization focused on supported fields.

- [ ] **Step 2: Remove allowlist fields from the settings section and save flow**

Delete the UI fields, values, and save-path parsing for the two legacy keys.

- [ ] **Step 3: Remove runtime user/channel allowlist checks**

Delete the inbound/action filter methods in `SlackAdapter` and keep only the thread/session gating logic.

- [ ] **Step 4: Run the targeted Slack test class to verify the implementation passes**

Run: `mvn -B -ntp -f pom.xml -pl :golemcore-slack-plugin -Dtest=SlackPluginConfigServiceTest,SlackPluginSettingsContributorTest,SlackAdapterTest test`

### Task 3: Verify And Prepare PR

**Files:**
- Verify only

- [ ] **Step 1: Run the Slack module package build**

Run: `mvn -B -ntp -f pom.xml -pl :golemcore-slack-plugin -am package`

- [ ] **Step 2: Run repository validation**

Run: `python3 scripts/plugins_repo.py validate`

- [ ] **Step 3: Commit the change**

Use: `git add ... && git commit -m "fix(slack): remove legacy allowlists"`

- [ ] **Step 4: Push branch and open PR**

Use: `git push -u origin fix/slack-remove-allowlists` then create the PR against `main`.
