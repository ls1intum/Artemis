import { Component, OnDestroy, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { Duration, QuizExercise, QuizExercisePopupService, QuizExerciseService } from '../../entities/quiz-exercise';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Option } from '../../entities/quiz-exercise';
import { Question, QuestionType } from '../../entities/question';
import { QuizReEvaluateWarningComponent } from './quiz-re-evaluate-warning.component';
import { HttpResponse } from '@angular/common/http';
import * as moment from 'moment';

@Component({
    selector: 'jhi-quiz-re-evaluate',
    templateUrl: './quiz-re-evaluate.component.html',
    providers: []
})
export class QuizReEvaluateComponent implements OnInit, OnChanges, OnDestroy {
    // Make constants available to html for comparison
    readonly DRAG_AND_DROP = QuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuestionType.MULTIPLE_CHOICE;

    private subscription: Subscription;

    quizExercise: QuizExercise;
    modalService: NgbModal;
    popupService: QuizExercisePopupService;
    router: Router;

    datePickerOpenStatus = {
        releaseDate: false
    };
    isSaving: boolean;
    duration: Duration;
    // Create Backup Quiz for resets
    backupQuiz: QuizExercise;

    // Status options depending on relationship between start time, end time, and current time
    statusOptionsVisible: Option[] = [new Option(false, 'Hidden'), new Option(true, 'Visible')];
    statusOptionsPractice: Option[] = [new Option(false, 'Closed'), new Option(true, 'Open for Practice')];
    statusOptionsActive: Option[] = [new Option(true, 'Active')];

    constructor(
        private quizExerciseService: QuizExerciseService,
        private route: ActivatedRoute,
        private routerC: Router,
        private modalServiceC: NgbModal,
        private quizExercisePopupService: QuizExercisePopupService
    ) {}

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe(params => {
            this.quizExerciseService.find(params['id']).subscribe((response: HttpResponse<QuizExercise>) => {
                this.quizExercise = response.body;
                this.prepareEntity(this.quizExercise);
                this.backupQuiz = Object.assign({}, this.quizExercise);
            });
        });

        this.modalService = this.modalServiceC;
        this.popupService = this.quizExercisePopupService;
        this.router = this.routerC;

        /** Initialize constants **/
        this.isSaving = false;
        this.duration = new Duration(0, 0);
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.quizExercise && changes.quizExercise.currentValue != null) {
            this.prepareEntity(this.quizExercise);
            this.backupQuiz = Object.assign({}, this.quizExercise);
        }
    }

    /**
     * Remove question from the quiz
     * @param questionToBeDeleted {Question} the question to remove
     */
    deleteQuestion(questionToBeDeleted: Question) {
        this.quizExercise.questions = this.quizExercise.questions.filter(question => question !== questionToBeDeleted);
    }

    /**
     * Handles the change of a question by replacing the array with a copy
     *                                      (allows for shallow comparison)
     */
    onQuestionUpdated() {
        this.quizExercise.questions = Array.from(this.quizExercise.questions);
    }

    /**
     * Determine if there are any changes waiting to be saved
     * @returns {boolean} true if there are any pending changes, false otherwise
     */
    pendingChanges() {
        // TODO: old => return !angular.equals(this.quizExercise, this.backupQuiz);
        console.log('pending changes?', JSON.stringify(this.quizExercise).toLowerCase() === JSON.stringify(this.backupQuiz).toLowerCase());
        return JSON.stringify(this.quizExercise).toLowerCase() === JSON.stringify(this.backupQuiz).toLowerCase();
    }

    /**
     * Check if the current inputs are valid
     * @returns {boolean} true if valid, false otherwise
     */
    validQuiz() {
        return this.quizExercise && this.quizExercise.title && this.quizExercise.title !== '' && this.quizExercise.duration;
    }

    /**
     * Open Warning-Modal
     *  -> if confirmed: send changed quiz to server (in Modal-controller)
     *                              and go back to parent-template
     *  -> if canceled: close Modal
     */
    save() {
        this.popupService.open(QuizReEvaluateWarningComponent as Component, this.quizExercise);
    }

    back() {
        this.router.navigate(['/course', this.quizExercise.course.id, 'quiz-exercise']);
    }

    /**
     * Makes sure the quizExercise is well formed and its fields are of the correct types
     * @param quizExercise
     */
    prepareEntity(quizExercise: QuizExercise) {
        quizExercise.releaseDate = quizExercise.releaseDate ? quizExercise.releaseDate : moment();
        quizExercise.duration = Number(quizExercise.duration);
        quizExercise.duration = isNaN(quizExercise.duration) ? 10 : quizExercise.duration;
    }

    /**
     * Reach to changes of duration inputs by updating model and ui
     */
    onDurationChange() {
        const duration = moment.duration(this.duration);
        this.quizExercise.duration = Math.min(Math.max(duration.asSeconds(), 0), 10 * 60 * 60);
        this.updateDuration();
    }

    /**
     * update ui to current value of duration
     */
    updateDuration() {
        const duration = moment.duration(this.quizExercise.duration, 'seconds');
        this.duration.minutes = 60 * duration.hours() + duration.minutes();
        this.duration.seconds = duration.seconds();
    }

    /**
     * Gives the duration time in a String with this format: <minutes>:<seconds>
     *@returns {String} the duration as String
     */
    durationString() {
        if (this.duration.seconds <= 0) {
            return this.duration.minutes + ':00';
        }
        if (this.duration.seconds < 10) {
            return this.duration.minutes + ':0' + this.duration.seconds;
        }
        return this.duration.minutes + ':' + this.duration.seconds;
    }

    /**
     * Resets the whole Quiz
     */
    resetAll() {
        this.quizExercise = Object.assign({}, this.backupQuiz);
    }

    /**
     * Resets the quiz title
     */
    resetQuizTitle() {
        this.quizExercise.title = this.backupQuiz.title;
    }

    /**
     * @function moveUp
     * @desc Move the question one position up
     * @param question {Question} the question to move
     */
    moveUp(question: Question) {
        const index = this.quizExercise.questions.indexOf(question);
        if (index === 0) {
            return;
        }
        const tempQuestions = Object.assign({}, this.quizExercise.questions);
        const temp = tempQuestions[index];
        tempQuestions[index] = tempQuestions[index - 1];
        tempQuestions[index - 1] = temp;
        this.quizExercise.questions = tempQuestions;
    }

    /**
     * @function moveDown
     * @desc Move the question one position down
     * @param question {Question} the question to move
     */
    moveDown(question: Question) {
        const index = this.quizExercise.questions.indexOf(question);
        if (index === this.quizExercise.questions.length - 1) {
            return;
        }
        const tempQuestions = Object.assign({}, this.quizExercise.questions);
        const temp = tempQuestions[index];
        tempQuestions[index] = tempQuestions[index + 1];
        tempQuestions[index + 1] = temp;
        this.quizExercise.questions = tempQuestions;
    }

    ngOnDestroy(): void {
        this.subscription.unsubscribe();
    }
}
