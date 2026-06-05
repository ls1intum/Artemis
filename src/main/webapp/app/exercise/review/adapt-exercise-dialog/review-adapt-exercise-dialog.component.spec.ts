import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReviewAdaptExerciseDialogComponent } from 'app/exercise/review/adapt-exercise-dialog/review-adapt-exercise-dialog.component';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('ReviewAdaptExerciseDialogComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ReviewAdaptExerciseDialogComponent>;
    let comp: ReviewAdaptExerciseDialogComponent;
    let dialogRef: { close: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        dialogRef = { close: vi.fn() };

        await TestBed.configureTestingModule({
            imports: [ReviewAdaptExerciseDialogComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: DynamicDialogConfig, useValue: { data: { findingText: 'Signature mismatch in solution.' } } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ReviewAdaptExerciseDialogComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should expose the read-only finding text from the dialog config', () => {
        expect(comp.findingText).toBe('Signature mismatch in solution.');
    });

    it('should close with trimmed instructions on confirm', () => {
        comp.instructions.set('  widen the input range  ');
        comp.confirm();
        expect(dialogRef.close).toHaveBeenCalledWith({ instructions: 'widen the input range' });
    });

    it('should close with undefined instructions when none are given', () => {
        comp.instructions.set('   ');
        comp.confirm();
        expect(dialogRef.close).toHaveBeenCalledWith({ instructions: undefined });
    });

    it('should close with undefined result on cancel', () => {
        comp.cancel();
        expect(dialogRef.close).toHaveBeenCalledWith(undefined);
    });
});
