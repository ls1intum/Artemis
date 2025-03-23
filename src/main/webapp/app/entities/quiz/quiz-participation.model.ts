import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { StudentParticipation } from 'app/exercise/entities/participation/student-participation.model';

export interface QuizParticipation {
    quizQuestions?: QuizQuestion[];
    studentParticipations?: StudentParticipation[];
}
