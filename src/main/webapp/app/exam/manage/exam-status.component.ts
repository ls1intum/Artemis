import { Component, Input, OnChanges } from '@angular/core';
import { faArrowRight, faCalendarCheck, faCheckCircle, faTimes, faTimesCircle, faCalendarTimes, faDotCircle } from '@fortawesome/free-solid-svg-icons';
import { Exam } from 'app/entities/exam.model';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import dayjs from 'dayjs/esm';
import { round } from 'app/shared/util/utils';

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
}

@Component({
    selector: 'jhi-exam-status',
    templateUrl: './exam-status.component.html',
    styleUrls: ['./exam-status.component.scss'],
})
export class ExamStatusComponent implements OnChanges {
    @Input()
    public exam: Exam;

    examChecklist: ExamChecklist;
    numberOfGeneratedStudentExams: number;

    configuredExercises: boolean;
    registeredStudents: boolean;
    generatedStudentExams: boolean;
    preparedExerciseStart: boolean;

    examPreparationFinished = false;
    examConductionState: ExamConductionState;
    examReviewState: ExamReviewState;

    readonly examConductionStateEnum = ExamConductionState;
    readonly examReviewStateEnum = ExamReviewState;
    readonly round = round;
    readonly Math = Math;

    // Icons
    faTimes = faTimes;
    faTimesCircle = faTimesCircle;
    faCheckCircle = faCheckCircle;
    faArrowRight = faArrowRight;
    faDotCircle = faDotCircle;
    faCalendarCheck = faCalendarCheck;
    faCalendarTimes = faCalendarTimes;

    constructor(private examChecklistService: ExamChecklistService) {}

    ngOnChanges(): void {
        this.examChecklistService.getExamStatistics(this.exam).subscribe((examStats) => {
            this.examChecklist = examStats;
            this.numberOfGeneratedStudentExams = this.examChecklist.numberOfGeneratedStudentExams ?? 0;

            // Step 1:
            this.setExamPreparation();
        });

        // Step 2: Exam conduction
        this.setConductionState();

        // Step 3: Exam correction
        this.setReviewState();
    }

    /**
     * Auxiliary method that determines if all configuration steps for the exam exercises are finished
     * @private
     * @returns boolean indicating whether configuration is finished
     */
    private areAllExercisesConfigured(): boolean {
        const atLeastOneGroup = this.examChecklistService.checkAtLeastOneExerciseGroup(this.exam);
        const numberOfExercisesEqual = this.examChecklistService.checkNumberOfExerciseGroups(this.exam);
        const noEmptyExerciseGroup = this.examChecklistService.checkEachGroupContainsExercise(this.exam);
        const maximumPointsEqual = this.examChecklistService.checkPointsExercisesEqual(this.exam);
        const examPointsReachable = this.examChecklistService.checkTotalPointsMandatory(maximumPointsEqual, this.exam);

        return atLeastOneGroup && noEmptyExerciseGroup && numberOfExercisesEqual && maximumPointsEqual && examPointsReachable;
    }

    /**
     * Auxiliary method indicating whether all steps of Exam preparation are done
     * @private
     */
    private isExamPreparationFinished(): boolean {
        return this.configuredExercises && this.registeredStudents && this.generatedStudentExams && this.preparedExerciseStart;
    }

    /**
     * Sets the conductionState according to the current situation
     * @private
     */
    private setConductionState(): void {
        if (this.examAlreadyEnded()) {
            this.examConductionState = ExamConductionState.FINISHED;
        } else if (this.examAlreadyStarted()) {
            this.examConductionState = ExamConductionState.RUNNING;
        } else {
            this.examConductionState = ExamConductionState.PLANNED;
        }
    }

    /**
     * Sets the reviewState according to the current situation
     * @private
     */
    private setReviewState(): void {
        if (!this.exam.examStudentReviewEnd) {
            this.examReviewState = ExamReviewState.UNSET;
        } else if (this.isExamReviewRunning()) {
            this.examReviewState = ExamReviewState.RUNNING;
        } else if (this.exam.examStudentReviewEnd.isBefore(dayjs())) {
            this.examReviewState = ExamReviewState.FINISHED;
        } else {
            this.examReviewState = ExamReviewState.PLANNED;
        }
    }

    /**
     * Auxiliary method that determines the state of the different sub steps of exam preparation and stores them in a map
     * Finally determines whether every sub step is sufficiently fulfilled and therefore exam preparation is finished
     * @private
     */
    private setExamPreparation(): void {
        // Step 1.1:
        this.configuredExercises = this.areAllExercisesConfigured();
        // Step 1.2:
        this.registeredStudents = this.examChecklistService.checkAtLeastOneRegisteredStudent(this.exam);
        // Step 1.3:
        this.generatedStudentExams = this.examChecklistService.checkAllExamsGenerated(this.exam, this.examChecklist) && this.registeredStudents;
        // Step 1.4:
        this.preparedExerciseStart = !!this.examChecklist.allExamExercisesAllStudentsPrepared && this.generatedStudentExams;
        this.examPreparationFinished = this.isExamPreparationFinished();
    }

    /**
     * Indicates whether the exam already started
     * @private
     */
    private examAlreadyStarted(): boolean {
        return this.exam.startDate! && this.exam.startDate.isBefore(dayjs());
    }

    /**
     * Indicates whether the exam is already finished
     * @private
     */
    private examAlreadyEnded(): boolean {
        return this.exam.endDate! && this.exam.endDate.isBefore(dayjs());
    }

    /**
     * Indicates whether exam review is already running
     * @private
     */
    private isExamReviewRunning(): boolean {
        return (
            ((!this.exam.examStudentReviewStart && this.exam.examStudentReviewEnd && this.exam.examStudentReviewEnd.isAfter(dayjs())) ||
                (this.exam.examStudentReviewStart && this.exam.examStudentReviewStart.isBefore(dayjs()) && this.exam.examStudentReviewEnd!.isAfter(dayjs()))) ??
            false
        );
    }
}
