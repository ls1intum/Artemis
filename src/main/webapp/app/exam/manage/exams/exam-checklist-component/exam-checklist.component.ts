import { Component, OnChanges, OnDestroy, OnInit, inject, input } from '@angular/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamChecklist } from 'app/exam/shared/entities/exam-checklist.model';
import { faChartBar, faEye, faListAlt, faThList, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Subject, Subscription } from 'rxjs';
import { captureException } from '@sentry/angular';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ChecklistCheckComponent } from 'app/shared/components/checklist-check/checklist-check.component';
import { ExamChecklistExerciseGroupTableComponent } from './exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { ExamEditWorkingTimeComponent } from './exam-edit-workingtime-dialog/exam-edit-working-time.component';
import { ExamLiveAnnouncementCreateButtonComponent } from './exam-announcement-dialog/exam-live-announcement-create-button.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_TEXT } from 'app/app.constants';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-exam-checklist',
    templateUrl: './exam-checklist.component.html',
    imports: [
        TranslateDirective,
        ChecklistCheckComponent,
        ExamChecklistExerciseGroupTableComponent,
        RouterLink,
        FaIconComponent,
        ProgressBarComponent,
        ExamEditWorkingTimeComponent,
        ExamLiveAnnouncementCreateButtonComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        HelpIconComponent,
    ],
})
export class ExamChecklistComponent implements OnChanges, OnInit, OnDestroy {
    private examChecklistService = inject(ExamChecklistService);
    private websocketService = inject(WebsocketService);
    private examManagementService = inject(ExamManagementService);
    private alertService = inject(AlertService);
    private studentExamService = inject(StudentExamService);
    private profileService = inject(ProfileService);

    exam = input.required<Exam>();
    getExamRoutesByIdentifier = input.required<(identifier: string) => (string | number | undefined)[]>();
    private longestWorkingTimeSub: Subscription | undefined = undefined;

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
    existsUnfinishedAssessments = false;
    existsUnassessedQuizzes = false;
    existsUnsubmittedExercises = false;
    isExamOver = false;
    longestWorkingTime?: number;

    numberOfSubmitted = 0;
    numberOfStarted = 0;

    disabledExercises: Exercise[] = [];

    // Icons
    faEye = faEye;
    faWrench = faWrench;
    faUser = faUser;
    faListAlt = faListAlt;
    faThList = faThList;
    faChartBar = faChartBar;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    private submittedSubscription?: Subscription;
    private startedSubscription?: Subscription;

    ngOnInit() {
        const submittedTopic = this.examChecklistService.getSubmittedTopic(this.exam());
        this.submittedSubscription = this.websocketService.subscribe<void>(submittedTopic).subscribe(() => (this.numberOfSubmitted += 1));
        const startedTopic = this.examChecklistService.getStartedTopic(this.exam());
        this.startedSubscription = this.websocketService.subscribe<void>(startedTopic).subscribe(() => (this.numberOfStarted += 1));
        const exam = this.exam();
        if (exam?.course?.id && exam?.id) {
            this.longestWorkingTimeSub = this.studentExamService.getLongestWorkingTimeForExam(exam.course.id, exam.id).subscribe((res) => {
                this.longestWorkingTime = res;
                this.calculateIsExamOver();
            });
        }
        const profileInfo = this.profileService.getProfileInfo();
        this.disabledExercises =
            this.exam()
                .exerciseGroups?.flatMap((group) => group.exercises)
                .filter((exercise) => exercise !== undefined)
                .filter((exercise) => !this.isExerciseTypeEnabled(profileInfo.activeModuleFeatures, exercise?.type)) ?? [];
    }

