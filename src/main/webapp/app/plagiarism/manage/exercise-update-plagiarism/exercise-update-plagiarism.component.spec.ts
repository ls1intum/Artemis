import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Subject } from 'rxjs';
import { ExerciseUpdatePlagiarismComponent } from './exercise-update-plagiarism.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { NgModel } from '@angular/forms';

describe('Exercise Update Plagiarism Component', () => {
    let comp: ExerciseUpdatePlagiarismComponent;
    let fixture: ComponentFixture<ExerciseUpdatePlagiarismComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExerciseUpdatePlagiarismComponent, FontAwesomeTestingModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseUpdatePlagiarismComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('exercise', new ProgrammingExercise(undefined, undefined));
    });

    it('should use provided plagiarism checks config', () => {
        const plagiarismDetectionConfig = {
            continuousPlagiarismControlEnabled: true,
            continuousPlagiarismControlPostDueDateChecksEnabled: false,
            similarityThreshold: 1,
            minimumScore: 2,
            minimumSize: 3,
        };

        fixture.componentRef.setInput('exercise', { ...comp.exercise(), plagiarismDetectionConfig });

        comp.ngOnInit();

        expect(comp.exercise().plagiarismDetectionConfig).toEqual(plagiarismDetectionConfig);
    });

    it('should use default if exercise does not have plagiarism checks config', () => {
        fixture.componentRef.setInput('exercise', { ...comp.exercise(), plagiarismDetectionConfig: undefined });

        comp.ngOnInit();

        expect(comp.exercise().plagiarismDetectionConfig).toEqual(DEFAULT_PLAGIARISM_DETECTION_CONFIG);
    });

    it('should set minimumSizeTooltip on init', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.detectChanges();
        expect(comp.minimumSizeTooltip).toBe('artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise');
    });

    it('should set default plagiarism detection config on init if not set', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.detectChanges();
        expect(comp.exercise().plagiarismDetectionConfig).toEqual(DEFAULT_PLAGIARISM_DETECTION_CONFIG);
    });

    it('should enable cpc', () => {
        fixture.componentRef.setInput('exercise', {
            plagiarismDetectionConfig: {
                continuousPlagiarismControlEnabled: false,
                continuousPlagiarismControlPostDueDateChecksEnabled: false,
            },
        } as Exercise);
        fixture.detectChanges();
        comp.toggleCPCEnabled();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlEnabled).toBeTrue();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlPostDueDateChecksEnabled).toBeTrue();
    });

    it('should disable cpc', () => {
        fixture.componentRef.setInput('exercise', {
            plagiarismDetectionConfig: {
                continuousPlagiarismControlEnabled: true,
                continuousPlagiarismControlPostDueDateChecksEnabled: true,
            },
        } as Exercise);
        fixture.detectChanges();
        comp.toggleCPCEnabled();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlEnabled).toBeFalse();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlPostDueDateChecksEnabled).toBeFalse();
    });

    it('should get correct minimumSizeTooltip for programming exercises', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.detectChanges();
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise');
    });

    it('should get correct minimumSizeTooltip for text exercises', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as Exercise);
        fixture.detectChanges();
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipTextExercise');
    });

    it('should aggregate aggregate input changes', () => {
        const inputFieldNames = ['fieldCPCEnabled', 'fieldThreshold', 'fieldResponsePeriod', 'fieldMinScore', 'fieldMinSize'];
        const calculateValidSpy = jest.spyOn(comp, 'calculateFormValid');
        const formValidChangesSpy = jest.spyOn(comp.formValidChanges, 'next');
        fixture.componentRef.setInput('exercise', {
            plagiarismDetectionConfig: {
                continuousPlagiarismControlEnabled: false,
                continuousPlagiarismControlPostDueDateChecksEnabled: true,
            },
        } as Exercise);

        for (const fieldName of inputFieldNames) {
            (comp as any)[fieldName] = () => ({ valueChanges: new Subject<boolean>(), valid: false }) as unknown as NgModel;

            const stub = {
                valueChanges: new Subject<boolean>(),
                valid: false,
            } as unknown as NgModel;

            (comp as any)[fieldName] = () => stub;
        }
        comp.ngAfterViewInit();
        for (const fieldName of inputFieldNames) {
            expect(((comp as any)[fieldName]().valueChanges! as Subject<boolean>).observed).toBeTrue();
        }

        (comp.fieldCPCEnabled()!.valueChanges! as Subject<boolean>).next(false);
        expect(calculateValidSpy).toHaveBeenCalledOnce();
        expect(comp.formValid).toBeTrue();

        // @ts-ignore
        comp.fieldCPCEnabled()!.valid = true;
        comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlEnabled = true;
        (comp.fieldCPCEnabled()!.valueChanges! as Subject<boolean>).next(true);

        expect(calculateValidSpy).toHaveBeenCalledTimes(2);
        expect(comp.formValid).toBeFalse();
        expect(formValidChangesSpy).toHaveBeenCalledWith(false);

        comp.ngOnDestroy();
        for (const fieldName of inputFieldNames) {
            expect(((comp as any)[fieldName]().valueChanges! as Subject<boolean>).observed).toBeFalse();
        }
    });
});
