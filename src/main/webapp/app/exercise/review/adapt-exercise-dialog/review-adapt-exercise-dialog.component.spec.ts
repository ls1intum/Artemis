import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ConsistencyIssueCategoryEnum, ConsistencyIssueSeverityEnum } from 'app/openapi/model/consistency-issue';
import { AdaptFinding, adaptFindingTagSeverity } from 'app/exercise/review/review-comment-utils';
import { ReviewAdaptExerciseDialogComponent, ReviewAdaptExerciseDialogResult } from 'app/exercise/review/adapt-exercise-dialog/review-adapt-exercise-dialog.component';

function finding(severity: ConsistencyIssueSeverityEnum, description: string): AdaptFinding {
    return { category: ConsistencyIssueCategoryEnum.MethodReturnTypeMismatch, severity, tagSeverity: adaptFindingTagSeverity(severity), description };
}

async function setup(findings?: AdaptFinding[]): Promise<{
    component: ReviewAdaptExerciseDialogComponent;
    fixture: ComponentFixture<ReviewAdaptExerciseDialogComponent>;
    confirmed: ReturnType<typeof vi.fn>;
    cancelled: ReturnType<typeof vi.fn>;
}> {
    await TestBed.configureTestingModule({
        imports: [ReviewAdaptExerciseDialogComponent],
        providers: [{ provide: TranslateService, useClass: MockTranslateService }],
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

        const list = fixture.nativeElement.querySelector('ul[aria-labelledby="adaptExerciseFindingsHeading"]');
        expect(fixture.nativeElement.querySelector('#adaptExerciseFindingsHeading')).not.toBeNull();
        const items = Array.from(list.querySelectorAll('li'), (item) => (item as HTMLElement).textContent);
        expect(items[0]).toContain('sorted-first');
        expect(items[1]).toContain('sorted-second');
        expect(items[2]).toContain('sorted-last');
        expect(list.querySelectorAll('tum-ui-tag')).toHaveLength(3);
        // The notice is present from the moment the dialog opens, so it is read in document order rather than
        // announced: `tum-ui-message` is only a live region when asked, and this one deliberately does not ask.
        const notice = fixture.nativeElement.querySelector('tum-ui-message[data-severity="warning"]');
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
});
