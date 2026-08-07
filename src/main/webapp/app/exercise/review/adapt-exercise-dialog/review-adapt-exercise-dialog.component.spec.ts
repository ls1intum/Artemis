import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ConsistencyIssueCategoryEnum, ConsistencyIssueSeverityEnum } from 'app/openapi/model/consistency-issue';
import { AdaptFinding, adaptFindingTagSeverity } from 'app/exercise/review/review-comment-utils';
import {
    ReviewAdaptExerciseDialogComponent,
    ReviewAdaptExerciseDialogData,
    ReviewAdaptExerciseDialogResult,
} from 'app/exercise/review/adapt-exercise-dialog/review-adapt-exercise-dialog.component';

function finding(severity: ConsistencyIssueSeverityEnum, description: string): AdaptFinding {
    return { category: ConsistencyIssueCategoryEnum.MethodReturnTypeMismatch, severity, tagSeverity: adaptFindingTagSeverity(severity), description };
}

async function setup(data: ReviewAdaptExerciseDialogData): Promise<{
    component: ReviewAdaptExerciseDialogComponent;
    fixture: ComponentFixture<ReviewAdaptExerciseDialogComponent>;
    close: ReturnType<typeof vi.fn>;
}> {
    const close = vi.fn();
    await TestBed.configureTestingModule({
        imports: [ReviewAdaptExerciseDialogComponent],
        providers: [
            { provide: TranslateService, useClass: MockTranslateService },
            { provide: DynamicDialogRef, useValue: { close } },
            { provide: DynamicDialogConfig, useValue: { data } },
        ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ReviewAdaptExerciseDialogComponent);
    return { component: fixture.componentInstance, fixture, close };
}

describe('ReviewAdaptExerciseDialogComponent', () => {
    afterEach(() => TestBed.resetTestingModule());

    it('sorts findings by severity, highest first', async () => {
        const { component } = await setup({
            findings: [
                finding(ConsistencyIssueSeverityEnum.Low, 'low'),
                finding(ConsistencyIssueSeverityEnum.High, 'high'),
                finding(ConsistencyIssueSeverityEnum.Medium, 'medium'),
            ],
        });
        expect(component.findings.map((f) => f.description)).toEqual(['high', 'medium', 'low']);
        expect(component.isFreeMode).toBe(false);
    });

    it('is free mode when no findings are selected and requires instructions before confirming', async () => {
        const { component, close } = await setup({});
        expect(component.isFreeMode).toBe(true);
        expect(component.confirmDisabled()).toBe(true);

        component.confirm();
        expect(close).not.toHaveBeenCalled();

        component.instructions.set('  make it harder  ');
        expect(component.confirmDisabled()).toBe(false);
        component.confirm();
        expect(close).toHaveBeenCalledWith({ instructions: 'make it harder' } satisfies ReviewAdaptExerciseDialogResult);
    });

    it('allows confirming with findings and no instructions, sending an undefined prompt', async () => {
        const { component, close } = await setup({ findings: [finding(ConsistencyIssueSeverityEnum.High, 'fix it')] });
        expect(component.confirmDisabled()).toBe(false);
        component.confirm();
        expect(close).toHaveBeenCalledWith({ instructions: undefined } satisfies ReviewAdaptExerciseDialogResult);
    });

    it('closes with undefined when cancelled', async () => {
        const { component, close } = await setup({ findings: [finding(ConsistencyIssueSeverityEnum.High, 'fix it')] });
        component.cancel();
        expect(close).toHaveBeenCalledWith(undefined);
    });

    it('exposes the remaining instruction capacity and associates it with the textarea', async () => {
        const { component, fixture } = await setup({});
        component.instructions.set('abc');
        fixture.detectChanges();

        expect(component.remainingCharacters()).toBe(7997);
        const textarea = fixture.nativeElement.querySelector('#adaptExerciseInstructions');
        expect(textarea.getAttribute('aria-describedby')).toContain('adaptExerciseCharacterCount');
        expect(fixture.nativeElement.querySelector('#adaptExerciseCharacterCount').textContent).toContain('adaptExercise.charactersRemaining');
    });

    it('renders selected findings as a labelled list and discloses automatic persistence', async () => {
        const { fixture } = await setup({ findings: [finding(ConsistencyIssueSeverityEnum.High, 'fix it')] });
        fixture.detectChanges();

        const heading = fixture.nativeElement.querySelector('#adaptExerciseFindingsHeading');
        const list = fixture.nativeElement.querySelector('ul[aria-labelledby="adaptExerciseFindingsHeading"]');
        expect(heading).not.toBeNull();
        expect(list.querySelectorAll('li')).toHaveLength(1);
        expect(list.querySelectorAll('tum-ui-tag')).toHaveLength(1);
        expect(fixture.nativeElement.querySelector('tum-ui-message[severity="warn"][role="status"]')).not.toBeNull();
        expect(fixture.nativeElement.textContent).toContain('adaptExercise.persistenceNotice');
    });

    it('disables the confirm button while free mode has no instructions and confirms once they are entered', async () => {
        const { fixture, close } = await setup({});
        fixture.detectChanges();

        const [cancelButton, confirmButton] = fixture.nativeElement.querySelectorAll('.review-adapt-exercise-dialog__actions tum-ui-button button');
        expect(cancelButton.disabled).toBe(false);
        expect(confirmButton.disabled).toBe(true);

        confirmButton.click();
        expect(close).not.toHaveBeenCalled();

        fixture.componentInstance.instructions.set('rework the tests');
        fixture.detectChanges();
        expect(confirmButton.disabled).toBe(false);

        confirmButton.click();
        expect(close).toHaveBeenCalledWith({ instructions: 'rework the tests' } satisfies ReviewAdaptExerciseDialogResult);
    });
});
