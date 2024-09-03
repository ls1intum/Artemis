import { QuizGroup } from 'app/entities/quiz/quiz-group.model';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { Exam } from 'app/entities/exam/exam.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class QuizPool implements BaseEntity {
    id?: number;
    exam: Exam;
    quizGroups: QuizGroup[] = [];
    quizQuestions: QuizQuestion[] = [];
    maxPoints = 0;
    randomizeQuestionOrder = false;
}
