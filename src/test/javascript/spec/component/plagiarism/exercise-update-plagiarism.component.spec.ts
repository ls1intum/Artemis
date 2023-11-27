import { defaultPlagiarismDetectionConfig } from 'app/entities/exercise.model';
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

        expect(comp.exercise.plagiarismDetectionConfig).toEqual(defaultPlagiarismDetectionConfig);
    });
});
