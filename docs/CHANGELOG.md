# Changelog — Odyssey Library

Format: [Keep a Changelog](https://keepachangelog.com). Versions follow SemVer.
Each release section must exist before merging to `main` (release notes are taken from here).

## [Unreleased]

## [1.0.0-alpha.3] - 2026-11-09
### Added
- Chest UI framework: anti-dupe menus, anvil text/number input, paged lists, confirm dialog, edit locks,
  suspended screens (items are never lost while navigating), remote rendering for Web Controls
- Auto-generated settings editor for any config
- `/odyssey` hub: paged module icons, admin tools (modules, settings, audit, performance)

## [1.0.0-alpha.2] - 2026-11-09
### Added
- Scheduler: tick tasks, async IO pool, main-thread hand-off, failure isolation
- Crash-safe JSON data store (atomic writes, 3 rolling backups, quarantine of unreadable files)
- Live typed config (`config/odyssey/*.json`) with validation, file watcher, change events and audit
- Messages and per-module lang files; admin notifications (offline queue); audit log; performance guard
- Permissions `odyssey.<module>.<action>` via Forge PermissionAPI with vanilla OP fallback
- Module API (`OdysseyModule`, `ModuleContext`, live enable/disable) and the `/odyssey` command root
- Configurable aliases (`aliases.json`), event regions for cross-mod event awareness

## [1.0.0-alpha.1] - 2026-11-09
### Added
- Project scaffold: Gradle 8.8, ForgeGradle 6, Forge 47.4.28, official mappings, Java 17
- Server-only `mods.toml` (`displayTest = IGNORE_ALL_VERSION`), logo, JitPack publishing, CI workflows
