# Relocate the sidebar minimize button into the sidebar header

**Date:** 2026-06-30
**Status:** Approved design, pending implementation plan

## Problem

In a course, each tab with a secondary sidebar (the list of exercises / lectures / exams /
conversations / tutorial groups) has a minimize ("collapse") toggle. On the **Exercises** tab this
toggle already lives inside the sidebar header, next to the page title. On every other tab the toggle
instead lives in the top course title bar. The user wants the toggle to live in the sidebar header —
"where it belongs" — consistently across **Lectures, Exams, Tutorials, Communication, and Iris**.

## Goal

Move the collapse toggle out of the top course title bar and into the secondary sidebar's header for
the five listed tabs, matching the Exercises tab.

Chosen behavior (**"title only in sidebar"**):

- **Sidebar expanded:** the page title and the toggle render inside the sidebar header. The top
  course title bar shows only its action buttons (refresh, notification bell, settings) — no page
  title, no toggle. No duplicated title.
- **Sidebar collapsed:** the sidebar (and its header) is hidden, so the top course title bar shows
  the toggle and the page title again, allowing the user to re-expand.

This mirrors the Exercises tab's spirit. (Exercises goes further and removes the title bar entirely,
but it has no refresh/notification actions to preserve; the other tabs do, so their title bar stays.)

## Current architecture (relevant pieces)

- **Toggle button:** `jhi-course-sidebar-toggle-button`
  (`app/course/shared/course-sidebar-toggle-button/`). Inputs `isCollapsed`, `isCommunicationModule`;
  output `toggleSidebar`.
- **Secondary sidebar:** `jhi-sidebar` (`app/course/sidebar/sidebar.component.*`). Already supports a
  header: inputs `pageTitle`, `showSidebarToggle`, `isSidebarCollapsed`; output `toggleSidebar`. It
  renders the toggle in the header only when `showSidebarToggle() && !isSidebarCollapsed()`.
- **Top title bar:** `jhi-course-title-bar` (`app/course/shared/course-title-bar/`). Renders the
  toggle when `hasSidebar()`, the page title (or a custom title template), and `<ng-content>` /
  actions. Shared by both `course-overview` (student) and `course-management-container`.
- **Container:** `course-overview.component` holds `pageTitle`, `isSidebarCollapsed`, `hasSidebar`,
  and a reference to the activated child tab component (`activatedComponentReference`). It hides the
  title bar entirely for Exercises via `showCourseTitleBar = computed(() => !(active instanceof
  CourseExercisesComponent))`, and passes the page title to Exercises via `setPageTitle(...)`.
- **Tab components:** `CourseLecturesComponent`, `CourseTutorialGroupsComponent`,
  `CourseExamsComponent`, `CourseConversationsComponent` each already have an `isCollapsed` signal and
  a `toggleSidebar()` method, and each renders `<jhi-sidebar>` without the header inputs.
- **Iris:** `CourseIrisComponent` renders `jhi-course-chatbot` → `jhi-iris-base-chatbot`. It has no
  `jhi-sidebar`. Its "sidebar" is the chat-history panel inside `iris-base-chatbot`, gated by the
  `isChatHistoryAvailable` input — passed `true` **only** by `course-chatbot.component.html` (the
  course Iris page). `CourseIrisComponent.toggleSidebar()` calls `toggleChatHistory()` and flips a
  plain `isCollapsed` boolean.
- **Route data:** tabs with `hasSidebar: true` are Exercises, Lectures, Communication, Tutorials,
  Exams, Iris, and Dashboard. Dashboard is explicitly out of scope.

## Design

### 1. Shared title bar — `course-title-bar.component`

Add an input `titleInSidebar = input(false)`.

- Toggle button: render when `hasSidebar() && (!titleInSidebar() || isSidebarCollapsed())`.
- Default page title (the `@else` `jhi-course-title-bar-title` branch): render when
  `!titleInSidebar() || isSidebarCollapsed()`.
- The custom title template branch (`customTitleTemplate()`) and the actions/`<ng-content>` are
  **unchanged** — they always render as today.

With the default `titleInSidebar = false`, behavior is identical to today for Dashboard,
course-management, and every page not in scope.

### 2. Container — `course-overview.component`

