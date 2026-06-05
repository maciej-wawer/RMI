# WikiRMI — Expansion Design (delta)

**Date:** 2026-06-05
**Status:** Approved — additive enhancement of the existing WikiRMI app.
**Principle:** All pure Java/Swing, no new dependencies. Reuses the existing per-page write-lock
discipline, so the deadlock-freedom argument and the 12 existing tests are unaffected.

## A. Live presence (online users + active edits)
- **RMI:** `List<String> listOnlineUsers(token)`; new callback `onPresenceChanged()` fired on login/logout.
- **Server:** `SessionManager.onlineUsernames()`; `NotificationService.presenceChanged()`.
- **Client:** `PresencePanel` (pure view: `setOnline(list)`, `setActiveEdits(list)`) docked right.
  Active edits derived from `listPages` `lockedBy`. `EditDialog` shows a live lease countdown.

## B. Compare + restore revisions
- **RMI:** `PageDTO restoreRevision(token, title, revIndex)` — under the page write lock, rejects if
  locked by another user, then writes the old revision's content as a NEW revision (audit-preserving)
  and bumps the version.
- **Client:** `TextDiff` (LCS line diff). `HistoryDialog`: multi-select 2 revisions → highlighted diff;
  "Przywróć tę wersję" button (1 selected).

## C. Markdown preview + `[[links]]`
- **Client-only.** `MarkdownRenderer.toHtml`: subset (`#`/`##`/`###`, `**bold**`, `*italic*`, `- ` lists,
  paragraphs) + `[[Tytuł]]` → `<a href="wiki:...">`. Main viewer becomes a `JEditorPane` (HTML);
  `[[links]]` clickable → navigate. Editing stays raw text.

## D. UI polish + admin
- Menu bar (Plik/Edycja/Konto/Administracja/Pomoc), toolbar, status bar (połączenie, user, online count).
- Shortcuts: Ctrl+E, Ctrl+H, F5/Ctrl+R, Ctrl+F.
- **RMI:** `changePassword(token, old, new)`; admin `forceUnlock(token, title)`.
- **Client:** `ChangePasswordDialog`; force-unlock action (admin).

## Server-side summary
`WikiService` +4 methods, `WikiClientCallback` +1; `WikiStore` += `restoreRevision`, `updatePassword`,
`forceUnlock`; `SessionManager` += `onlineUsernames`; `NotificationService` += `presenceChanged`.

## New client files
`PresencePanel`, `MarkdownRenderer`, `TextDiff`, `ChangePasswordDialog`. `MainFrame` becomes the
orchestrator (menu/toolbar/3-pane/status/shortcuts/HTML view). `HistoryDialog` and `EditDialog` extended.

## Tests (target 18/18 + integration)
`RestoreRevisionTest`, `ChangePasswordTest`, `ForceUnlockTest`, `MarkdownRendererTest`, `TextDiffTest`,
online-users check; re-run the 2-client RMI integration (callback list now includes `onPresenceChanged`).
