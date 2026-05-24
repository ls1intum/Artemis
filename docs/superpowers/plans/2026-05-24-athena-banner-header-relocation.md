# Athena Banner Header Relocation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Relocate `jhi-feedback-suggestions-banner` from a full-width block below the assessment header to a compact inline indicator on the right side of the assessment header row, across modeling, text, and programming assessment pages.

**Architecture:** Use Angular named content projection (`ng-content select="[jhiBannerSlot]"`) in `assessment-layout` to place a banner slot beside `jhi-assessment-header` in a flex row — no new inputs or prop-drilling required. Redesign the banner's three visual states from full-width `p-message` blocks to compact inline elements (`p-tag` chips and a spinner+text row).

**Tech Stack:** Angular 21 signals, PrimeNG (`Tag` from `primeng/tag`, `TooltipModule` from `primeng/tooltip`), FontAwesome icons, Vitest + `@analogjs/vitest-angular`

---

## File Map

| File | Change |
|------|--------|
| `src/main/webapp/app/assessment/manage/assessment-layout/assessment-layout.component.html` | Wrap header in flex row + add named `ng-content` slot |
| `src/main/webapp/app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.html` | Replace `p-message` blocks with compact inline elements |
| `src/main/webapp/app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.ts` | Swap `MessageModule` for `Tag` |
| `src/main/webapp/app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.spec.ts` | Update selectors to match new markup |
| `src/main/webapp/app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component.html` | Add `jhiBannerSlot`, move banner out of `@if (submission)` |
| `src/main/webapp/app/text/manage/assess/submission-assessment/text-submission-assessment.component.html` | Add `jhiBannerSlot`, move banner out of `@if (submission)` |
| `src/main/webapp/app/programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component.html` | Add `jhiBannerSlot`, move banner out of `@if (submission)` |

---

## Task 1: Add named content slot to assessment-layout

**Files:**
- Modify: `src/main/webapp/app/assessment/manage/assessment-layout/assessment-layout.component.html`

- [ ] **Step 1: Update assessment-layout template**

Replace the current opening of `assessment-layout.component.html` (the standalone `<jhi-assessment-header … />` line) with a flex wrapper that places the header and the named banner slot side-by-side.

Current first two meaningful lines of the file:
```html
@let complaint = this.complaint();
<jhi-assessment-header
    (navigateBack)="navigateBack.emit()"
    ...
/>
```

Replace them with:
```html
@let complaint = this.complaint();
<div class="d-flex align-items-center flex-wrap gap-2 mb-1">
    <jhi-assessment-header
        class="flex-grow-1"
        (navigateBack)="navigateBack.emit()"
        [isLoading]="isLoading()"
        [saveBusy]="saveBusy()"
        [submitBusy]="submitBusy()"
        [cancelBusy]="cancelBusy()"
        [nextSubmissionBusy]="nextSubmissionBusy()"
        [isTeamMode]="isTeamMode()"
        [isAssessor]="isAssessor()"
        [isTestRun]="isTestRun()"
        [exerciseDashboardLink]="exerciseDashboardLink()"
        [canOverride]="canOverride()"
        [assessmentsAreValid]="assessmentsAreValid()"
        [hasAssessmentDueDatePassed]="hasAssessmentDueDatePassed()"
        [exercise]="exercise()"
        [result]="result()"
        [correctionRound]="correctionRound()"
        [isProgrammingExercise]="isProgrammingExercise()"
        (save)="save.emit()"
        (onSubmit)="onSubmit.emit()"
        (onCancel)="onCancel.emit()"
        [(highlightDifferences)]="highlightDifferences"
        (nextSubmission)="nextSubmission.emit()"
        [hasComplaint]="!!complaint"
        [hasMoreFeedbackRequest]="complaint?.complaintType === MORE_FEEDBACK"
        [complaintHandled]="!!complaint ? complaint.accepted !== undefined : false"
        [complaintType]="complaint?.complaintType"
        (useAsExampleSubmission)="useAsExampleSubmission.emit()"
    />
    <ng-content select="[jhiBannerSlot]" />
</div>
<jhi-assessment-complaint-alert [complaint]="complaint" />
<ng-content />
```

