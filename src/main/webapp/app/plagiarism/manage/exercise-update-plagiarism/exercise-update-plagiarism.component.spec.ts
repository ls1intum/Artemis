import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TranslateService } from '@ngx-translate/core';

import { ExerciseUpdatePlagiarismComponent } from './exercise-update-plagiarism.component';
import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Exercise Update Plagiarism Component', () => {
    setupTestBed({ zoneless: true });

    let comp: ExerciseUpdatePlagiarismComponent;
    let fixture: ComponentFixture<ExerciseUpdatePlagiarismComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseUpdatePlagiarismComponent, ReactiveFormsModule, FontAwesomeTestingModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseUpdatePlagiarismComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('exercise', new ProgrammingExercise(undefined, undefined));
    });

    it('should use provided plagiarism checks config', () => {
        const cfg = {
            continuousPlagiarismControlEnabled: true,
            continuousPlagiarismControlPostDueDateChecksEnabled: false,
            similarityThreshold: 1,
            minimumScore: 2,
            minimumSize: 3,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 10,
        };
        fixture.componentRef.setInput('exercise', { ...comp.exercise(), plagiarismDetectionConfig: cfg });

        comp.ngOnInit();

        expect(comp.exercise()?.plagiarismDetectionConfig).toEqual(cfg);
    });

    it('should use default if exercise does not have plagiarism checks config', () => {
        fixture.componentRef.setInput('exercise', { ...comp.exercise(), plagiarismDetectionConfig: undefined });
        comp.ngOnInit();

        expect(comp.exercise()?.plagiarismDetectionConfig).toEqual(DEFAULT_PLAGIARISM_DETECTION_CONFIG);
    });

    it('should set minimumTokenCountTooltip on init for programming', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        comp.ngOnInit();
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumTokenCountTooltipProgrammingExercise');
    });

    it('should set minimumSizeTooltip on init for text', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as Exercise);
        comp.ngOnInit();
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipTextExercise');
    });
    it('should set default plagiarism detection config on init if not set', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        comp.ngOnInit();
        expect(comp.exercise()?.plagiarismDetectionConfig).toEqual(DEFAULT_PLAGIARISM_DETECTION_CONFIG);
    });

    it('should enable cpc', () => {
        comp.form.patchValue({ continuousPlagiarismControlEnabled: false });
        expect(comp.form.get('continuousPlagiarismControlEnabled')?.value).toBe(false);

        comp.form.patchValue({ continuousPlagiarismControlEnabled: true });
        expect(comp.form.get('continuousPlagiarismControlEnabled')?.value).toBe(true);
        expect(comp.form.get('continuousPlagiarismControlPostDueDateChecksEnabled')?.enabled).toBe(true);
    });

    it('should disable cpc', () => {
        comp.form.patchValue({ continuousPlagiarismControlEnabled: true });
        expect(comp.form.get('continuousPlagiarismControlEnabled')?.value).toBe(true);

        comp.form.patchValue({ continuousPlagiarismControlEnabled: false });
        expect(comp.form.get('continuousPlagiarismControlEnabled')?.value).toBe(false);
        expect(comp.form.get('continuousPlagiarismControlPostDueDateChecksEnabled')?.disabled).toBe(true);
    });

    it('should get correct minimumSizeTooltip for programming exercises', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        comp.ngOnInit();
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumTokenCountTooltipProgrammingExercise');
    });

    it('should get correct minimumSizeTooltip for text exercises', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as Exercise);
        comp.ngOnInit();
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipTextExercise');
    });

    it('should get correct minimumTokenCountLabel for programming exercises', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        comp.ngOnInit();
        expect(comp.getMinimumSizeLabel()).toBe('artemisApp.plagiarism.minimumTokenCount');
    });

    it('should get correct minimumSizeLabel for text exercises', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as Exercise);
        comp.ngOnInit();
        expect(comp.getMinimumSizeLabel()).toBe('artemisApp.plagiarism.minimumSize');
    });

    it('should aggregate aggregate input changes', () => {
        fixture.componentRef.setInput('exercise', {
            plagiarismDetectionConfig: {
                continuousPlagiarismControlEnabled: false,
                continuousPlagiarismControlPostDueDateChecksEnabled: false,
            },
        } as Exercise);

        comp.ngOnInit();
        comp.form.patchValue({ continuousPlagiarismControlEnabled: true });
        comp.form.patchValue({
            continuousPlagiarismControlPostDueDateChecksEnabled: true,
            similarityThreshold: 10,
            minimumScore: 10,
            minimumSize: 10,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 10,
        });

        fixture.detectChanges();

        expect(comp.isFormValid()).toBe(true);
    });

    it('should mark form invalid for out-of-range similarityThreshold', () => {
        comp.ngOnInit();

        // below range
        comp.form.patchValue({
            continuousPlagiarismControlEnabled: true,
            similarityThreshold: -1,
            minimumScore: 0,
            minimumSize: 0,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 7,
        });

        expect(comp.form.valid).toBe(false);
        expect(comp.isFormValid()).toBe(false);

        // above range
        comp.form.patchValue({
            similarityThreshold: 101,
        });

        expect(comp.form.valid).toBe(false);
        expect(comp.isFormValid()).toBe(false);
    });

    it('should mark form invalid for out-of-range minimumScore', () => {
        comp.ngOnInit();

        comp.form.patchValue({
            continuousPlagiarismControlEnabled: true,
            similarityThreshold: 0,
            minimumScore: -1,
            minimumSize: 0,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 7,
        });

        expect(comp.form.valid).toBe(false);
        expect(comp.isFormValid()).toBe(false);

        comp.form.patchValue({
            minimumScore: 101,
        });

        expect(comp.form.valid).toBe(false);
        expect(comp.isFormValid()).toBe(false);
    });

    it('should allow minimumSize to be large and disallow negative values', () => {
        comp.ngOnInit();

        comp.form.patchValue({ continuousPlagiarismControlEnabled: true });
        fixture.detectChanges();

        // negative not allowed
        comp.form.patchValue({
            similarityThreshold: 0,
            minimumScore: 0,
            minimumSize: -1,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 7,
        });
        fixture.detectChanges();

        expect(comp.form.valid).toBe(false);
        expect(comp.isFormValid()).toBe(false);

        // large value (e.g. > 100) is allowed
        comp.form.patchValue({
            minimumSize: 101,
        });
        fixture.detectChanges();

        expect(comp.form.valid).toBe(true);
        expect(comp.isFormValid()).toBe(true);
    });

    it('should mark form invalid for out-of-range response period', () => {
        comp.ngOnInit();

        comp.form.patchValue({
            continuousPlagiarismControlEnabled: true,
            similarityThreshold: 0,
            minimumScore: 0,
            minimumSize: 0,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 6, // below min 7
        });

        expect(comp.form.valid).toBe(false);
        expect(comp.isFormValid()).toBe(false);

        comp.form.patchValue({
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 32, // above max 31
        });

        expect(comp.form.valid).toBe(false);
        expect(comp.isFormValid()).toBe(false);
    });

    it('should propagate form values into exercise.plagiarismDetectionConfig', () => {
        const initialExercise: Exercise = {
            ...comp.exercise(),
            plagiarismDetectionConfig: { ...DEFAULT_PLAGIARISM_DETECTION_CONFIG },
        } as Exercise;
        fixture.componentRef.setInput('exercise', initialExercise);

        comp.ngOnInit();

        comp.form.patchValue({
            continuousPlagiarismControlEnabled: true,
        });

        expect(comp.exercise()?.plagiarismDetectionConfig?.continuousPlagiarismControlEnabled).toBe(true);

        comp.form.patchValue({
            similarityThreshold: 42,
        });

        expect(comp.exercise()?.plagiarismDetectionConfig?.similarityThreshold).toBe(42);

        comp.form.patchValue({
            minimumScore: 13,
        });

        expect(comp.exercise()?.plagiarismDetectionConfig?.minimumScore).toBe(13);
    });

    it('should disable CPC-related controls when CPC is disabled', () => {
        comp.ngOnInit();

        comp.form.patchValue({ continuousPlagiarismControlEnabled: true });
        comp.form.patchValue({
            continuousPlagiarismControlPostDueDateChecksEnabled: true,
            similarityThreshold: 10,
            minimumScore: 10,
            minimumSize: 10,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 10,
        });

        comp.form.patchValue({ continuousPlagiarismControlEnabled: false });

        expect(comp.form.get('continuousPlagiarismControlPostDueDateChecksEnabled')?.disabled).toBe(true);
        expect(comp.form.get('similarityThreshold')?.disabled).toBe(true);
        expect(comp.form.get('minimumScore')?.disabled).toBe(true);
        expect(comp.form.get('minimumSize')?.disabled).toBe(true);
        expect(comp.form.get('continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod')?.disabled).toBe(true);
    });

    it('should accept boundary values for all fields', () => {
        comp.ngOnInit();

        comp.form.patchValue({ continuousPlagiarismControlEnabled: true });

        comp.form.patchValue({
            similarityThreshold: 0,
            minimumScore: 0,
            minimumSize: 0,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 7,
        });

        expect(comp.form.valid).toBe(true);

        fixture.detectChanges();
        expect(comp.isFormValid()).toBe(true);

        comp.form.patchValue({
            similarityThreshold: 100,
            minimumScore: 100,
            minimumSize: 1000, // large value allowed
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 31,
        });

        expect(comp.form.valid).toBe(true);
        fixture.detectChanges();
        expect(comp.isFormValid()).toBe(true);
    });

    it('should require all fields when CPC is enabled', () => {
        comp.ngOnInit();

        comp.form.patchValue({
            continuousPlagiarismControlEnabled: true,
            similarityThreshold: null,
            minimumScore: null,
            minimumSize: null,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: null,
        });

        expect(comp.form.valid).toBe(false);
        expect(comp.isFormValid()).toBe(false);
        expect(comp.form.get('similarityThreshold')?.hasError('required')).toBe(true);
        expect(comp.form.get('minimumScore')?.hasError('required')).toBe(true);
        expect(comp.form.get('minimumSize')?.hasError('required')).toBe(true);
        expect(comp.form.get('continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod')?.hasError('required')).toBe(true);
    });

    describe('integer validator', () => {
        it('should reject non-integer similarityThreshold', () => {
            comp.ngOnInit();
            comp.form.patchValue({ continuousPlagiarismControlEnabled: true });

            comp.form.patchValue({ similarityThreshold: 50.5 });

            expect(comp.form.get('similarityThreshold')?.hasError('notInteger')).toBe(true);
            expect(comp.form.valid).toBe(false);
        });

        it('should reject non-integer minimumScore', () => {
            comp.ngOnInit();
            comp.form.patchValue({ continuousPlagiarismControlEnabled: true });

            comp.form.patchValue({ minimumScore: 22.5 });

            expect(comp.form.get('minimumScore')?.hasError('notInteger')).toBe(true);
            expect(comp.form.valid).toBe(false);
        });

        it('should reject non-integer minimumSize', () => {
            comp.ngOnInit();
            comp.form.patchValue({ continuousPlagiarismControlEnabled: true });

            comp.form.patchValue({ minimumSize: 50.5 });

            expect(comp.form.get('minimumSize')?.hasError('notInteger')).toBe(true);
            expect(comp.form.valid).toBe(false);
        });

        it('should reject non-integer response period', () => {
            comp.ngOnInit();
            comp.form.patchValue({ continuousPlagiarismControlEnabled: true });

            comp.form.patchValue({ continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 7.5 });

            expect(comp.form.get('continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod')?.hasError('notInteger')).toBe(true);
            expect(comp.form.valid).toBe(false);
        });

        it('should accept integer values', () => {
            comp.ngOnInit();
            comp.form.patchValue({ continuousPlagiarismControlEnabled: true });

            comp.form.patchValue({
                similarityThreshold: 50,
                minimumScore: 22,
                minimumSize: 50,
                continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 7,
            });

            expect(comp.form.get('similarityThreshold')?.hasError('notInteger')).toBe(false);
            expect(comp.form.get('minimumScore')?.hasError('notInteger')).toBe(false);
            expect(comp.form.get('minimumSize')?.hasError('notInteger')).toBe(false);
            expect(comp.form.get('continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod')?.hasError('notInteger')).toBe(false);
            expect(comp.form.valid).toBe(true);
        });

        it('should return null for empty value (let required validator handle it)', () => {
            const result = ExerciseUpdatePlagiarismComponent.integerValidator({ value: null } as any);
            expect(result).toBeNull();

            const result2 = ExerciseUpdatePlagiarismComponent.integerValidator({ value: '' } as any);
            expect(result2).toBeNull();
        });
    });
});
