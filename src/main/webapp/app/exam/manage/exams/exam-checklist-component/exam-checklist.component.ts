import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { faChartBar, faEye, faListAlt, faThList, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { StudentExam } from 'app/entities/student-exam.model';

@Component({
    selector: 'jhi-exam-checklist',
    templateUrl: './exam-checklist.component.html',
})
export class ExamChecklistComponent implements OnChanges, OnInit, OnDestroy {
    @Input() exam: Exam;
    @Input() getExamRoutesByIdentifier: any;
    course: Course | undefined;

    examChecklist: ExamChecklist;
    isLoading = false;
    pointsExercisesEqual = false;
    allExamsGenerated = false;
    allGroupsContainExercise = false;
    totalPoints = false;
    hasOptionalExercises = false;
    countMandatoryExercises = 0;
    isTestExam: boolean;
    isEvaluatingQuizExercises: boolean;
    isAssessingUnsubmittedExams: boolean;
    existsUnfinishedAssessments: boolean = false;
    existsUnassessedQuizzes: boolean = false;
    existsUnsubmittedExercises: boolean = false;
    isExamOver = false;
    longestWorkingTime?: number;
    studentExams: StudentExam[];

    numberOfSubmitted = 0;
    numberOfStarted = 0;

    examPreparationFinished: boolean;

    // Icons
    faEye = faEye;
    faWrench = faWrench;
    faUser = faUser;
    faListAlt = faListAlt;
    faThList = faThList;
    faChartBar = faChartBar;

    constructor(
        private examChecklistService: ExamChecklistService,
        private websocketService: JhiWebsocketService,
        private examManagementService: ExamManagementService,
        private alertService: AlertService,
        private artemisTranslatePipe: ArtemisTranslatePipe,
        private studentExamService: StudentExamService,
    ) {}

    ngOnInit() {
        const submittedTopic = this.examChecklistService.getSubmittedTopic(this.exam);
        this.websocketService.subscribe(submittedTopic);
        this.websocketService.receive(submittedTopic).subscribe(() => (this.numberOfSubmitted += 1));
        const startedTopic = this.examChecklistService.getStartedTopic(this.exam);
        this.websocketService.subscribe(startedTopic);
        this.websocketService.receive(startedTopic).subscribe(() => (this.numberOfStarted += 1));
    }

    ngOnChanges() {
        this.course = this.exam.course;
        this.isTestExam = this.exam.testExam!;
        this.pointsExercisesEqual = this.examChecklistService.checkPointsExercisesEqual(this.exam);
        this.totalPoints = this.examChecklistService.checkTotalPointsMandatory(this.pointsExercisesEqual, this.exam);
        this.allGroupsContainExercise = this.examChecklistService.checkEachGroupContainsExercise(this.exam);
        this.countMandatoryExercises = this.exam.exerciseGroups?.filter((group) => group.isMandatory)?.length ?? 0;
        this.hasOptionalExercises = this.countMandatoryExercises < (this.exam.exerciseGroups?.length ?? 0);
        if (this.course && this.course.id && this.exam && this.exam.id) {
            this.studentExamService.getLongestWorkingTimeForExam(this.course.id, this.exam.id).subscribe((res) => {
                this.longestWorkingTime = res;
                this.calculateIsExamOver();
            });
        }
        this.examChecklistService.getExamStatistics(this.exam).subscribe((examStats) => {
            this.examChecklist = examStats;
            this.allExamsGenerated =
                !!this.exam.numberOfExamUsers && this.exam.numberOfExamUsers > 0 && this.examChecklistService.checkAllExamsGenerated(this.exam, this.examChecklist);
            this.numberOfStarted = this.examChecklist.numberOfExamsStarted;
            this.numberOfSubmitted = this.examChecklist.numberOfExamsSubmitted;
            if (this.isExamOver) {
                if (this.examChecklist.numberOfTotalExamAssessmentsFinishedByCorrectionRound) {
                    const lastAssessmentFinished = this.examChecklist.numberOfTotalExamAssessmentsFinishedByCorrectionRound.last();
                    this.existsUnfinishedAssessments = lastAssessmentFinished !== this.examChecklist.numberOfTotalParticipationsForAssessment;
                }
                this.existsUnassessedQuizzes = this.examChecklist.existsUnassessedQuizzes;
                this.existsUnsubmittedExercises = this.examChecklist.existsUnsubmittedExercises;
            }
        });
    }

    ngOnDestroy(): void {
        const submittedTopic = this.examChecklistService.getSubmittedTopic(this.exam);
        this.websocketService.unsubscribe(submittedTopic);
        const startedTopic = this.examChecklistService.getStartedTopic(this.exam);
        this.websocketService.unsubscribe(startedTopic);
    }

    /**
     * Evaluates all the quiz exercises that belong to the exam
     */
    evaluateQuizExercises() {
        this.isEvaluatingQuizExercises = true;
        if (this.exam.course?.id !== undefined && this.exam.id !== undefined) {
            this.examManagementService.evaluateQuizExercises(this.exam.course.id, this.exam.id).subscribe({
                next: (res) => {
                    this.alertService.success('artemisApp.studentExams.evaluateQuizExerciseSuccess', { number: res?.body });
                    this.isEvaluatingQuizExercises = false;
                },
                error: (err: HttpErrorResponse) => {
                    this.handleError('artemisApp.studentExams.evaluateQuizExerciseFailure', err);
                    this.isEvaluatingQuizExercises = false;
                },
            });
        } else {
            throw new Error(`Cannot evaluate quiz exercises due to missing course or exam id.`);
        }
    }

    assessUnsubmittedExamModelingAndTextParticipations() {
        this.isAssessingUnsubmittedExams = true;
        if (this.exam.course?.id !== undefined && this.exam.id !== undefined) {
            this.examManagementService.assessUnsubmittedExamModelingAndTextParticipations(this.exam.course.id, this.exam.id).subscribe({
                next: (res) => {
                    this.alertService.success('artemisApp.studentExams.assessUnsubmittedStudentExamsSuccess', { number: res?.body });
                    this.isAssessingUnsubmittedExams = false;
                },
                error: (err: HttpErrorResponse) => {
                    this.handleError('artemisApp.studentExams.assessUnsubmittedStudentExamsFailure', err);
                    this.isAssessingUnsubmittedExams = false;
                },
            });
        } else {
            throw new Error(`Cannot unsubmitted exercises due to missing course or exam id.`);
        }
    }

    /**
     * Shows the translated error message if an error key is available in the error response. Otherwise it defaults to the generic alert.
     * @param translationString the string identifier in the translation service for the text. This is ignored if the response does not contain an error message or error key.
     * @param err the error response
     */
    private handleError(translationString: string, err: HttpErrorResponse) {
        let errorDetail;
        if (err?.error && err.error.errorKey) {
            errorDetail = this.artemisTranslatePipe.transform(err.error.errorKey);
        } else {
            errorDetail = err?.error?.message;
        }
        if (errorDetail) {
            this.alertService.error(translationString, { message: errorDetail });
        } else {
            // Sometimes the response does not have an error field, so we default to generic error handling
            onError(this.alertService, err);
        }
    }

    calculateIsExamOver() {
        if (this.longestWorkingTime && this.exam) {
            const startDate = dayjs(this.exam.startDate);
            let endDate = startDate.add(this.longestWorkingTime, 'seconds');
            if (this.exam.gracePeriod) {
                endDate = endDate.add(this.exam.gracePeriod!, 'seconds');
            }
            this.isExamOver = endDate.isBefore(dayjs());
        }
    }
}