The rest of the file (`@if (submission()) { <jhi-assessment-note … /> }` and the complaint form) stays unchanged.

- [ ] **Step 2: Run the lint check to confirm no template errors**

```bash
pnpm run lint 2>&1 | grep -i "assessment-layout" | head -20
```

Expected: no errors referencing `assessment-layout.component.html`.

- [ ] **Step 3: Commit**

```bash
git add src/main/webapp/app/assessment/manage/assessment-layout/assessment-layout.component.html
git commit -m "feat(assessment-layout): add named banner slot beside assessment header"
```

---

## Task 2: Redesign banner component — compact inline visuals

**Files:**
- Modify: `src/main/webapp/app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.html`
- Modify: `src/main/webapp/app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.ts`

- [ ] **Step 1: Update the TypeScript — swap MessageModule for Tag**

Replace the entire content of `feedback-suggestions-banner.component.ts` with:

```typescript
import { Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Tag } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-feedback-suggestions-banner',
    templateUrl: './feedback-suggestions-banner.component.html',
    imports: [Tag, TooltipModule, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class FeedbackSuggestionsBannerComponent {
    readonly isLoading = input.required<boolean>();
    readonly hasAutomaticFeedback = input.required<boolean>();
    readonly isAssessor = input.required<boolean>();
    readonly resultCompletionDate = input<dayjs.Dayjs | undefined>(undefined);
    readonly isFeedbackSuggestionsEnabled = input.required<boolean>();

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faQuestionCircle = faQuestionCircle;
}
```

- [ ] **Step 2: Update the template — compact inline elements**

Replace the entire content of `feedback-suggestions-banner.component.html` with:

```html
@if (hasAutomaticFeedback() && isAssessor() && !resultCompletionDate() && !isFeedbackSuggestionsEnabled()) {
    <p-tag
        severity="info"
        styleClass="text-wrap"
        [value]="'artemisApp.assessment.feedbackSuggestions.automaticAssessmentAvailable' | artemisTranslate"
    />
}
@if (hasAutomaticFeedback() && isAssessor() && !resultCompletionDate() && isFeedbackSuggestionsEnabled()) {
    <div class="d-flex align-items-center gap-1">
        <p-tag
            severity="info"
            styleClass="text-wrap"
            [value]="'artemisApp.assessment.feedbackSuggestions.generativeAIAssessmentAvailable' | artemisTranslate"
        />
        <fa-icon
            [icon]="faQuestionCircle"
            size="lg"
            class="ms-1 cursor-pointer"
            [pTooltip]="'artemisApp.assessment.feedbackSuggestions.generativeAIAssessmentInfo' | artemisTranslate"
            tooltipPosition="left"
        />
    </div>
}
@if (isLoading() && isFeedbackSuggestionsEnabled()) {
    <div class="d-flex align-items-center gap-1 text-secondary" style="font-size: 80%">
        <fa-icon [icon]="faCircleNotch" animation="spin" />
        <span jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"></span>
    </div>
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/webapp/app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.html
git add src/main/webapp/app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.ts
git commit -m "feat(feedback-suggestions-banner): replace p-message blocks with compact inline elements"
```

---

## Task 3: Update banner spec for new markup

**Files:**
- Modify: `src/main/webapp/app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.spec.ts`

The existing tests query by `[jhiTranslate="…"]` on the translate attribute. After the redesign the translate directives and `artemisTranslate` pipe are still present, but the selectors need to stay accurate. The three tests for "show/hide" states still work with `By.css('[jhiTranslate="..."]')`. Only the "no banners" test changes because `<p-tag [value]="… | artemisTranslate">` does NOT use `jhiTranslate` — it uses the pipe instead.

