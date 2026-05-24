# Design Spec: Relocate Athena AI Loading Banner to Assessment Header

**Date:** 2026-05-24  
**Branch:** feature/athena/align-loading-ui-for-feedback-suggestions  
**Scope:** modeling, text, and programming assessment pages

---

## Problem

`<jhi-feedback-suggestions-banner>` currently renders as a full-width block element **below** the `<jhi-assessment-header>`, occupying an entire row and pushing all assessment content downward. This is visually heavy and inconsistent with the inline status messages already in the header (complaint notices, lock notices, etc.).

---

## Goal

Move the banner to the **right side of the assessment header row**, visually aligned with the Save/Submit/Cancel buttons, and redesign its visual style from a full-width `p-message` block to a compact inline indicator.

---

## Architecture

### Component Hierarchy (current)

```
assessment-layout
  ├── jhi-assessment-header          ← <h3> flex row with buttons
  ├── jhi-assessment-complaint-alert
  └── <ng-content>                   ← banner + assessment body go here
        ├── jhi-feedback-suggestions-banner  ← FULL WIDTH block
        └── ... assessment content
```

### Component Hierarchy (after)

```
assessment-layout
  ├── [flex row wrapper]
  │     ├── jhi-assessment-header          ← flex-grow-1
  │     └── <ng-content select="[jhiBannerSlot]">  ← banner projected here
  ├── jhi-assessment-complaint-alert
  └── <ng-content>                         ← assessment body only
        └── ... assessment content
```

---

## Changes

### 1. `assessment-layout.component.html`

Wrap `<jhi-assessment-header>` in a flex row and add a named content slot beside it:

```html
<div class="d-flex align-items-center flex-wrap gap-2">
    <jhi-assessment-header class="flex-grow-1" … />
    <ng-content select="[jhiBannerSlot]" />
</div>
<jhi-assessment-complaint-alert … />
<ng-content />
…
```

No new inputs required on `assessment-layout`.

### 2. `feedback-suggestions-banner.component.html` — visual redesign

Replace the three `<p-message>` blocks with compact inline elements:

**Loading state** (spinner + small text):
```html
@if (isLoading() && isFeedbackSuggestionsEnabled()) {
    <div class="d-flex align-items-center gap-1 text-muted" style="font-size: 65%">
        <fa-icon [icon]="faCircleNotch" animation="spin" />
        <span jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading" />
    </div>
}
```

**Auto feedback available** (p-tag, no feedback suggestions):
```html
@if (hasAutomaticFeedback() && isAssessor() && !resultCompletionDate() && !isFeedbackSuggestionsEnabled()) {
    <p-tag severity="info" styleClass="text-wrap" value="{{ 'artemisApp.assessment.feedbackSuggestions.automaticAssessmentAvailable' | artemisTranslate }}" />
}
```

**Generative AI available** (p-tag + tooltip icon):
```html
@if (hasAutomaticFeedback() && isAssessor() && !resultCompletionDate() && isFeedbackSuggestionsEnabled()) {
    <div class="d-flex align-items-center gap-1">
        <p-tag severity="info" styleClass="text-wrap" value="{{ 'artemisApp.assessment.feedbackSuggestions.generativeAIAssessmentAvailable' | artemisTranslate }}" />
        <fa-icon
            [icon]="faQuestionCircle"
            class="cursor-pointer"
            [pTooltip]="'artemisApp.assessment.feedbackSuggestions.generativeAIAssessmentInfo' | artemisTranslate"
            tooltipPosition="left"
        />
    </div>
}
```

Remove `MessageModule` from imports; add `TagModule` from `primeng/tag`.

### 3. Three exercise assessment templates

**For each of modeling, text, programming:**
- Move `<jhi-feedback-suggestions-banner>` out of the `@if (submission)` guard (banner's internal guards handle visibility)
- Add `jhiBannerSlot` attribute so Angular projects it into the named slot

**modeling-assessment-editor.component.html** — move banner before `jhi-assessment-layout`'s body content and add attribute:
```html
<jhi-assessment-layout …>
    <jhi-feedback-suggestions-banner
        jhiBannerSlot
        [isLoading]="isLoading"
        [hasAutomaticFeedback]="hasAutomaticFeedback"
        [isAssessor]="isAssessor"
        [resultCompletionDate]="result?.completionDate"
        [isFeedbackSuggestionsEnabled]="isFeedbackSuggestionsEnabled"
    />
    @if (submission) {
        <div class="editor-container flex-grow-1">…</div>
        …
    }
</jhi-assessment-layout>
```

Same pattern for `text-submission-assessment.component.html` and `code-editor-tutor-assessment-container.component.html`.

---

## Visual Outcome

| Viewport | Header row |
|----------|-----------|
| Wide (≥ lg) | `[Assessment] ──────────────────── [AI tag/spinner] [Save] [Submit] [Cancel]` |
| Narrow (< lg) | Header and banner wrap to next line, still grouped together |

The banner never pushes assessment content downward.

---

## Constraints & Notes

- No new inputs on `assessment-layout` or `assessment-header` — zero prop-drilling.
- Named content projection is native Angular — no workaround needed.
- `isLoading` in `assessment-header` is the page-load spinner; `isLoading` in the banner is the AI-fetch spinner — they are independent.
- The `@if (submission)` guard is removed from around the banner because the banner's own conditions (`isLoading && isFeedbackSuggestionsEnabled`, etc.) already prevent rendering when there's nothing to show.
- Accessibility: `p-tag` renders with WCAG-AA contrast; spinner has `aria-label` via translate key.

---

## Files Changed

| File | Change |
|------|--------|
| `assessment/manage/assessment-layout/assessment-layout.component.html` | Wrap header in flex row, add named `ng-content` slot |
| `assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.html` | Replace `p-message` with compact inline elements |
| `assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.ts` | Swap `MessageModule` for `TagModule` |
| `modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component.html` | Add `jhiBannerSlot`, move banner out of submission guard |
| `text/manage/assess/submission-assessment/text-submission-assessment.component.html` | Add `jhiBannerSlot`, move banner out of submission guard |
| `programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component.html` | Add `jhiBannerSlot`, move banner out of submission guard |
| Banner spec file | Update tests for new visual structure |

---

## Out of Scope

- Changes to `assessment-header` inputs or logic
- Changes to the banner's input API
- Any other exercise types (file upload, quiz) — they don't use the banner
