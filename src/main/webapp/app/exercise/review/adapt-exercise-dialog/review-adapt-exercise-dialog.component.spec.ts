import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ConsistencyIssueCategoryEnum, ConsistencyIssueSeverityEnum } from 'app/openapi/model/consistency-issue';
import { AdaptFinding, adaptFindingTagSeverity } from 'app/exercise/review/review-comment-utils';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { ReviewAdaptExerciseDialogComponent, ReviewAdaptExerciseDialogResult } from 'app/exercise/review/adapt-exercise-dialog/review-adapt-exercise-dialog.component';

function finding(severity: ConsistencyIssueSeverityEnum, description: string, extra: Partial<AdaptFinding> = {}): AdaptFinding {
    return {
        source: 'finding',
        category: ConsistencyIssueCategoryEnum.MethodReturnTypeMismatch,
        severity,
        tagSeverity: adaptFindingTagSeverity(severity),
        description,
        targetType: CommentThreadLocationType.SOLUTION_REPO,
        ...extra,
    };
}

function comment(threadId: number, description: string, targetType = CommentThreadLocationType.PROBLEM_STATEMENT): AdaptFinding {
    return { threadId, source: 'comment', description, tagSeverity: 'info', targetType, authorName: 'Instructor' };
}

async function setup(findings?: AdaptFinding[]): Promise<{
    component: ReviewAdaptExerciseDialogComponent;
    fixture: ComponentFixture<ReviewAdaptExerciseDialogComponent>;
    confirmed: ReturnType<typeof vi.fn>;
    cancelled: ReturnType<typeof vi.fn>;
}> {
    await TestBed.configureTestingModule({
        imports: [ReviewAdaptExerciseDialogComponent],
        providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
    }).compileComponents();
    const fixture = TestBed.createComponent(ReviewAdaptExerciseDialogComponent);
    if (findings) {
        fixture.componentRef.setInput('findings', findings);
    }
    const confirmed = vi.fn();
    const cancelled = vi.fn();
    fixture.componentInstance.confirmed.subscribe(confirmed);
    fixture.componentInstance.cancelled.subscribe(cancelled);
    fixture.detectChanges();
    return { component: fixture.componentInstance, fixture, confirmed, cancelled };
}

/** The template renders exactly two kit buttons, cancel first. */
function actionButtons(fixture: ComponentFixture<ReviewAdaptExerciseDialogComponent>): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('tum-ui-button button'));
}