- [ ] **Step 1: Update the spec**

Replace the entire content of `feedback-suggestions-banner.component.spec.ts` with:

```typescript
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FeedbackSuggestionsBannerComponent } from 'app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component';
import { Tag } from 'primeng/tag';

describe('FeedbackSuggestionsBannerComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<FeedbackSuggestionsBannerComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FeedbackSuggestionsBannerComponent);
                fixture.componentRef.setInput('isLoading', false);
                fixture.componentRef.setInput('hasAutomaticFeedback', false);
                fixture.componentRef.setInput('isAssessor', false);
                fixture.componentRef.setInput('isFeedbackSuggestionsEnabled', false);
            });
    });

    it('should show the non-AI automatic assessment tag when feedback suggestions are disabled', () => {
        fixture.componentRef.setInput('hasAutomaticFeedback', true);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.detectChanges();

        const tags = fixture.debugElement.queryAll(By.directive(Tag));
        expect(tags).toHaveLength(1);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeFalsy();
    });

    it('should show the generative AI assessment tag when feedback suggestions are enabled', () => {
        fixture.componentRef.setInput('hasAutomaticFeedback', true);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.componentRef.setInput('isFeedbackSuggestionsEnabled', true);
        fixture.detectChanges();

        const tags = fixture.debugElement.queryAll(By.directive(Tag));
        expect(tags).toHaveLength(1);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeFalsy();
    });

    it('should show the loading spinner when loading feedback suggestions', () => {
        fixture.componentRef.setInput('isLoading', true);
        fixture.componentRef.setInput('isFeedbackSuggestionsEnabled', true);
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.directive(Tag))).toHaveLength(0);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeTruthy();
    });

    it('should render nothing when no conditions are met', () => {
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.directive(Tag))).toHaveLength(0);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeFalsy();
    });
});
```

- [ ] **Step 2: Run the banner spec**

```bash
pnpm run vitest -- src/main/webapp/app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.spec.ts
```

