import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

export interface QuizParticipation {
    quizQuestions?: QuizQuestion[];
    studentParticipations?: StudentParticipation[];
}
