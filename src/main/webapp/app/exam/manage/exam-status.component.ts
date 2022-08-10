import { Component, Input, OnChanges } from '@angular/core';
import { faArrowRight, faCheckCircle, faTimes, faTimesCircle, faDotCircle, faCircleExclamation } from '@fortawesome/free-solid-svg-icons';
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
    ERROR,
}

@Component({
    selector: 'jhi-exam-status',
    templateUrl: './exam-status.component.html',
    styleUrls: ['./exam-status.component.scss'],
})
export class ExamStatusComponent implements OnChanges {
    @Input()
    public exam: Exam;

    @Input()
    public isAtLeastInstructor: boolean;

    examChecklist: ExamChecklist;
    numberOfGeneratedStudentExams: number;

    configuredExercises: boolean;
    registeredStudents: boolean;
    generatedStudentExams: boolean;
    preparedExerciseStart: boolean;

    // all steps for the preparation finished
    examPreparationFinished = false;
    // All mandatory steps for the preparation finished
    mandatoryPreparationFinished = false;
    examConductionState: ExamConductionState;
    examReviewState: ExamReviewState;
    examCorrectionState: ExamReviewState;

    isTestExam: boolean;
    maxPointExercises: number;

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
    faCircleExclamation = faCircleExclamation;

    constructor(private examChecklistService: ExamChecklistService) {}

    ngOnChanges(): void {
        this.examChecklistService.getExamStatistics(this.exam).subscribe((examStats) => {
            this.examChecklist = examStats;
            this.numberOfGeneratedStudentExams = this.examChecklist.numberOfGeneratedStudentExams ?? 0;
            this.isTestExam = this.exam.testExam!;

            if (this.isAtLeastInstructor) {
                // Step 1:
                this.setExamPreparation();
            }

            // Step 2: Exam conduction
            this.setConductionState();

            if (!this.isTestExam) {
                // Step 3: Exam correction
                this.setReviewState();
                this.setCorrectionState();
            }
        });
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
        let examPointsReachable;
        if (this.isTestExam) {
            // This method is called here, as it is part of the exercise configuration - although it is a separate entry to highlight the importance
            this.maxPointExercises = this.examChecklistService.calculateExercisePoints(maximumPointsEqual, this.exam);
            examPointsReachable = this.exam.maxPoints === this.maxPointExercises;
        } else {
            examPointsReachable = this.examChecklistService.checkTotalPointsMandatory(maximumPointsEqual, this.exam);
        }

        return atLeastOneGroup && noEmptyExerciseGroup && numberOfExercisesEqual && maximumPointsEqual && examPointsReachable;
    }

    /**
     * Auxiliary method indicating whether all steps of Exam preparation are done
     * @private
     */
    private isExamPreparationFinished(): boolean {
        if (this.isTestExam) {
            // For test exam, only the exerciseConfiguration needs to be performed by the instructor
            return this.configuredExercises;
        } else {
            return this.configuredExercises && this.registeredStudents && this.generatedStudentExams && this.preparedExerciseStart;
        }
    }

    /**
     * Helper method to indicate weather the mandatory preparation steps are performed in order to display a warning in the status.
     * (PrepareExerciseStart is not mandatory, but highly recommended)
     * @private
     */
    private isMandatoryPreparationFinished(): boolean {
        if (this.isTestExam) {
            return this.configuredExercises;
        } else {
            return this.configuredExercises && this.registeredStudents && this.generatedStudentExams;
        }
    }

    /**
     * Sets the conductionState according to the current situation
     * @private
     */
    private setConductionState(): void {
        // In case the exercise configuration is wrong, but the (Test)Exam already started, students are not able to start a test eam or real exam
        // The ERROR-State should only be visible to Instructors, as editors & TAs have no access to the required data to determine if the preparation is finished
        if (this.isAtLeastInstructor && this.examAlreadyStarted() && !this.mandatoryPreparationFinished) {
            this.examConductionState = ExamConductionState.ERROR;
        } else if (this.examAlreadyEnded() && (!this.isAtLeastInstructor || this.examPreparationFinished)) {
            this.examConductionState = ExamConductionState.FINISHED;
        } else if (this.examAlreadyStarted() && !this.examAlreadyEnded()) {
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
        } else if (this.isExamReviewPlanned()) {
            this.examReviewState = ExamReviewState.PLANNED;
        } else if (this.isExamReviewRunning()) {
            this.examReviewState = ExamReviewState.RUNNING;
        } else {
            this.examReviewState = ExamReviewState.FINISHED;
        }
    }

    /**
     * Auxiliary method that sets the state for the whole Exam correction section
     * @private
     */
    private setCorrectionState(): void {
        if (this.examReviewState === ExamReviewState.RUNNING) {
            this.examCorrectionState = ExamReviewState.RUNNING;
        } else if (!this.exam.publishResultsDate || this.examReviewState === ExamReviewState.UNSET) {
            this.examCorrectionState = ExamReviewState.UNSET;
        } else if (this.exam.publishResultsDate && this.examReviewState === ExamReviewState.PLANNED) {
            this.examCorrectionState = ExamReviewState.PLANNED;
        } else if (this.examReviewState === ExamReviewState.FINISHED && this.allComplaintsResolved()) {
            this.examCorrectionState = ExamReviewState.FINISHED;
        } else {
            this.examCorrectionState = ExamReviewState.RUNNING;
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
        // For test exam, only the exerciseConfiguration needs to be performed by the instructor
        if (!this.isTestExam) {
            // Step 1.2
            this.registeredStudents = this.examChecklistService.checkAtLeastOneRegisteredStudent(this.exam);
            // Step 1.3:
            this.generatedStudentExams = this.examChecklistService.checkAllExamsGenerated(this.exam, this.examChecklist) && this.registeredStudents;
            // Step 1.4:
            this.preparedExerciseStart = !!this.examChecklist.allExamExercisesAllStudentsPrepared && this.generatedStudentExams;
        } else {
            this.registeredStudents = false;
            this.generatedStudentExams = false;
            this.preparedExerciseStart = false;
        }
        this.examPreparationFinished = this.isExamPreparationFinished();
        this.mandatoryPreparationFinished = this.isMandatoryPreparationFinished();
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

    /**
     * Indicates whether exam review is planned
     * @private
     */
    private isExamReviewPlanned(): boolean {
        return (this.exam.examStudentReviewStart && this.exam.examStudentReviewStart.isAfter(dayjs())) ?? false;
    }

    /**
     * Indicates whether all complaints are resolved
     * @private
     */
    private allComplaintsResolved(): boolean {
        return this.examChecklist.numberOfAllComplaints === this.examChecklist.numberOfAllComplaintsDone;
    }
}
