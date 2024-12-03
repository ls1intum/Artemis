import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseUpdatePlagiarismComponent } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { Subject } from 'rxjs';
import { ComponentRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { TranslateDirective } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from 'app/forms/forms.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('Exercise Update Plagiarism Component', () => {
    let fixture: ComponentFixture<ExerciseUpdatePlagiarismComponent>;
    let comp: ExerciseUpdatePlagiarismComponent;
    let compRef: ComponentRef<ExerciseUpdatePlagiarismComponent>;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ExerciseUpdatePlagiarismComponent],
        })
            .overrideComponent(ExerciseUpdatePlagiarismComponent, {
                remove: {
                    imports: [TranslateDirective, FaIconComponent, NgbTooltip, FormsModule, ArtemisTranslatePipe],
                },
                add: {
                    imports: [
                        MockDirective(TranslateDirective),
                        MockComponent(FaIconComponent),
                        MockDirective(NgbTooltip),
                        MockModule(FormsModule),
                        MockPipe(ArtemisTranslatePipe),
                    ],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseUpdatePlagiarismComponent);

                const exercise = new ProgrammingExercise(undefined, undefined);
                fixture.componentRef.setInput('exercise', exercise);
                compRef = fixture.componentRef;
                comp = fixture.componentRef.instance;
            });
    });

    it('should use provided plagiarism checks config', () => {
        const plagiarismDetectionConfig = {
            continuousPlagiarismControlEnabled: true,
            continuousPlagiarismControlPostDueDateChecksEnabled: false,
            similarityThreshold: 1,
            minimumScore: 2,
            minimumSize: 3,
        };
        const exercise = comp.exercise();
        exercise.plagiarismDetectionConfig = plagiarismDetectionConfig;
        compRef.setInput('exercise', exercise);

        comp.ngOnInit();

        expect(comp.exercise().plagiarismDetectionConfig).toEqual(plagiarismDetectionConfig);
    });

    it('should use default if exercise does not have plagiarism checks config', () => {
        comp.exercise().plagiarismDetectionConfig = undefined;

        comp.ngOnInit();

        expect(comp.exercise().plagiarismDetectionConfig).toEqual(DEFAULT_PLAGIARISM_DETECTION_CONFIG);
    });

    it('should set minimumSizeTooltip on init', () => {
        const exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
        compRef.setInput('exercise', exercise);
        comp.ngOnInit();
        expect(comp.minimumSizeTooltip).toBe('artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise');
    });

    it('should set default plagiarism detection config on init if not set', () => {
        const exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
        compRef.setInput('exercise', exercise);
        comp.ngOnInit();
        expect(comp.exercise().plagiarismDetectionConfig).toEqual(DEFAULT_PLAGIARISM_DETECTION_CONFIG);
    });

    it('should enable cpc', () => {
        const exercise = {
            plagiarismDetectionConfig: {
                continuousPlagiarismControlEnabled: false,
                continuousPlagiarismControlPostDueDateChecksEnabled: false,
            },
        } as Exercise;
        compRef.setInput('exercise', exercise);
        comp.toggleCPCEnabled();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlEnabled).toBeTrue();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlPostDueDateChecksEnabled).toBeTrue();
    });

    it('should disable cpc', () => {
        const exercise = {
            plagiarismDetectionConfig: {
                continuousPlagiarismControlEnabled: true,
                continuousPlagiarismControlPostDueDateChecksEnabled: true,
            },
        } as Exercise;
        compRef.setInput('exercise', exercise);
        comp.toggleCPCEnabled();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlEnabled).toBeFalse();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlPostDueDateChecksEnabled).toBeFalse();
    });

    it('should get correct minimumSizeTooltip for programming exercises', () => {
        const exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
        compRef.setInput('exercise', exercise);
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise');
    });

    it('should get correct minimumSizeTooltip for text exercises', () => {
        const exercise = { type: ExerciseType.TEXT } as Exercise;
        compRef.setInput('exercise', exercise);
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipTextExercise');
    });

    it('should get correct minimumSizeTooltip for modeling exercises', () => {
        const exercise = { type: ExerciseType.MODELING } as Exercise;
        compRef.setInput('exercise', exercise);
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipModelingExercise');
    });

    it('should aggregate aggregate input changes', () => {
        const inputFieldNames = ['fieldCPCEnabled', 'fieldThreshhold', 'fieldResponsePeriod', 'fieldMinScore', 'fieldMinSize'];
        const calculateValidSpy = jest.spyOn(comp, 'calculateFormValid');
        const formValidChangesSpy = jest.spyOn(comp.formValidChanges, 'next');
        const exercise = {
            plagiarismDetectionConfig: {
                continuousPlagiarismControlEnabled: false,
                continuousPlagiarismControlPostDueDateChecksEnabled: true,
            },
        } as Exercise;
        compRef.setInput('exercise', exercise);

        for (const fieldName of inputFieldNames) {
            (comp as any)[fieldName] = { valueChanges: new Subject(), valid: false };
        }
        comp.ngAfterViewInit();
        for (const fieldName of inputFieldNames) {
            expect(((comp as any)[fieldName].valueChanges! as Subject<boolean>).observed).toBeTrue();
        }

        (comp.fieldCPCEnabled!.valueChanges! as Subject<boolean>).next(false);
        expect(calculateValidSpy).toHaveBeenCalledOnce();
        expect(comp.formValid).toBeTrue();

        // @ts-ignore
        comp.fieldCPCEnabled!.valid = true;
        comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlEnabled = true;
        (comp.fieldCPCEnabled!.valueChanges! as Subject<boolean>).next(true);

        expect(calculateValidSpy).toHaveBeenCalledTimes(2);
        expect(comp.formValid).toBeFalse();
        expect(formValidChangesSpy).toHaveBeenCalledWith(false);

        comp.ngOnDestroy();
        for (const fieldName of inputFieldNames) {
            expect(((comp as any)[fieldName].valueChanges! as Subject<boolean>).observed).toBeFalse();
        }
    });
});
