import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewAdaptExerciseDialogComponent, ReviewAdaptExerciseDialogData } from 'app/exercise/review/adapt-exercise-dialog/review-adapt-exercise-dialog.component';
import { AdaptFinding } from 'app/exercise/review/review-comment-utils';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, describe, expect, it, vi } from 'vitest';

function finding(overrides: Partial<AdaptFinding> = {}): AdaptFinding {
    return {
        category: ConsistencyIssue.CategoryEnum.MethodReturnTypeMismatch,
        severity: ConsistencyIssue.SeverityEnum.High,
        locationLabel: 'Solution: src/Sorter.java:12',
        description: 'Signature mismatch in solution.',
        ...overrides,
    };
}

describe('ReviewAdaptExerciseDialogComponent', () => {
    setupTestBed({ zoneless: true });
    let dialogRef: { close: ReturnType<typeof vi.fn> };

    async function createComponent(
        data: ReviewAdaptExerciseDialogData,
    ): Promise<{ fixture: ComponentFixture<ReviewAdaptExerciseDialogComponent>; comp: ReviewAdaptExerciseDialogComponent }> {
        dialogRef = { close: vi.fn() };
        await TestBed.configureTestingModule({
            imports: [ReviewAdaptExerciseDialogComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: DynamicDialogConfig, useValue: { data } },
            ],
        }).compileComponents();
        const fixture = TestBed.createComponent(ReviewAdaptExerciseDialogComponent);
        return { fixture, comp: fixture.componentInstance };
    }

    afterEach(() => {
        vi.restoreAllMocks();
        TestBed.resetTestingModule();
    });

    describe('review-thread mode (findings present, S3)', () => {
        it('should expose the read-only findings and not be in free mode', async () => {
            const { comp } = await createComponent({ findings: [finding()] });
            expect(comp.findings).toHaveLength(1);
            expect(comp.findings[0].description).toBe('Signature mismatch in solution.');
            expect(comp.isFreeMode).toBe(false);
        });

        it('should render one card per finding with a severity tag', async () => {
            const { fixture } = await createComponent({
                findings: [finding({ severity: ConsistencyIssue.SeverityEnum.High }), finding({ severity: ConsistencyIssue.SeverityEnum.Low, description: 'Minor naming issue.' })],
            });
            fixture.detectChanges();
            const host = fixture.nativeElement as HTMLElement;
            expect(host.querySelectorAll('.review-adapt-exercise-dialog__finding-card')).toHaveLength(2);
            expect(host.querySelectorAll('p-tag').length).toBeGreaterThanOrEqual(2);
        });

        it('maps severities to PrimeNG tag severities', async () => {
            const { comp } = await createComponent({ findings: [finding()] });
            expect(comp['severityTag'](ConsistencyIssue.SeverityEnum.High)).toBe('danger');
            expect(comp['severityTag'](ConsistencyIssue.SeverityEnum.Medium)).toBe('warn');
            expect(comp['severityTag'](ConsistencyIssue.SeverityEnum.Low)).toBe('info');
        });

        it('should allow confirm with no instructions (the finding alone suffices)', async () => {
            const { comp } = await createComponent({ findings: [finding()] });
            expect(comp.confirmDisabled()).toBe(false);
        });

        it('should close with trimmed instructions on confirm', async () => {
            const { comp } = await createComponent({ findings: [finding()] });
            comp.instructions.set('  widen the input range  ');
            comp.confirm();
            expect(dialogRef.close).toHaveBeenCalledWith({ instructions: 'widen the input range' });
        });

        it('should close with undefined instructions when none are given', async () => {
            const { comp } = await createComponent({ findings: [finding()] });
            comp.instructions.set('   ');
            comp.confirm();
            expect(dialogRef.close).toHaveBeenCalledWith({ instructions: undefined });
        });
    });

    describe('free mode (no findings, S4)', () => {
        it('should be in free mode with no findings', async () => {
            const { comp } = await createComponent({});
            expect(comp.findings).toEqual([]);
            expect(comp.isFreeMode).toBe(true);
        });

        it('should treat an empty findings array as free mode', async () => {
            const { comp } = await createComponent({ findings: [] });
            expect(comp.isFreeMode).toBe(true);
        });

        it('should require instructions: confirm is disabled until non-empty', async () => {
            const { comp } = await createComponent({});
            expect(comp.confirmDisabled()).toBe(true);
            comp.instructions.set('   ');
            expect(comp.confirmDisabled()).toBe(true);
            comp.instructions.set('make it harder');
            expect(comp.confirmDisabled()).toBe(false);
        });

        it('should not close while instructions are empty even if confirm is invoked', async () => {
            const { comp } = await createComponent({});
            comp.instructions.set('  ');
            comp.confirm();
            expect(dialogRef.close).not.toHaveBeenCalled();
        });

        it('should close with the typed instructions on confirm', async () => {
            const { comp } = await createComponent({});
            comp.instructions.set('  add edge-case tests  ');
            comp.confirm();
            expect(dialogRef.close).toHaveBeenCalledWith({ instructions: 'add edge-case tests' });
        });
    });

    it('should close with undefined result on cancel', async () => {
        const { comp } = await createComponent({ findings: [finding()] });
        comp.cancel();
        expect(dialogRef.close).toHaveBeenCalledWith(undefined);
    });
});