describe('ReviewAdaptExerciseDialogComponent', () => {
    afterEach(() => TestBed.resetTestingModule());

    it('renders the findings as a labelled list, highest severity first, and discloses automatic persistence', async () => {
        const { fixture } = await setup([
            finding(ConsistencyIssueSeverityEnum.Low, 'sorted-last'),
            finding(ConsistencyIssueSeverityEnum.High, 'sorted-first'),
            finding(ConsistencyIssueSeverityEnum.Medium, 'sorted-second'),
        ]);

        const list = fixture.nativeElement.querySelector('[role="group"][aria-labelledby="adaptExerciseFindingsHeading"]');
        expect(fixture.nativeElement.querySelector('#adaptExerciseFindingsHeading')).not.toBeNull();
        const items = Array.from(list.querySelectorAll('li'), (item) => (item as HTMLElement).textContent);
        expect(items[0]).toContain('sorted-first');
        expect(items[1]).toContain('sorted-second');
        expect(items[2]).toContain('sorted-last');
        expect(list.querySelectorAll('tum-ui-tag')).toHaveLength(3);
        // The notice is present from the moment the dialog opens, so it is read in document order rather than
        // announced: this message delegates announcements to its surrounding dialog.
        const notice = fixture.nativeElement.querySelector('[data-testid="adapt-persistence-notice"]');
        expect(notice).not.toBeNull();
        expect(notice.getAttribute('role')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('adaptExercise.persistenceNotice');
    });

    it('requires instructions before confirming when no findings were selected', async () => {
        const { component, fixture, confirmed } = await setup();
        const [, confirmButton] = actionButtons(fixture);
        expect(confirmButton.disabled).toBe(true);

        confirmButton.click();
        expect(confirmed).not.toHaveBeenCalled();

        component.instructions.set('  make it harder  ');
        fixture.detectChanges();
        expect(confirmButton.disabled).toBe(false);

        confirmButton.click();
        expect(confirmed).toHaveBeenCalledExactlyOnceWith({ instructions: 'make it harder' } satisfies ReviewAdaptExerciseDialogResult);
    });

    it('confirms with findings and no instructions, reporting an undefined prompt', async () => {
        const { fixture, confirmed } = await setup([finding(ConsistencyIssueSeverityEnum.High, 'fix it')]);
        const [, confirmButton] = actionButtons(fixture);

        expect(confirmButton.disabled).toBe(false);
        confirmButton.click();

        expect(confirmed).toHaveBeenCalledExactlyOnceWith({ instructions: undefined } satisfies ReviewAdaptExerciseDialogResult);
    });

    it('lets the instructor select and deselect existing comments without typing instructions', async () => {
        const { fixture, component, confirmed } = await setup([comment(7, 'Clarify empty input')]);
        fixture.componentRef.setInput('selectedFeedbackThreadIds', []);
        fixture.detectChanges();
        const [, confirm] = actionButtons(fixture);
        expect(confirm.disabled).toBe(true);
        const checkbox = fixture.nativeElement.querySelector('[data-testid="adapt-feedback-selection"] input') as HTMLInputElement;
        checkbox.click();
        fixture.detectChanges();
        expect(confirm.disabled).toBe(false);
        confirm.click();
        expect(confirmed).toHaveBeenCalledWith({ instructions: undefined, selectedFeedbackThreadIds: [7] });
        checkbox.click();
        fixture.detectChanges();
        expect(confirm.disabled).toBe(true);
        expect(component.selectedIds()).toEqual([]);
    });

    it('reports a cancellation without a result', async () => {
        const { fixture, confirmed, cancelled } = await setup([finding(ConsistencyIssueSeverityEnum.High, 'fix it')]);

        actionButtons(fixture)[0].click();

        expect(cancelled).toHaveBeenCalledOnce();
        expect(confirmed).not.toHaveBeenCalled();
    });

    it('caps the instructions and describes the textarea with the help text and the remaining-character count', async () => {
        const { fixture } = await setup();

        const textarea = fixture.nativeElement.querySelector('#adaptExerciseInstructions');
        expect(textarea.getAttribute('maxlength')).toBe('8000');
        expect(textarea.getAttribute('aria-describedby')).toBe('adaptExerciseFreeHelp adaptExerciseCharacterCount');
        expect(fixture.nativeElement.querySelector('#adaptExerciseCharacterCount').textContent).toContain('adaptExercise.charactersRemaining');
    });
    it('submits selected comments together with trimmed additional instructions', async () => {
        const { fixture, component, confirmed } = await setup([comment(7, 'Clarify empty input')]);
        fixture.componentRef.setInput('selectedFeedbackThreadIds', [7]);
        component.instructions.set('  Include an example too.  ');
        fixture.detectChanges();
        actionButtons(fixture)[1].click();
        expect(confirmed).toHaveBeenCalledExactlyOnceWith({ instructions: 'Include an example too.', selectedFeedbackThreadIds: [7] });
    });

    it('blocks a run that becomes active while drafting without losing either input', async () => {
        const { fixture, component, confirmed } = await setup([comment(7, 'Clarify empty input')]);
        fixture.componentRef.setInput('selectedFeedbackThreadIds', [7]);
        component.instructions.set('Keep this instruction');
        fixture.componentRef.setInput('blockedReason', 'artemisApp.review.adaptExercise.runInProgress');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="adapt-blocked-reason"]').textContent).toContain('runInProgress');
        actionButtons(fixture)[1].click();
        expect(confirmed).not.toHaveBeenCalled();
        fixture.componentRef.setInput('blockedReason', undefined);
        fixture.detectChanges();
        actionButtons(fixture)[1].click();
        expect(confirmed).toHaveBeenCalledExactlyOnceWith({ instructions: 'Keep this instruction', selectedFeedbackThreadIds: [7] });
    });

    it('locks submission inputs while pending and retains them for retry after a failure', async () => {
        const { fixture, component, confirmed } = await setup([comment(7, 'Clarify empty input')]);
        fixture.componentRef.setInput('selectedFeedbackThreadIds', [7]);
        component.instructions.set('Keep my guidance');
        fixture.componentRef.setInput('submitting', true);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(actionButtons(fixture).every((button) => button.disabled)).toBe(true);
        expect(fixture.nativeElement.querySelector('textarea').disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('input[type="checkbox"]').disabled).toBe(true);
        component['confirm']();
        expect(confirmed).not.toHaveBeenCalled();
        fixture.componentRef.setInput('submitting', false);
        fixture.componentRef.setInput('submissionError', 'artemisApp.review.adaptExercise.startFailed');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('startFailed');
        actionButtons(fixture)[1].click();
        expect(confirmed).toHaveBeenCalledExactlyOnceWith({ instructions: 'Keep my guidance', selectedFeedbackThreadIds: [7] });
    });

    it('does not reference absent help text when comments exist but none are selected', async () => {
        const { fixture } = await setup([comment(7, 'Clarify empty input')]);
        fixture.componentRef.setInput('selectedFeedbackThreadIds', []);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('textarea').getAttribute('aria-describedby')).toBe('adaptExerciseCharacterCount');
    });

    it('rejects overlong instructions even if a review comment is selected', async () => {
        const { fixture, component, confirmed } = await setup([finding(ConsistencyIssueSeverityEnum.High, 'fix it')]);
        component.instructions.set('a'.repeat(8001));
        fixture.detectChanges();
        component['confirm']();
        expect(confirmed).not.toHaveBeenCalled();
        expect(actionButtons(fixture)[1].disabled).toBe(true);
    });
    it('offers progress in a new tab without discarding the adaptation draft', async () => {
        const { component, fixture } = await setup();
        component.instructions.set('Keep my instructions');
        fixture.componentRef.setInput('progressLink', ['/course-management', 1, 'programming-exercises', 42, 'generation']);
        fixture.componentRef.setInput('submissionError', 'artemisApp.review.adaptExercise.startFailed');
        fixture.detectChanges();
        const link = fixture.nativeElement.querySelector('[data-testid="adapt-open-progress"]') as HTMLAnchorElement;
        expect(link.getAttribute('href')).toBe('/course-management/1/programming-exercises/42/generation');
        expect(link.target).toBe('_blank');
        expect(link.rel).toBe('noopener');
        expect(component.instructions()).toBe('Keep my instructions');
    });

    it('groups comments by where they sit, selects a whole group through its checkbox, and clears everything', async () => {
        const { fixture, component } = await setup([
            comment(1, 'statement first', CommentThreadLocationType.PROBLEM_STATEMENT),
            comment(2, 'template', CommentThreadLocationType.TEMPLATE_REPO),
            comment(3, 'template again', CommentThreadLocationType.TEMPLATE_REPO),
        ]);
        fixture.componentRef.setInput('selectedFeedbackThreadIds', [1]);
        fixture.detectChanges();

        const groups = Array.from(fixture.nativeElement.querySelectorAll('section[data-testid^="adapt-group-"]')) as HTMLElement[];
        expect(groups.map((group) => group.dataset['testid'])).toEqual(['adapt-group-PROBLEM_STATEMENT', 'adapt-group-TEMPLATE_REPO']);

        const templateGroupCheckbox = groups[1].querySelector('[data-testid="adapt-group-selection"] input') as HTMLInputElement;
        templateGroupCheckbox.click();
        fixture.detectChanges();
        expect(component.selectedIds()).toEqual([1, 2, 3]);
        expect(component['selectedCount']()).toBe(3);

        (fixture.nativeElement.querySelector('[data-testid="adapt-clear-selection"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(component.selectedIds()).toEqual([]);
        (fixture.nativeElement.querySelector('[data-testid="adapt-select-visible"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(component.selectedIds()).toEqual([1, 2, 3]);
    });

    it('offers a filter and a search only with many comments, and "select shown" selects exactly the visible ones', async () => {
        const few = await setup([comment(1, 'a'), comment(2, 'b')]);
        expect(few.fixture.nativeElement.querySelector('[data-testid="adapt-triage-tools"]')).toBeNull();
        TestBed.resetTestingModule();

        const { fixture, component } = await setup([
            comment(1, 'rename the method'),
            comment(2, 'rename the class'),
            comment(3, 'add a test', CommentThreadLocationType.TEST_REPO),
            finding(ConsistencyIssueSeverityEnum.High, 'return type mismatch', { threadId: 4 }),
            finding(ConsistencyIssueSeverityEnum.Low, 'unused import', { threadId: 5 }),
        ]);
        fixture.componentRef.setInput('selectedFeedbackThreadIds', []);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="adapt-triage-tools"]')).not.toBeNull();

        component['query'].set('rename');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelectorAll('[data-testid="adapt-feedback-selection"]')).toHaveLength(2);
        (fixture.nativeElement.querySelector('[data-testid="adapt-select-visible"]') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(component.selectedIds()).toEqual([1, 2]);

        component['query'].set('');
        component['onFilterChange']('findings');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelectorAll('[data-testid="adapt-feedback-selection"]')).toHaveLength(2);
        component['onFilterChange']('selected');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelectorAll('[data-testid="adapt-feedback-selection"]')).toHaveLength(2);
        // The selection survives the view changing underneath it: the count still reports what is selected overall.
        expect(component['selectedCount']()).toBe(2);

        component['query'].set('nothing matches this');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="adapt-no-match"]')).not.toBeNull();
    });

    it('clamps a long description until it is expanded', async () => {
        const long = 'line\n'.repeat(12);
        const { fixture } = await setup([comment(1, long), comment(2, 'short')]);
        const labels = Array.from(fixture.nativeElement.querySelectorAll('label.line-clamp-3'));
        expect(labels).toHaveLength(1);
        const expand = fixture.nativeElement.querySelector('[data-testid="adapt-finding-expand"]') as HTMLButtonElement;
        expect(expand.getAttribute('aria-expanded')).toBe('false');
        expand.click();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelectorAll('label.line-clamp-3')).toHaveLength(0);
        expect(expand.getAttribute('aria-expanded')).toBe('true');
    });
});