Expected: 4 tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/webapp/app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.spec.ts
git commit -m "test(feedback-suggestions-banner): update specs for compact inline markup"
```

---

## Task 4: Update modeling assessment template

**Files:**
- Modify: `src/main/webapp/app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component.html`

- [ ] **Step 1: Move banner out of submission guard and add slot attribute**

In `modeling-assessment-editor.component.html`, the banner is currently inside `@if (submission) { … }` as the first child. Move it to be a direct child of `<jhi-assessment-layout>` — before the `@if (submission)` block — and add the `jhiBannerSlot` attribute.

Replace this section:
```html
    @if (submission) {
        <jhi-feedback-suggestions-banner
            [isLoading]="isLoading"
            [hasAutomaticFeedback]="hasAutomaticFeedback"
            [isAssessor]="isAssessor"
            [resultCompletionDate]="result?.completionDate"
            [isFeedbackSuggestionsEnabled]="isFeedbackSuggestionsEnabled"
        />
        <div class="editor-container flex-grow-1">
```

With:
```html
    <jhi-feedback-suggestions-banner
        jhiBannerSlot
        [isLoading]="isLoading"
        [hasAutomaticFeedback]="hasAutomaticFeedback"
        [isAssessor]="isAssessor"
        [resultCompletionDate]="result?.completionDate"
        [isFeedbackSuggestionsEnabled]="isFeedbackSuggestionsEnabled"
    />
    @if (submission) {
        <div class="editor-container flex-grow-1">
```

- [ ] **Step 2: Commit**

```bash
git add src/main/webapp/app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component.html
git commit -m "feat(modeling-assessment): project feedback-suggestions-banner into header slot"
```

---

## Task 5: Update text assessment template

**Files:**
- Modify: `src/main/webapp/app/text/manage/assess/submission-assessment/text-submission-assessment.component.html`

- [ ] **Step 1: Move banner out of submission guard and add slot attribute**

In `text-submission-assessment.component.html`, the banner is the first child inside `@if (submission) { … }`. Move it before the guard and add `jhiBannerSlot`.

Replace this section:
```html
    @if (submission) {
        <!--        TODO: hasAutomaticFeedback is currently always false; the banner will not be shown until the feedback type detection is fixed -->
        <jhi-feedback-suggestions-banner
            [isLoading]="isLoading()"
            [hasAutomaticFeedback]="hasAutomaticFeedback"
            [isAssessor]="isAssessor"
            [resultCompletionDate]="result?.completionDate"
            [isFeedbackSuggestionsEnabled]="isFeedbackSuggestionsEnabled"
        />
        <div class="row">
```

With:
```html
    <!--        TODO: hasAutomaticFeedback is currently always false; the banner will not be shown until the feedback type detection is fixed -->
    <jhi-feedback-suggestions-banner
        jhiBannerSlot
        [isLoading]="isLoading()"
        [hasAutomaticFeedback]="hasAutomaticFeedback"
        [isAssessor]="isAssessor"
        [resultCompletionDate]="result?.completionDate"
        [isFeedbackSuggestionsEnabled]="isFeedbackSuggestionsEnabled"
    />
    @if (submission) {
        <div class="row">
```

- [ ] **Step 2: Commit**

```bash
git add src/main/webapp/app/text/manage/assess/submission-assessment/text-submission-assessment.component.html
git commit -m "feat(text-assessment): project feedback-suggestions-banner into header slot"
```

---

## Task 6: Update programming assessment template

**Files:**
- Modify: `src/main/webapp/app/programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component.html`

- [ ] **Step 1: Move banner out of submission guard and add slot attribute**

In `code-editor-tutor-assessment-container.component.html`, the banner is the only child inside a dedicated `@if (submission)` block (separate from the block that renders `<ng-container *ngTemplateOutlet="assessment" />`). Unwrap it and add `jhiBannerSlot`.

Replace:
```html
        @if (submission) {
            <jhi-feedback-suggestions-banner
                [isLoading]="loadingFeedbackSuggestions"
                [hasAutomaticFeedback]="hasAutomaticFeedback"
                [isAssessor]="isAssessor"
                [resultCompletionDate]="manualResult?.completionDate"
                [isFeedbackSuggestionsEnabled]="isFeedbackSuggestionsEnabled"
            />
        }
        @if (submission) {
```

With:
```html
        <jhi-feedback-suggestions-banner
            jhiBannerSlot
            [isLoading]="loadingFeedbackSuggestions"
            [hasAutomaticFeedback]="hasAutomaticFeedback"
            [isAssessor]="isAssessor"
            [resultCompletionDate]="manualResult?.completionDate"
            [isFeedbackSuggestionsEnabled]="isFeedbackSuggestionsEnabled"
        />
        @if (submission) {
```

- [ ] **Step 2: Commit**

```bash
git add src/main/webapp/app/programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component.html
git commit -m "feat(programming-assessment): project feedback-suggestions-banner into header slot"
```

---

## Task 7: Run full client test suite and lint

- [ ] **Step 1: Run all impacted specs**

```bash
pnpm run vitest -- src/main/webapp/app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component.spec.ts src/main/webapp/app/assessment/manage/assessment-layout/assessment-layout.component.spec.ts
```

Expected: all tests pass (no failures).

- [ ] **Step 2: Run lint**

```bash
pnpm run lint 2>&1 | grep -E "(assessment-layout|feedback-suggestions-banner|modeling-assessment-editor|text-submission-assessment|code-editor-tutor-assessment-container)" | head -30
```

Expected: no lint errors in changed files.

- [ ] **Step 3: Run broader related test-diff**

```bash
pnpm run test-diff 2>&1 | tail -20
```

Expected: no new failures introduced.
