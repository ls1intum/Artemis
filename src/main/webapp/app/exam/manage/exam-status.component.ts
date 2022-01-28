import { Component, Input, OnChanges } from '@angular/core';
import { faArrowRight, faCalendarCheck, faCheckCircle, faTimes, faTimesCircle, faCalendarTimes, faDotCircle } from '@fortawesome/free-solid-svg-icons';
import { Exam } from 'app/entities/exam.model';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import dayjs from 'dayjs/esm';

export enum ExamStatusTranslationSuffixes {
    CONFIGURE_EXERCISES = 'configureExercises',
    REGISTER_STUDENTS = 'registerStudents',
    GENERATE_STUDENT_EXAMS = 'generateStudentExams',
    PREPARE_EXERCISE_START = 'prepareExerciseStart',
}

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
    @Input()
    public time: dayjs.Dayjs;

    translationSuffixes = [
        ExamStatusTranslationSuffixes.CONFIGURE_EXERCISES,
        ExamStatusTranslationSuffixes.REGISTER_STUDENTS,
        ExamStatusTranslationSuffixes.GENERATE_STUDENT_EXAMS,
        ExamStatusTranslationSuffixes.PREPARE_EXERCISE_START,
    ];
    translationToFlagMap = new Map<string, boolean>();
    examChecklist: ExamChecklist;
    numberOfGeneratedStudentExams: number;

    examPreparationFinished = false;
    examConductionState: ExamConductionState;
    examReviewState: ExamReviewState;

    readonly examConductionAndCorrectionStateEnum = ExamConductionState;

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
        // Step 1: Exam preparation

        // Step 1.1: Configure Exercises
        this.translationToFlagMap.set(ExamStatusTranslationSuffixes.CONFIGURE_EXERCISES, this.areAllExercisesConfigured());
        this.translationToFlagMap.set(ExamStatusTranslationSuffixes.REGISTER_STUDENTS, this.examChecklistService.checkAtLeastOneRegisteredStudent(this.exam));
        this.examChecklistService.getExamStatistics(this.exam).subscribe((examStats) => {
            this.translationToFlagMap.set(ExamStatusTranslationSuffixes.GENERATE_STUDENT_EXAMS, this.examChecklistService.checkAllExamsGenerated(this.exam, examStats));
            this.translationToFlagMap.set(ExamStatusTranslationSuffixes.PREPARE_EXERCISE_START, !!examStats.allExamExercisesAllStudentsPrepared);
            this.examPreparationFinished = this.isExamPreparationFinished();
            this.examChecklist = examStats;
            this.numberOfGeneratedStudentExams = this.examChecklist.numberOfGeneratedStudentExams ?? 0;
        });

        // Step 2: Exam conduction
        this.setConductionState();

        // Step 3: Exam correction
        this.setReviewState();
    }

    /**
     * Auxiliary method that determines if all configuration steps for the exam exercises are finished. There are 5 requirements that have to be fulfilled:
     * 1. The exam has at least one exercise group
     * 2. The number of exam exercises equals number of exercise groups
     * 3. Each exercise group contains at least one exercise
     * 4. Within each exercise group, exercises have same maximum points
     * 5. Maximum points of exam are reachable
     * @private
     * returns boolean indicating whether configuration is finished
     */
    private areAllExercisesConfigured(): boolean {
        // 1.
        const atLeastOneGroup = this.examChecklistService.checkAtLeastOneExerciseGroup(this.exam);

        // 2.
        const numberOfExercisesEqual = this.examChecklistService.checkNumberOfExerciseGroups(this.exam);

        // 3.
        const noEmptyExerciseGroup = this.examChecklistService.checkAllGroupContainsExercise(this.exam);

        // 4.
        const maximumPointsEqual = this.examChecklistService.checkPointsExercisesEqual(this.exam);

        // 5.
        const examPointsReachable = this.examChecklistService.checkTotalPointsMandatory(maximumPointsEqual, this.exam);

        return atLeastOneGroup && noEmptyExerciseGroup && numberOfExercisesEqual && maximumPointsEqual && examPointsReachable;
    }

    /**
     * Auxiliary method indicating whether all steps of Exam preparation are done
     * @private
     */
    private isExamPreparationFinished(): boolean {
        return this.translationSuffixes.every((suffix) => this.translationToFlagMap.get(suffix));
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
        } else if (
            (!this.exam.examStudentReviewStart && this.exam.examStudentReviewEnd && this.exam.examStudentReviewEnd.isAfter(this.time)) ||
            (this.exam.examStudentReviewStart && this.exam.examStudentReviewStart.isBefore(this.time) && this.exam.examStudentReviewEnd.isAfter(this.time))
        ) {
            this.examReviewState = ExamReviewState.RUNNING;
        } else if (this.exam.examStudentReviewEnd.isBefore(this.time)) {
            this.examReviewState = ExamReviewState.FINISHED;
        } else {
            this.examReviewState = ExamReviewState.PLANNED;
        }
    }

    /**
     * Indicates whether the exam already started
     */
    examAlreadyStarted() {
        return this.exam.startDate && this.exam.startDate.isBefore(this.time);
    }

    /**
     * Indicates whether the exam is already finished
     */
    examAlreadyEnded() {
        console.log(this.exam.endDate!.isAfter(this.time));
        return this.exam.endDate && this.exam.endDate.isBefore(this.time);
    }
}
