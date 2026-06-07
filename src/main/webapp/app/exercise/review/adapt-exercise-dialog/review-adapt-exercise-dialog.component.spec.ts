import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewAdaptExerciseDialogComponent, ReviewAdaptExerciseDialogData } from 'app/exercise/review/adapt-exercise-dialog/review-adapt-exercise-dialog.component';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, describe, expect, it, vi } from 'vitest';

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

    describe('review-thread mode (finding present, S3)', () => {
        it('should expose the read-only finding text and not be in free mode', async () => {
            const { comp } = await createComponent({ findingText: 'Signature mismatch in solution.' });
            expect(comp.findingText).toBe('Signature mismatch in solution.');
            expect(comp.isFreeMode).toBe(false);
        });

        it('should allow confirm with no instructions (the finding alone suffices)', async () => {
            const { comp } = await createComponent({ findingText: 'Signature mismatch in solution.' });
            expect(comp.confirmDisabled()).toBe(false);
        });

        it('should close with trimmed instructions on confirm', async () => {
            const { comp } = await createComponent({ findingText: 'Signature mismatch in solution.' });
            comp.instructions.set('  widen the input range  ');
            comp.confirm();
            expect(dialogRef.close).toHaveBeenCalledWith({ instructions: 'widen the input range' });
        });

        it('should close with undefined instructions when none are given', async () => {
            const { comp } = await createComponent({ findingText: 'Signature mismatch in solution.' });
            comp.instructions.set('   ');
            comp.confirm();
            expect(dialogRef.close).toHaveBeenCalledWith({ instructions: undefined });
        });
    });

    describe('free mode (no finding, S4)', () => {
        it('should be in free mode with no finding text', async () => {
            const { comp } = await createComponent({});
            expect(comp.findingText).toBeUndefined();
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

    describe('presets (TIER-3, both modes)', () => {
        const REGENERATE_TESTS_VALUE_KEY = 'artemisApp.review.adaptExercise.presetRegenerateTestsValue';
        const SIMPLIFY_TEMPLATE_VALUE_KEY = 'artemisApp.review.adaptExercise.presetSimplifyTemplateValue';
        const EDGE_CASE_TESTS_VALUE_KEY = 'artemisApp.review.adaptExercise.presetEdgeCaseTestsValue';

        it('should set instructions to the resolved preset value in free mode and enable confirm', async () => {
            const { comp } = await createComponent({});
            expect(comp.confirmDisabled()).toBe(true);
            comp.applyPreset(REGENERATE_TESTS_VALUE_KEY);
            // MockTranslateService.instant echoes the key back as the resolved value.
            expect(comp.instructions()).toBe(REGENERATE_TESTS_VALUE_KEY);
            expect(comp.confirmDisabled()).toBe(false);
        });

        it('should set instructions to the resolved preset value in review-thread mode', async () => {
            const { comp } = await createComponent({ findingText: 'Signature mismatch in solution.' });
            comp.applyPreset(SIMPLIFY_TEMPLATE_VALUE_KEY);
            expect(comp.instructions()).toBe(SIMPLIFY_TEMPLATE_VALUE_KEY);
        });

        it('should overwrite previously typed instructions when a preset is applied', async () => {
            const { comp } = await createComponent({});
            comp.instructions.set('something the instructor typed');
            comp.applyPreset(EDGE_CASE_TESTS_VALUE_KEY);
            expect(comp.instructions()).toBe(EDGE_CASE_TESTS_VALUE_KEY);
        });
    });

    it('should close with undefined result on cancel', async () => {
        const { comp } = await createComponent({ findingText: 'x' });
        comp.cancel();
        expect(dialogRef.close).toHaveBeenCalledWith(undefined);
    });
});
