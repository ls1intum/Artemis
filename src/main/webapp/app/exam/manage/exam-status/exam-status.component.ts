import { Component, OnDestroy, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { faArrowRight, faCheckCircle, faCircleExclamation, faDotCircle, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { Exam, hasTestExamMode, isActingAsTestExam } from 'app/exam/shared/entities/exam.model';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { ExamChecklist } from 'app/exam/shared/entities/exam-checklist.model';
import dayjs from 'dayjs/esm';
import { round } from 'app/foundation/util/utils';
import { Course } from 'app/course/shared/entities/course.model';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Subscription } from 'rxjs';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';

export enum ExamReviewState {
    UNSET = 'unset',
    PLANNED = 'planned',
    RUNNING = 'running',
    FINISHED = 'finished',
}

export enum ExamConductionState {
    PLANNED,
    RUNNING,
    FINISHED,
    ERROR,
}

@Component({
    selector: 'jhi-exam-status',
    templateUrl: './exam-status.component.html',
    styleUrls: ['./exam-status.component.scss'],
    imports: [NgClass, FaIconComponent, TranslateDirective, ArtemisDatePipe, ArtemisTranslatePipe, ArtemisDurationFromSecondsPipe],
})
export class ExamStatusComponent implements OnInit, OnDestroy {
    private examChecklistService = inject(ExamChecklistService);
    private websocketService = inject(WebsocketService);

    public exam = input.required<Exam>();
    public course = input.required<Course>();

    constructor() {
        effect(() => {
            this.updateChecklistState();
        });
    }

    readonly examChecklist = signal<ExamChecklist>(undefined!);
    readonly numberOfGeneratedStudentExams = signal<number>(0);

    readonly configuredExercises = signal<boolean>(false);
    readonly registeredStudents = signal<boolean>(false);
    readonly generatedStudentExams = signal<boolean>(false);
    readonly preparedExerciseStart = signal<boolean>(false);

    // all steps for the preparation finished
    readonly examPreparationFinished = signal(false);
    // All mandatory steps for the preparation finished
    readonly mandatoryPreparationFinished = signal(false);
    readonly examConductionState = signal<ExamConductionState>(undefined!);
    readonly examReviewState = signal<ExamReviewState>(undefined!);
    readonly examCorrectionState = signal<ExamReviewState>(undefined!);

    readonly isActingAsTestExam = computed(() => isActingAsTestExam(this.exam()));
    readonly isTestExamMode = computed(() => hasTestExamMode(this.exam()));
    readonly maxPointExercises = signal<number>(0);

    readonly examConductionStateEnum = ExamConductionState;
    readonly examReviewStateEnum = ExamReviewState;
    readonly round = round;
    readonly Math = Math;

    readonly numberOfSubmitted = signal(0);
    readonly numberOfStarted = signal(0);
    private submittedSubscription?: Subscription;
    private startedSubscription?: Subscription;

    // Icons
    faTimes = faTimes;
    faTimesCircle = faTimesCircle;
    faCheckCircle = faCheckCircle;
    faArrowRight = faArrowRight;
    faDotCircle = faDotCircle;
    faCircleExclamation = faCircleExclamation;

    ngOnInit() {
        const submittedTopic = this.examChecklistService.getSubmittedTopic(this.exam());
        this.submittedSubscription = this.websocketService.subscribe<void>(submittedTopic).subscribe(() => this.numberOfSubmitted.update((count) => count + 1));
        const startedTopic = this.examChecklistService.getStartedTopic(this.exam());
        this.startedSubscription = this.websocketService.subscribe<void>(startedTopic).subscribe(() => this.numberOfStarted.update((count) => count + 1));
    }

    private updateChecklistState() {
        this.examChecklistService.getExamStatistics(this.exam()).subscribe((examStats) => {
            this.examChecklist.set(examStats);
            this.numberOfGeneratedStudentExams.set(this.examChecklist().numberOfGeneratedStudentExams ?? 0);

            if (this.course()?.isAtLeastInstructor) {
                // Step 1:
                this.setExamPreparation();
            }

            // Step 2: Exam conduction
            this.setConductionState();

            if (!this.isTestExamMode()) {
                // Step 3: Exam correction
                this.setReviewState();
                this.setCorrectionState();
            }

            this.numberOfStarted.set(this.examChecklist().numberOfExamsStarted);
            this.numberOfSubmitted.set(this.examChecklist().numberOfExamsSubmitted);
        });
    }

    ngOnDestroy(): void {
        this.submittedSubscription?.unsubscribe();
        this.startedSubscription?.unsubscribe();
    }

    /**
     * Auxiliary method that determines if all configuration steps for the exam exercises are finished
     * @returns boolean indicating whether configuration is finished
     */
    private areAllExercisesConfigured(): boolean {
        const atLeastOneGroup = this.examChecklistService.checkAtLeastOneExerciseGroup(this.exam());
        const numberOfExercisesEqual = this.examChecklistService.checkNumberOfExerciseGroups(this.exam());
        const noEmptyExerciseGroup = this.examChecklistService.checkEachGroupContainsExercise(this.exam());
        const maximumPointsEqual = this.examChecklistService.checkPointsExercisesEqual(this.exam());
        let examPointsReachable;
        if (this.isTestExamMode()) {
            // This method is called here, as it is part of the exercise configuration - although it is a separate entry to highlight the importance
            this.maxPointExercises.set(this.examChecklistService.calculateExercisePoints(maximumPointsEqual, this.exam()));
            examPointsReachable = this.exam().examMaxPoints === this.maxPointExercises();
        } else {
            examPointsReachable = this.examChecklistService.checkTotalPointsMandatory(maximumPointsEqual, this.exam());
        }

        return atLeastOneGroup && noEmptyExerciseGroup && numberOfExercisesEqual && maximumPointsEqual && examPointsReachable;
    }

    /**
     * Auxiliary method indicating whether all steps of Exam preparation are done
     */
    private isExamPreparationFinished(): boolean {
        if (this.isActingAsTestExam()) {
            // For test exam, only the exerciseConfiguration needs to be performed by the instructor
            return this.configuredExercises();
        } else {
            return this.configuredExercises() && this.registeredStudents() && this.generatedStudentExams() && this.preparedExerciseStart();
        }
    }

    /**
     * Helper method to indicate weather the mandatory preparation steps are performed in order to display a warning in the status.
     * (PrepareExerciseStart is not mandatory, but highly recommended)
     */
    private isMandatoryPreparationFinished(): boolean {
        if (this.isActingAsTestExam()) {
            return this.configuredExercises();
        } else {
            return this.configuredExercises() && this.registeredStudents() && this.generatedStudentExams();
        }
    }

    /**
     * Sets the conductionState according to the current situation
     */
    private setConductionState(): void {
        // In case the exercise configuration is wrong, but the (Test)Exam already started, students are not able to start a test eam or real exam
        // The ERROR-State should only be visible to Instructors, as editors & TAs have no access to the required data to determine if the preparation is finished
        const course = this.course();
        if (course?.isAtLeastInstructor && this.examAlreadyStarted() && !this.mandatoryPreparationFinished()) {
            this.examConductionState.set(ExamConductionState.ERROR);
        } else if (this.examAlreadyEnded() && ((course && !course.isAtLeastInstructor) || this.examPreparationFinished())) {
            this.examConductionState.set(ExamConductionState.FINISHED);
        } else if (this.examAlreadyStarted() && !this.examAlreadyEnded()) {
            this.examConductionState.set(ExamConductionState.RUNNING);
        } else {
            this.examConductionState.set(ExamConductionState.PLANNED);
        }
    }

    /**
     * Sets the reviewState according to the current situation
     */
    private setReviewState(): void {
        if (!this.exam().examStudentReviewEnd) {
            this.examReviewState.set(ExamReviewState.UNSET);
        } else if (this.isExamReviewPlanned()) {
            this.examReviewState.set(ExamReviewState.PLANNED);
        } else if (this.isExamReviewRunning()) {
            this.examReviewState.set(ExamReviewState.RUNNING);
        } else {
            this.examReviewState.set(ExamReviewState.FINISHED);
        }
    }

    /**
     * Auxiliary method that sets the state for the whole Exam correction section
     */
    private setCorrectionState(): void {
        if (this.examReviewState() === ExamReviewState.RUNNING) {
            this.examCorrectionState.set(ExamReviewState.RUNNING);
        } else if (!this.exam().publishResultsDate || this.examReviewState() === ExamReviewState.UNSET) {
            this.examCorrectionState.set(ExamReviewState.UNSET);
        } else if (this.exam().publishResultsDate && this.examReviewState() === ExamReviewState.PLANNED) {
            this.examCorrectionState.set(ExamReviewState.PLANNED);
        } else if (this.examReviewState() === ExamReviewState.FINISHED && this.allComplaintsResolved()) {
            this.examCorrectionState.set(ExamReviewState.FINISHED);
        } else {
            this.examCorrectionState.set(ExamReviewState.RUNNING);
        }
    }

    /**
     * Auxiliary method that determines the state of the different sub steps of exam preparation and stores them in a map
     * Finally determines whether every sub step is sufficiently fulfilled and therefore exam preparation is finished
     */
    private setExamPreparation(): void {
        // Step 1.1:
        this.configuredExercises.set(this.areAllExercisesConfigured());
        // For test exam, only the exerciseConfiguration needs to be performed by the instructor
        if (!this.isActingAsTestExam()) {
            // Step 1.2
            this.registeredStudents.set(this.examChecklistService.checkAtLeastOneRegisteredStudent(this.exam()));
            // Step 1.3:
            this.generatedStudentExams.set(this.examChecklistService.checkAllExamsGenerated(this.exam(), this.examChecklist()) && this.registeredStudents());
            // Step 1.4:
            this.preparedExerciseStart.set(!!this.examChecklist().allExamExercisesAllStudentsPrepared && this.generatedStudentExams());
        } else {
            this.registeredStudents.set(false);
            this.generatedStudentExams.set(false);
            this.preparedExerciseStart.set(false);
        }
        this.examPreparationFinished.set(this.isExamPreparationFinished());
        this.mandatoryPreparationFinished.set(this.isMandatoryPreparationFinished());
    }

    /**
     * Indicates whether the exam already started
     */
    private examAlreadyStarted(): boolean {
        const exam = this.exam();
        return !!exam.startDate && exam.startDate.isBefore(dayjs());
    }

    /**
     * Indicates whether the exam is already finished
     */
    private examAlreadyEnded(): boolean {
        const exam = this.exam();
        return !!exam.endDate && exam.endDate.isBefore(dayjs());
    }

    /**
     * Indicates whether exam review is already running
     */
    private isExamReviewRunning(): boolean {
        const exam = this.exam();
        return (
            ((!exam.examStudentReviewStart && exam.examStudentReviewEnd && exam.examStudentReviewEnd.isAfter(dayjs())) ||
                (exam.examStudentReviewStart && exam.examStudentReviewStart.isBefore(dayjs()) && exam.examStudentReviewEnd!.isAfter(dayjs()))) ??
            false
        );
    }

    /**
     * Indicates whether exam review is planned
     */
    private isExamReviewPlanned(): boolean {
        const exam = this.exam();
        return (exam.examStudentReviewStart && exam.examStudentReviewStart.isAfter(dayjs())) ?? false;
    }

    /**
     * Indicates whether all complaints are resolved
     */
    private allComplaintsResolved(): boolean {
        return this.examChecklist().numberOfAllComplaints === this.examChecklist().numberOfAllComplaintsDone;
    }
}
