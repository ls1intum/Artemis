import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseUpdatePlagiarismComponent } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

describe('Exercise Update Plagiarism Component', () => {
    let comp: ExerciseUpdatePlagiarismComponent;

    beforeEach(() => {
        comp = new ExerciseUpdatePlagiarismComponent();

        comp.exercise = new ProgrammingExercise(undefined, undefined);
    });

    it('should use provided plagiarism checks config', () => {
        const plagiarismDetectionConfig = {
            continuousPlagiarismControlEnabled: true,
            continuousPlagiarismControlPostDueDateChecksEnabled: false,
            similarityThreshold: 1,
            minimumScore: 2,
            minimumSize: 3,
        };
        comp.exercise.plagiarismDetectionConfig = plagiarismDetectionConfig;

        comp.ngOnInit();

        expect(comp.exercise.plagiarismDetectionConfig).toEqual(plagiarismDetectionConfig);
    });

    it('should use default if exercise does not have plagiarism checks config', () => {
        comp.exercise.plagiarismDetectionConfig = undefined;

        comp.ngOnInit();

        expect(comp.exercise.plagiarismDetectionConfig).toEqual(DEFAULT_PLAGIARISM_DETECTION_CONFIG);
    });

    it('should set minimumSizeTooltip on init', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
        comp.ngOnInit();
        expect(comp.minimumSizeTooltip).toBe('artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise');
    });

    it('should set default plagiarism detection config on init if not set', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
        comp.ngOnInit();
        expect(comp.exercise.plagiarismDetectionConfig).toEqual(DEFAULT_PLAGIARISM_DETECTION_CONFIG);
    });

    it('should enable cpc', () => {
        comp.exercise = {
            plagiarismDetectionConfig: { continuousPlagiarismControlEnabled: false, continuousPlagiarismControlPostDueDateChecksEnabled: false },
        } as Exercise;
        comp.toggleCPCEnabled();
        expect(comp.exercise.plagiarismDetectionConfig!.continuousPlagiarismControlEnabled).toBeTrue();
        expect(comp.exercise.plagiarismDetectionConfig!.continuousPlagiarismControlPostDueDateChecksEnabled).toBeTrue();
    });

    it('should disable cpc', () => {
        comp.exercise = {
            plagiarismDetectionConfig: { continuousPlagiarismControlEnabled: true, continuousPlagiarismControlPostDueDateChecksEnabled: true },
        } as Exercise;
        comp.toggleCPCEnabled();
        expect(comp.exercise.plagiarismDetectionConfig!.continuousPlagiarismControlEnabled).toBeFalse();
        expect(comp.exercise.plagiarismDetectionConfig!.continuousPlagiarismControlPostDueDateChecksEnabled).toBeFalse();
    });

    it('should get correct minimumSizeTooltip for programming exercises', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise');
    });

    it('should get correct minimumSizeTooltip for text exercises', () => {
        comp.exercise = { type: ExerciseType.TEXT } as Exercise;
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipTextExercise');
    });

    it('should get correct minimumSizeTooltip for modeling exercises', () => {
        comp.exercise = { type: ExerciseType.MODELING } as Exercise;
        expect(comp.getMinimumSizeTooltip()).toBe('artemisApp.plagiarism.minimumSizeTooltipModelingExercise');
    });
});
