import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { CoursePracticeQuizService } from 'app/quiz/overview/service/course-practice-quiz.service';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { toSignal } from '@angular/core/rxjs-interop';

@Component({
    selector: 'jhi-course-practice-quiz',
    imports: [MultipleChoiceQuestionComponent, ShortAnswerQuestionComponent, DragAndDropQuestionComponent, ButtonComponent],
    templateUrl: './course-practice-quiz.component.html',
})
export class CoursePracticeQuizComponent {
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private quizService = inject(CoursePracticeQuizService);

    currentIndex = signal(0);

    // Reactive chain for loading quiz questions based on the current route
    paramsSignal = toSignal(this.route.parent?.params!);
    courseId = computed(() => this.paramsSignal()?.['courseId']);
    questionsSignal = toSignal(this.quizService.getQuizQuestions(this.courseId())!, { initialValue: [] });
    questions = computed(() => this.questionsSignal());

    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

    /**
     * checks if the current question is the last question
     */
    isLastQuestion = computed(() => {
        if (this.questions().length === 0) {
            return true;
        }
        return this.currentIndex() === this.questions().length - 1;
    });

    /**
     * gets the current question
     */
    currentQuestion = computed(() => {
        if (this.questions().length === 0) {
            return undefined;
        }
        return this.questions()[this.currentIndex()];
    });

    /**
     * increments the current question index or navigates to the course practice page if the last question is reached
     */
    nextQuestion(): void {
        if (this.isLastQuestion()) {
            this.navigateToPractice();
        } else {
            this.currentIndex.set(this.currentIndex() + 1);
        }
    }

    /**
     * navigates to the course practice page
     */
    navigateToPractice(): void {
        this.router.navigate(['courses', this.courseId(), 'practice']);
    }
}
