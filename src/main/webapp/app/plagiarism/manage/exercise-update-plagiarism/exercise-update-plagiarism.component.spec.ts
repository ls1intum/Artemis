import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TranslateService } from '@ngx-translate/core';

import { ExerciseUpdatePlagiarismComponent } from './exercise-update-plagiarism.component';
import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Exercise Update Plagiarism Component', () => {
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
        fixture.componentRef.setInput('exercise', Object.assign({}, comp.exercise(), { plagiarismDetectionConfig: cfg }));

        comp.ngOnInit();

        expect(comp.exercise()?.plagiarismDetectionConfig).toEqual(cfg);
    });

    it('should use default if exercise does not have plagiarism checks config', () => {
        fixture.componentRef.setInput('exercise', Object.assign({}, comp.exercise(), { plagiarismDetectionConfig: undefined }));
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
        expect(comp.form.get('continuousPlagiarismControlEnabled')?.value).toBeFalse();

        comp.form.patchValue({ continuousPlagiarismControlEnabled: true });
        expect(comp.form.get('continuousPlagiarismControlEnabled')?.value).toBeTrue();
        expect(comp.form.get('continuousPlagiarismControlPostDueDateChecksEnabled')?.enabled).toBeTrue();
    });

    it('should disable cpc', () => {
        comp.form.patchValue({ continuousPlagiarismControlEnabled: true });
        expect(comp.form.get('continuousPlagiarismControlEnabled')?.value).toBeTrue();

        comp.form.patchValue({ continuousPlagiarismControlEnabled: false });
        expect(comp.form.get('continuousPlagiarismControlEnabled')?.value).toBeFalse();
        expect(comp.form.get('continuousPlagiarismControlPostDueDateChecksEnabled')?.disabled).toBeTrue();
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

        expect(comp.isFormValid()).toBeTrue();
    });
});
