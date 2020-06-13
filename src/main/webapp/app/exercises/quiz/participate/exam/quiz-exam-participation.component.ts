import { Component, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { DeviceDetectorService } from 'ngx-device-detector';
import * as smoothscroll from 'smoothscroll-polyfill';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exam-quiz',
    templateUrl: './quiz-exam-participation.component.html',
    providers: [ParticipationService],
    styleUrls: ['./quiz-participation.component.scss'],
})
export class QuizExamParticipationComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly ButtonSize = ButtonSize;
    readonly ButtonType = ButtonType;

    @ViewChildren(MultipleChoiceQuestionComponent)
    mcQuestionComponents: QueryList<MultipleChoiceQuestionComponent>;

    @ViewChildren(DragAndDropQuestionComponent)
    dndQuestionComponents: QueryList<DragAndDropQuestionComponent>;

    @ViewChildren(ShortAnswerQuestionComponent)
    shortAnswerQuestionComponents: QueryList<ShortAnswerQuestionComponent>;

    private subscription: Subscription;

    quizExercise: QuizExercise;
    quizId: number;
    courseId: number;
    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

    constructor(private deviceService: DeviceDetectorService, private route: ActivatedRoute) {
        smoothscroll.polyfill();
    }

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe((params) => {
            this.quizId = Number(params['exerciseId']);
            this.courseId = Number(params['courseId']);
        });
    }

    ngOnDestroy(): void {
        this.subscription.unsubscribe();
    }

    /**
     * By clicking on the bubble of the progress navigation towards the corresponding question of the quiz is triggered
     * @param questionIndex
     */
    navigateToQuestion(questionIndex: number): void {
        document.getElementById('question' + questionIndex)!.scrollIntoView({
            behavior: 'smooth',
        });
    }

    /**
     * Determines if the current device is a mobile device
     */
    isMobile(): boolean {
        return this.deviceService.isMobile();
    }
}
