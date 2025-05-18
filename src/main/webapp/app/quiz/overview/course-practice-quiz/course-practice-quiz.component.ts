import { Component, Input, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { CoursePracticeQuizService } from 'app/quiz/overview/course-practice-quiz/course-practice-quiz.service';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';

@Component({
    selector: 'jhi-course-practice-quiz',
    imports: [MultipleChoiceQuestionComponent, ShortAnswerQuestionComponent, DragAndDropQuestionComponent],
    templateUrl: './course-practice-quiz.component.html',
    styleUrl: './course-practice-quiz.component.scss',
})
export class CoursePracticeQuizComponent implements OnInit {
    @Input() questions: QuizQuestion[] = [];

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    private route = inject(ActivatedRoute);
    private quizService = inject(CoursePracticeQuizService);

    courseId: number;
    currentIndex = 0;
    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

    ngOnInit(): void {
        this.route.parent?.params.subscribe((params) => {
            this.courseId = params['courseId'];
            this.loadQuestions(this.courseId);
        });
    }

    loadQuestions(courseId: number): void {
        this.quizService.getQuizQuestions(courseId).subscribe((questions) => {
            this.questions = questions;
        });
    }

    nextQuestion(): void {
        if (!this.isLastQuestion) {
            this.currentIndex++;
        }
    }

    get isLastQuestion(): boolean {
        return this.currentIndex === this.questions.length - 1;
    }

    get currentQuestion(): QuizQuestion {
        return this.questions[this.currentIndex];
    }
}
