import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseUpdatePlagiarismComponent } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Subject } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';

describe('Exercise Update Plagiarism Component', () => {
    let comp: ExerciseUpdatePlagiarismComponent;
    let fixture: ComponentFixture<ExerciseUpdatePlagiarismComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ExerciseUpdatePlagiarismComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseUpdatePlagiarismComponent);
        comp = fixture.componentInstance;
        comp.exercise.set(new ProgrammingExercise(undefined, undefined));
    });

    it('should use provided plagiarism checks config', () => {
        const plagiarismDetectionConfig = {
            continuousPlagiarismControlEnabled: true,
            continuousPlagiarismControlPostDueDateChecksEnabled: false,
            similarityThreshold: 1,
            minimumScore: 2,
            minimumSize: 3,
        };
        comp.exercise.update((exercise) => {
            exercise.plagiarismDetectionConfig = plagiarismDetectionConfig;
            return exercise;
        });

        comp.ngOnInit();

        expect(comp.exercise().plagiarismDetectionConfig).toEqual(plagiarismDetectionConfig);
    });

    it('should use default if exercise does not have plagiarism checks config', () => {
        comp.exercise.update((exercise) => {
            exercise.plagiarismDetectionConfig = undefined;
            return exercise;
        });

        comp.ngOnInit();

        expect(comp.exercise().plagiarismDetectionConfig).toEqual(DEFAULT_PLAGIARISM_DETECTION_CONFIG);
    });

    it('should set minimumSizeTooltip on init', () => {
        comp.exercise.update((exercise) => {
            exercise.type = ExerciseType.PROGRAMMING;
            return exercise;
        });
        comp.ngOnInit();
        expect(comp.minimumSizeTooltip).toBe('artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise');
    });

    it('should set default plagiarism detection config on init if not set', () => {
        comp.exercise.update((exercise) => {
            exercise.type = ExerciseType.PROGRAMMING;
            return exercise;
        });
        comp.ngOnInit();
        expect(comp.exercise().plagiarismDetectionConfig).toEqual(DEFAULT_PLAGIARISM_DETECTION_CONFIG);
    });

    it('should enable cpc', () => {
        comp.exercise.update((exercise) => {
            exercise.plagiarismDetectionConfig = { continuousPlagiarismControlEnabled: false, continuousPlagiarismControlPostDueDateChecksEnabled: false };
            return exercise;
        });
        comp.toggleCPCEnabled();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlEnabled).toBeTrue();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlPostDueDateChecksEnabled).toBeTrue();
    });

    it('should disable cpc', () => {
        comp.exercise.update((exercise) => {
            exercise.plagiarismDetectionConfig = { continuousPlagiarismControlEnabled: true, continuousPlagiarismControlPostDueDateChecksEnabled: true };
            return exercise;
        });
        comp.toggleCPCEnabled();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlEnabled).toBeFalse();
        expect(comp.exercise().plagiarismDetectionConfig!.continuousPlagiarismControlPostDueDateChecksEnabled).toBeFalse();
    });

    it('should get correct minimumSizeTooltip for programming exercises', () => {
        comp.exercise.update((exercise) => {
            exercise.type = ExerciseType.PROGRAMMING;
            return exercise;
        });
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise');
    });

    it('should get correct minimumSizeTooltip for text exercises', () => {
        comp.exercise.update((exercise) => {
            exercise.type = ExerciseType.TEXT;
            return exercise;
        });
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipTextExercise');
    });

    it('should get correct minimumSizeTooltip for modeling exercises', () => {
        comp.exercise.update((exercise) => {
            exercise.type = ExerciseType.MODELING;
            return exercise;
        });
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipModelingExercise');
    });

    it('should aggregate aggregate input changes', () => {
        const inputFieldNames = ['fieldCPCEnabled', 'fieldThreshhold', 'fieldResponsePeriod', 'fieldMinScore', 'fieldMinSize'];
        const calculateValidSpy = jest.spyOn(comp, 'calculateFormValid');
        const formValidChangesSpy = jest.spyOn(comp.formValidChanges, 'next');
        comp.exercise.update((exercise) => {
            exercise.plagiarismDetectionConfig = { continuousPlagiarismControlEnabled: false, continuousPlagiarismControlPostDueDateChecksEnabled: true };
            return exercise;
        });

        // initialize
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
        comp.exercise.update((exercise) => {
            if (exercise.plagiarismDetectionConfig) {
                exercise.plagiarismDetectionConfig.continuousPlagiarismControlEnabled = true;
            }
            return exercise;
        });
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
