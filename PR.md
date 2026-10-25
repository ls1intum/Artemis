### Summary

The Participations page used a single-selection filter dropdown that could close unexpectedly while scrolling, and its controls mixed several UI libraries. This PR replaces it with grouped filters that support multiple selections and migrates the table and page controls to TUM AET UI. Single-option filters use checkboxes, selected dropdown options appear as removable chips, and Reset Filter clears all selections.

### Checklist

#### General

- [x] Kept the changes focused on participation filtering and the associated UI controls.
- [x] Added English and German translations for the filter categories and shorter search placeholders.
- [x] Resolved the merge conflicts with `develop`.

#### Server

- [x] Combined filter selections using alternatives within each category and intersections across categories.
- [x] Retained server-side pagination, search, sorting, and score-range filtering.
- [x] Added integration coverage for combined filter categories.
- [x] Introduced no database schema changes or migrations.

#### Client

- [x] Migrated the participation table and associated buttons, popovers, tooltips, and inputs to TUM AET UI.
- [x] Added regression coverage for multiple selections, removable chips, checkboxes, Reset Filter, scrolling, and keyboard interaction.
- [x] Verified the participation table's server request handling through regression tests.
- [x] Passed the focused client tests, strict test type checks, client build, and targeted lint checks.
- [x] Passed the client and server dead-code checks.

### Motivation and Context

Users need to combine compatible participation filters without repeatedly reopening a dropdown. The previous filter supported only one selection, and scrolling could dismiss it unexpectedly. Separate filter categories make the available choices easier to scan, while checkboxes simplify categories with a single option.

### Description

#### Client

- Replace the single filter dropdown with separate dropdowns for submissions, results, and assessment type, where applicable.
- Use checkboxes for categories with one option, including participation mode, locked assessments, and submissions for non-programming exercises.
- Show selected dropdown options as removable chips and provide a Reset Filter action.
- Keep dropdowns open during multiple selections, scrolling, and input refreshes; retain dismissal through outside clicks and Escape.
- Narrow the search field and shorten its English and German placeholder text.
- Replace the participation table and related page controls with TUM AET UI components while preserving their permissions, confirmation flows, and actions.
- Refine toolbar spacing, equal-width submission counters, and centered failed-submission text.

#### Server

- Accept comma-separated filter selections in the existing participation search request.
- Combine selections with **OR within a category** and **AND across categories**. For example, selecting Successful and Unsuccessful includes either result status; adding Manual restricts those rows to manual assessments.
- Preserve existing score-range filtering and participation retrieval behavior without database changes.

### Steps for Testing

1. Open **Participations** for a programming exercise and a non-programming exercise. Combine dropdown selections and checkboxes, remove chips, and use Reset Filter. Confirm the displayed rows match the selections.
2. Keep a dropdown open while selecting multiple options and scrolling. Check keyboard interaction, outside-click dismissal, and Escape.
3. Check that existing search, sorting, pagination, score-range filtering, assessment actions, participation management, and exports still behave correctly for instructors and tutors.
4. Check the layout in English and German, light and dark themes, and at a narrow viewport.

### Validation

- **114 focused tests passed:** 92 participation tests, 5 filter-toolbar tests, and 17 TUM AET UI table tests.
- Strict client test type checks passed.
- Development client build and targeted ESLint checks passed.
- Client and server dead-code checks passed.

### Screenshots

Attach current screenshots of the redesigned filter toolbar and participation controls when opening the PR.
