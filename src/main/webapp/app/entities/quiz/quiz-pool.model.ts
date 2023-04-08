import { QuizGroup } from 'app/entities/quiz/quiz-group.model';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { Exam } from 'app/entities/exam.model';

export class QuizPool {
    public id?: number;
    public exam: Exam;
    public quizGroups: QuizGroup[] = [];
    public quizQuestions: QuizQuestion[] = [];
    public maxPoints: number;
}
