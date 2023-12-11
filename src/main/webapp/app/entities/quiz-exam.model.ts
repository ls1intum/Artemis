import { ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { QuizConfiguration } from 'app/entities/quiz/quiz-configuration.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { QuizParticipation } from 'app/entities/quiz/quiz-participation.model';

export class QuizExam implements QuizParticipation, QuizConfiguration {
    public id?: number;
    public type?: ExerciseType;
    public studentParticipations?: StudentParticipation[];
    public exerciseGroup?: ExerciseGroup;
    public quizQuestions?: QuizQuestion[];
    public randomizeQuestionOrder?: boolean;
    public title?: string;
    public maxPoints?: number;
    public includedInOverallScore?: IncludedInOverallScore;

    constructor() {
        this.id = 0;
        this.type = ExerciseType.QUIZ;
        const submission = new QuizSubmission();
        submission.isSynced = true;
        this.studentParticipations = [
            {
                initializationState: InitializationState.INITIALIZED,
                submissions: [submission],
            },
        ];
        this.exerciseGroup = new ExerciseGroup();
        this.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
    }
}