- Add `titleInSidebar = computed(() => { const c = activatedComponentReference(); return c
  instanceof CourseLecturesComponent || c instanceof CourseTutorialGroupsComponent || c instanceof
  CourseExamsComponent || c instanceof CourseConversationsComponent || c instanceof
  CourseIrisComponent; })` and bind `[titleInSidebar]="titleInSidebar()"` on `jhi-course-title-bar`.
- Extend the existing `handleComponentActivation` `setPageTitle(...)` call (currently Exercises-only)
  to also push `pageTitle()` into the five tab components, so each can render the title in its sidebar
  header.
- **Collapse-state sync (key risk).** Today the title bar's `isSidebarCollapsed` stays correct only
  because every toggle routes through `course-overview` (`handleToggleSidebar` reads
  `child.isCollapsed` after toggling). Once the toggle lives inside the child's sidebar, that path is
  bypassed and the title bar would not learn that the sidebar collapsed — leaving the user collapsed
  with no visible re-expand control. Fix: derive the collapsed state the title bar binds to reactively
  from the active child's `isCollapsed` signal, e.g. a `computed` that reads
  `activatedComponentReference()?.isCollapsed` (Angular signal tracking works through the child's
  signal getter, so changes from any control — in-sidebar toggle, title-bar toggle, keyboard
  shortcut, communication's open/close bus — propagate). The exact wiring (whether to retire the
  inherited `isSidebarCollapsed` signal for this binding, and how it coexists with the base class's
  `openSidebar$`/`closeSidebar$` handlers and `handleToggleSidebar`) is finalized in the
  implementation plan. The base `BaseCourseContainerComponent` and `course-management-container` must
  remain behaviorally unchanged.

### 3. The four list-sidebar tabs

For `CourseLecturesComponent`, `CourseTutorialGroupsComponent`, `CourseExamsComponent`,
`CourseConversationsComponent`:

- Add a `pageTitle` signal and a `setPageTitle(title: string)` method (mirroring
  `CourseExercisesComponent`).
- In each template, pass to `<jhi-sidebar>`:
  `[pageTitle]="pageTitle()"`, `[showSidebarToggle]="true"`, `[isSidebarCollapsed]="isCollapsed()"`,
  `(toggleSidebar)="toggleSidebar()"`.

No change needed to the existing `isCollapsed` / `toggleSidebar()` logic or the per-tab collapse
persistence in `CourseOverviewService`.

### 4. Iris — `iris-base-chatbot.component`

- Add a `jhi-course-sidebar-toggle-button` into the open-state chat-history header
  (`chat-history-top`), wired to collapse the panel (`setChatHistoryVisibility(false)`). It renders
  only when `isChatHistoryAvailable()`, i.e. only on the course Iris page; lecture/exercise-widget/
  tutor-suggestion chatbots (which pass `isChatHistoryAvailable=false`) are untouched.
- The panel's collapsed state must propagate up to `CourseIrisComponent` / `course-overview` so the
  title bar stays in sync (same requirement as §2). Implementation plan decides whether
  `CourseIrisComponent.isCollapsed` should derive from the chatbot's `isChatHistoryOpen` signal, or
  whether the in-panel button emits an output that flows up through `course-chatbot`.

## Testing

- **Vitest — title bar:** with `titleInSidebar=true`, assert the toggle and default title are hidden
  when expanded and shown when collapsed; with `titleInSidebar=false`, behavior unchanged; custom
  title template and actions always render.
- **Vitest — each of the four tabs:** assert `<jhi-sidebar>` receives `showSidebarToggle=true` and the
  page title, and that a collapse → expand round-trip keeps the container's collapsed state in sync.
- **Vitest — Iris:** assert the toggle renders in the chat-history header only when
  `isChatHistoryAvailable`, and collapsing the panel propagates to the container.
- **Manual:** all five tabs — expand/collapse, confirm the title bar shows the toggle/title only when
  collapsed, no duplicate titles, and re-expand works. Confirm Dashboard, management, and non-sidebar
  tabs are visually unchanged.

## Out of scope

- Exercises (already in the target state).
- Dashboard, course-management, and all non-sidebar tabs — no behavior change.
- Restyling the toggle button or the sidebar header beyond what is needed to host the button.
