import { QuizGroup } from 'app/quiz/shared/entities/quiz-group.model';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
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
