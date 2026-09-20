import { Component, OnDestroy, OnInit, effect, inject, input, signal } from '@angular/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { isActingAsTestExam, isRealExam } from 'app/exam/overview/exam.utils';
import { ExamChecklist } from 'app/exam/shared/entities/exam-checklist.model';
import { faChartBar, faEye, faListAlt, faThList, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Subject, Subscription } from 'rxjs';
import { captureException } from '@sentry/angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ChecklistCheckComponent } from 'app/shared-ui/components/checklist-check/checklist-check.component';
import { ExamChecklistExerciseGroupTableComponent } from './exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgressBarComponent } from 'app/exercise/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { ExamEditWorkingTimeComponent } from './exam-edit-workingtime-dialog/exam-edit-working-time.component';
import { ExamLiveAnnouncementCreateButtonComponent } from './exam-announcement-dialog/exam-live-announcement-create-button.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_TEXT } from 'app/app.constants';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

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
export class ExamChecklistComponent implements OnInit, OnDestroy {
    private examChecklistService = inject(ExamChecklistService);
    private websocketService = inject(WebsocketService);
    private examManagementService = inject(ExamManagementService);
    private alertService = inject(AlertService);
    private studentExamService = inject(StudentExamService);
    private profileService = inject(ProfileService);

    exam = input.required<Exam>();
    getExamRoutesByIdentifier = input.required<(identifier: string) => (string | number | undefined)[]>();

    constructor() {
        effect(() => {
            this.updateChecklistState();
        });
    }
    private longestWorkingTimeSub: Subscription | undefined = undefined;

    readonly examChecklist = signal<ExamChecklist | undefined>(undefined);
    isLoading = false;
    readonly pointsExercisesEqual = signal(false);
    readonly allExamsGenerated = signal(false);
    readonly allGroupsContainExercise = signal(false);
    readonly totalPoints = signal(false);
    readonly hasOptionalExercises = signal(false);
    readonly countMandatoryExercises = signal(0);
    // when it is a test exam or a simulation and test exam and the simulation phase is over
    readonly isActingAsTestExam = signal<boolean>(undefined!);
    readonly isRealExam = signal<boolean>(undefined!);
    readonly isEvaluatingQuizExercises = signal(false);
    readonly isAssessingUnsubmittedExams = signal(false);
    readonly existsUnfinishedAssessments = signal(false);
    readonly existsUnassessedQuizzes = signal(false);
    readonly existsUnsubmittedExercises = signal(false);
    readonly isExamOver = signal(false);
    longestWorkingTime?: number;

    readonly numberOfSubmitted = signal(0);
    readonly numberOfStarted = signal(0);

    readonly disabledExercises = signal<Exercise[]>([]);

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
        this.submittedSubscription = this.websocketService.subscribe<void>(submittedTopic).subscribe(() => this.numberOfSubmitted.update((value) => value + 1));
        const startedTopic = this.examChecklistService.getStartedTopic(this.exam());
        this.startedSubscription = this.websocketService.subscribe<void>(startedTopic).subscribe(() => this.numberOfStarted.update((value) => value + 1));
        const exam = this.exam();
        if (exam?.course?.id && exam?.id) {
            this.longestWorkingTimeSub = this.studentExamService.getLongestWorkingTimeForExam(exam.course.id, exam.id).subscribe((res) => {
                this.longestWorkingTime = res;
                this.calculateIsExamOver();
            });
        }
        const profileInfo = this.profileService.getProfileInfo();
        this.disabledExercises.set(
            this.exam()
                .exerciseGroups?.flatMap((group) => group.exercises)
                .filter((exercise) => exercise !== undefined)
                .filter((exercise) => !this.isExerciseTypeEnabled(profileInfo.activeModuleFeatures, exercise?.type)) ?? [],
        );
    }

    private updateChecklistState() {
        this.isActingAsTestExam.set(isActingAsTestExam(this.exam()));
        this.isRealExam.set(isRealExam(this.exam()));
        this.pointsExercisesEqual.set(this.examChecklistService.checkPointsExercisesEqual(this.exam()));
        this.totalPoints.set(this.examChecklistService.checkTotalPointsMandatory(this.pointsExercisesEqual(), this.exam()));
        this.allGroupsContainExercise.set(this.examChecklistService.checkEachGroupContainsExercise(this.exam()));
        this.countMandatoryExercises.set(this.exam().exerciseGroups?.filter((group) => group.isMandatory)?.length ?? 0);
        this.hasOptionalExercises.set(this.countMandatoryExercises() < (this.exam().exerciseGroups?.length ?? 0));
        this.examChecklistService.getExamStatistics(this.exam()).subscribe((examStats) => {
            this.examChecklist.set(examStats);
            const exam = this.exam();
            this.allExamsGenerated.set(!!exam.numberOfExamUsers && exam.numberOfExamUsers > 0 && this.examChecklistService.checkAllExamsGenerated(exam, examStats));
            this.numberOfStarted.set(examStats.numberOfExamsStarted);
            this.numberOfSubmitted.set(examStats.numberOfExamsSubmitted);
            if (this.isExamOver()) {
                if (examStats.numberOfTotalExamAssessmentsFinishedByCorrectionRound !== undefined) {
                    const lastAssessmentFinished = examStats.numberOfTotalExamAssessmentsFinishedByCorrectionRound.last();
                    this.existsUnfinishedAssessments.set(lastAssessmentFinished !== examStats.numberOfTotalParticipationsForAssessment);
                }
            }
            this.existsUnassessedQuizzes.set(examStats.existsUnassessedQuizzes);
            this.existsUnsubmittedExercises.set(examStats.existsUnsubmittedExercises);
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
        this.isEvaluatingQuizExercises.set(true);
        const exam = this.exam();
        if (exam.course?.id && exam.id) {
            this.examManagementService.evaluateQuizExercises(exam.course.id, exam.id).subscribe({
                next: (res) => {
                    this.alertService.success('artemisApp.studentExams.evaluateQuizExerciseSuccess', { number: res?.body });
                    this.existsUnassessedQuizzes.set(false);
                    this.isEvaluatingQuizExercises.set(false);
                },
                error: (error: HttpErrorResponse) => {
                    this.dialogErrorSource.next(error.message);
                    this.alertService.error('artemisApp.studentExams.evaluateQuizExerciseFailure');
                    this.isEvaluatingQuizExercises.set(false);
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
        this.isAssessingUnsubmittedExams.set(true);
        const exam = this.exam();
        if (exam.course?.id && exam.id) {
            this.examManagementService.assessUnsubmittedExamModelingAndTextParticipations(exam.course.id, exam.id).subscribe({
                next: (res) => {
                    this.alertService.success('artemisApp.studentExams.assessUnsubmittedStudentExamsSuccess', { number: res?.body });
                    this.existsUnsubmittedExercises.set(false);
                    this.isAssessingUnsubmittedExams.set(false);
                },
                error: (error: HttpErrorResponse) => {
                    this.dialogErrorSource.next(error.message);
                    this.alertService.error('artemisApp.studentExams.assessUnsubmittedStudentExamsFailure');
                    this.isAssessingUnsubmittedExams.set(false);
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
            this.isExamOver.set(endDate.isBefore(dayjs()));
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