    ngOnChanges() {
        this.isTestExam = this.exam().testExam!;
        this.pointsExercisesEqual = this.examChecklistService.checkPointsExercisesEqual(this.exam());
        this.totalPoints = this.examChecklistService.checkTotalPointsMandatory(this.pointsExercisesEqual, this.exam());
        this.allGroupsContainExercise = this.examChecklistService.checkEachGroupContainsExercise(this.exam());
        this.countMandatoryExercises = this.exam().exerciseGroups?.filter((group) => group.isMandatory)?.length ?? 0;
        this.hasOptionalExercises = this.countMandatoryExercises < (this.exam().exerciseGroups?.length ?? 0);
        this.examChecklistService.getExamStatistics(this.exam()).subscribe((examStats) => {
            this.examChecklist = examStats;
            const exam = this.exam();
            this.allExamsGenerated = !!exam.numberOfExamUsers && exam.numberOfExamUsers > 0 && this.examChecklistService.checkAllExamsGenerated(exam, this.examChecklist);
            this.numberOfStarted = this.examChecklist.numberOfExamsStarted;
            this.numberOfSubmitted = this.examChecklist.numberOfExamsSubmitted;
            if (this.isExamOver) {
                if (this.examChecklist.numberOfTotalExamAssessmentsFinishedByCorrectionRound !== undefined) {
                    const lastAssessmentFinished = this.examChecklist.numberOfTotalExamAssessmentsFinishedByCorrectionRound.last();
                    this.existsUnfinishedAssessments = lastAssessmentFinished !== this.examChecklist.numberOfTotalParticipationsForAssessment;
                }
            }
            this.existsUnassessedQuizzes = this.examChecklist.existsUnassessedQuizzes;
            this.existsUnsubmittedExercises = this.examChecklist.existsUnsubmittedExercises;
        });
    }

    ngOnDestroy(): void {
        this.submittedSubscription?.unsubscribe();
        this.startedSubscription?.unsubscribe();
        if (this.longestWorkingTimeSub) {
            this.longestWorkingTimeSub.unsubscribe();
        }
    }

    /**
     * Evaluates all the quiz exercises that belong to the exam
     */
    evaluateQuizExercises() {
        this.isEvaluatingQuizExercises = true;
        const exam = this.exam();
        if (exam.course?.id && exam.id) {
            this.examManagementService.evaluateQuizExercises(exam.course.id, exam.id).subscribe({
                next: (res) => {
                    this.alertService.success('artemisApp.studentExams.evaluateQuizExerciseSuccess', { number: res?.body });
                    this.existsUnassessedQuizzes = false;
                    this.isEvaluatingQuizExercises = false;
                },
                error: (error: HttpErrorResponse) => {
                    this.dialogErrorSource.next(error.message);
                    this.alertService.error('artemisApp.studentExams.evaluateQuizExerciseFailure');
                    this.isEvaluatingQuizExercises = false;
                },
            });
        } else {
            captureException(new Error(`Quiz exercises could not be evaluated due to missing course ID or exam ID`));
        }
    }

    /**
     * Evaluates all the unsubmitted Text and Modelling submissions to 0
     */
    assessUnsubmittedExamModelingAndTextParticipations() {
        this.isAssessingUnsubmittedExams = true;
        const exam = this.exam();
        if (exam.course?.id && exam.id) {
            this.examManagementService.assessUnsubmittedExamModelingAndTextParticipations(exam.course.id, exam.id).subscribe({
                next: (res) => {
                    this.alertService.success('artemisApp.studentExams.assessUnsubmittedStudentExamsSuccess', { number: res?.body });
                    this.existsUnsubmittedExercises = false;
                    this.isAssessingUnsubmittedExams = false;
                },
                error: (error: HttpErrorResponse) => {
                    this.dialogErrorSource.next(error.message);
                    this.alertService.error('artemisApp.studentExams.assessUnsubmittedStudentExamsFailure');
                    this.isAssessingUnsubmittedExams = false;
                },
            });
        } else {
            captureException(new Error(`Unsubmitted exercises could not be evaluated due to missing course ID or exam ID`));
        }
    }

    calculateIsExamOver() {
        if (this.longestWorkingTime && this.exam()) {
            const startDate = dayjs(this.exam().startDate);
            let endDate = startDate.add(this.longestWorkingTime, 'seconds');
            if (this.exam().gracePeriod) {
                endDate = endDate.add(this.exam().gracePeriod!, 'seconds');
            }
            this.isExamOver = endDate.isBefore(dayjs());
        }
    }

    private isExerciseTypeEnabled(activeModuleFeatures: string[], exerciseType?: ExerciseType) {
        switch (exerciseType) {
            case ExerciseType.TEXT:
                return activeModuleFeatures.includes(MODULE_FEATURE_TEXT);
            // For now, all exercises are enabled by default
            default:
                return true;
        }
    }
}
