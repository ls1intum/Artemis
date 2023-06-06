import { QuizGroup } from 'app/entities/quiz/quiz-group.model';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { Exam } from 'app/entities/exam.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class QuizPool implements BaseEntity {
    public id?: number;
    public exam: Exam;
    public quizGroups: QuizGroup[] = [];
    public quizQuestions: QuizQuestion[] = [];
    public maxPoints = 0;
    public randomizeQuestionOrder = false;
}
