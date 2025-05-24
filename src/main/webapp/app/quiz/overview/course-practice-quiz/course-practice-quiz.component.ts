import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, combineLatest, of } from 'rxjs';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { CoursePracticeQuizService } from 'app/quiz/overview/service/course-practice-quiz.service';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-course-practice-quiz',
    imports: [MultipleChoiceQuestionComponent, ShortAnswerQuestionComponent, DragAndDropQuestionComponent, ButtonComponent],
    templateUrl: './course-practice-quiz.component.html',
    styleUrl: './course-practice-quiz.component.scss',
})
export class CoursePracticeQuizComponent implements OnInit, OnDestroy {
    @Input() questions: QuizQuestion[] = [];

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private quizService = inject(CoursePracticeQuizService);
    private subscription: Subscription;

    courseId: number;
    currentIndex = 0;
    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

    ngOnInit(): void {
        this.subscription = combineLatest([this.route.parent?.params ?? of({ courseId: undefined })]).subscribe(([params]) => {
            this.courseId = params['courseId'];
            this.loadQuestions(this.courseId);
        });
    }

    ngOnDestroy(): void {
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
    }

    /**
     * loads the quiz questions for the given course
     * @param courseId
     */
    loadQuestions(courseId: number): void {
        this.quizService.getQuizQuestions(courseId).subscribe((questions) => {
            this.questions = questions;
        });
    }

    /**
     * increments the current question index or navigates to the course practice page if the last question is reached
     */
    nextQuestion(): void {
        if (this.isLastQuestion) {
            this.navigateToPractice();
        } else {
            this.currentIndex++;
        }
    }

    /**
     * checks if the current question is the last question
     */
    get isLastQuestion(): boolean {
        if (this.questions.length === 0) {
            return true;
        }
        return this.currentIndex === this.questions.length - 1;
    }

    /**
     * gets the current question
     */
    get currentQuestion(): QuizQuestion {
        if (this.questions.length === 0 || this.currentIndex < 0 || this.currentIndex >= this.questions.length) {
            throw new Error('No questions available or invalid question index');
        }
        return this.questions[this.currentIndex];
    }

    /**
     * navigates to the course practice page
     */
    navigateToPractice(): void {
        this.router.navigate(['courses', this.courseId, 'practice']);
    }
}
