import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { AdaptFinding, adaptFindingTagSeverity } from 'app/exercise/review/review-comment-utils';
import {
    ReviewAdaptExerciseDialogComponent,
    ReviewAdaptExerciseDialogData,
    ReviewAdaptExerciseDialogResult,
} from 'app/exercise/review/adapt-exercise-dialog/review-adapt-exercise-dialog.component';

function finding(severity: ConsistencyIssue.SeverityEnum, description: string): AdaptFinding {
    return { category: ConsistencyIssue.CategoryEnum.MethodReturnTypeMismatch, severity, tagSeverity: adaptFindingTagSeverity(severity), description };
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
    setupTestBed({ zoneless: true });

    afterEach(() => TestBed.resetTestingModule());

    it('sorts findings by severity, highest first', async () => {
        const { component } = await setup({
            findings: [
                finding(ConsistencyIssue.SeverityEnum.Low, 'low'),
                finding(ConsistencyIssue.SeverityEnum.High, 'high'),
                finding(ConsistencyIssue.SeverityEnum.Medium, 'medium'),
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
        const { component, close } = await setup({ findings: [finding(ConsistencyIssue.SeverityEnum.High, 'fix it')] });
        expect(component.confirmDisabled()).toBe(false);
        component.confirm();
        expect(close).toHaveBeenCalledWith({ instructions: undefined } satisfies ReviewAdaptExerciseDialogResult);
    });

    it('closes with undefined when cancelled', async () => {
        const { component, close } = await setup({ findings: [finding(ConsistencyIssue.SeverityEnum.High, 'fix it')] });
        component.cancel();
        expect(close).toHaveBeenCalledWith(undefined);
    });
});
