import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming-exercise-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-button.component';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import dayjs from 'dayjs/esm';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SortService } from 'app/shared/service/sort.service';
import { Submission } from 'app/entities/submission.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { createBuildPlanUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ConsistencyCheckComponent } from 'app/shared/consistency-check/consistency-check.component';
import { SubmissionPolicyService } from 'app/exercises/programming/manage/services/submission-policy.service';
import { ProgrammingExerciseGradingService } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import {
    faBook,
    faChartBar,
    faCheckDouble,
    faEraser,
    faExclamationTriangle,
    faEye,
    faListAlt,
    faPencilAlt,
    faTable,
    faTimes,
    faUserCheck,
    faUsers,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { TestwiseCoverageReportModalComponent } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-report-modal.component';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeHintService } from 'app/exercises/shared/exercise-hint/services/code-hint.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { ProgrammingLanguageFeatureService } from 'app/exercises/programming/shared/service/programming-language-feature/programming-language-feature.service';

@Component({
    selector: 'jhi-programming-exercise-detail',
    templateUrl: './programming-exercise-detail.component.html',
    styleUrls: ['./programming-exercise-detail.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ProgrammingExerciseDetailComponent implements OnInit, OnDestroy {
    readonly dayjs = dayjs;
    readonly ActionType = ActionType;
    readonly ProgrammingExerciseParticipationType = ProgrammingExerciseParticipationType;
    readonly FeatureToggle = FeatureToggle;
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly ButtonSize = ButtonSize;
    readonly AssessmentType = AssessmentType;
    programmingExercise: ProgrammingExercise;
    isExamExercise: boolean;
    supportsAuxiliaryRepositories: boolean;
    baseResource: string;
    shortBaseResource: string;
    teamBaseResource: string;
    loadingTemplateParticipationResults = true;
    loadingSolutionParticipationResults = true;
    lockingOrUnlockingRepositories = false;
    courseId: number;
    doughnutStats: ExerciseManagementStatisticsDto;

    isAdmin = false;
    addedLineCount: number;
    removedLineCount: number;
    isLoadingDiffReport: boolean;

    plagiarismCheckSupported = false; // default value

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faTimes = faTimes;
    faBook = faBook;
    faWrench = faWrench;
    faCheckDouble = faCheckDouble;
    faTable = faTable;
    faExclamationTriangle = faExclamationTriangle;
    faUserCheck = faUserCheck;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faPencilAlt = faPencilAlt;
    faEraser = faEraser;
    faUsers = faUsers;
    faEye = faEye;

    constructor(
        private activatedRoute: ActivatedRoute,
        private accountService: AccountService,
        private programmingExerciseService: ProgrammingExerciseService,
        private exerciseService: ExerciseService,
        private alertService: AlertService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private programmingExerciseSubmissionPolicyService: SubmissionPolicyService,
        private repositoryFileService: CodeEditorRepositoryFileService,
        private eventManager: EventManager,
        private modalService: NgbModal,
        private translateService: TranslateService,
        private profileService: ProfileService,
        private statisticsService: StatisticsService,
        private sortService: SortService,
        private programmingExerciseGradingService: ProgrammingExerciseGradingService,
        private codeHintService: CodeHintService,
        private router: Router,
        private programmingLanguageFeatureService: ProgrammingLanguageFeatureService,
    ) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
            const exerciseId = this.programmingExercise.id!;
            this.isExamExercise = !!this.programmingExercise.exerciseGroup;
            this.courseId = this.isExamExercise ? this.programmingExercise.exerciseGroup!.exam!.course!.id! : this.programmingExercise.course!.id!;
            this.isAdmin = this.accountService.isAdmin();

            if (!this.isExamExercise) {
                this.baseResource = `/course-management/${this.courseId}/programming-exercises/${exerciseId}/`;
                this.shortBaseResource = `/course-management/${this.courseId}/`;
                this.teamBaseResource = `/course-management/${this.courseId}/exercises/${exerciseId}/`;
            } else {
                this.baseResource =
                    `/course-management/${this.courseId}/exams/${this.programmingExercise.exerciseGroup?.exam?.id}` +
                    `/exercise-groups/${this.programmingExercise.exerciseGroup?.id}/programming-exercises/${exerciseId}/`;
                this.shortBaseResource = `/course-management/${this.courseId}/exams/${this.programmingExercise.exerciseGroup?.exam?.id}/`;
                this.teamBaseResource =
                    `/course-management/${this.courseId}/exams/${this.programmingExercise.exerciseGroup?.exam?.id}` +
                    `/exercise-groups/${this.programmingExercise.exerciseGroup?.id}/exercises/${this.programmingExercise.exerciseGroup?.exam?.id}/`;
            }

            this.programmingExerciseService.findWithTemplateAndSolutionParticipation(programmingExercise.id!, true).subscribe((updatedProgrammingExercise) => {
                this.programmingExercise = updatedProgrammingExercise.body!;

                // get the latest results for further processing
                if (this.programmingExercise.templateParticipation) {
                    const latestTemplateResult = this.getLatestResult(this.programmingExercise.templateParticipation.submissions);
                    if (latestTemplateResult) {
                        this.programmingExercise.templateParticipation.results = [latestTemplateResult];
                    }
                    // This is needed to access the exercise in the result details
                    this.programmingExercise.templateParticipation.programmingExercise = this.programmingExercise;
                }
                if (this.programmingExercise.solutionParticipation) {
                    const latestSolutionResult = this.getLatestResult(this.programmingExercise.solutionParticipation.submissions);
                    if (latestSolutionResult) {
                        this.programmingExercise.solutionParticipation.results = [latestSolutionResult];
                    }
                    // This is needed to access the exercise in the result details
                    this.programmingExercise.solutionParticipation.programmingExercise = this.programmingExercise;
                }
                this.setLatestCoveredLineRatio();
                this.loadingTemplateParticipationResults = false;
                this.loadingSolutionParticipationResults = false;

                this.profileService.getProfileInfo().subscribe((profileInfo) => {
                    if (profileInfo) {
                        if (this.programmingExercise.projectKey && this.programmingExercise.templateParticipation && this.programmingExercise.templateParticipation.buildPlanId) {
                            this.programmingExercise.templateParticipation.buildPlanUrl = createBuildPlanUrl(
                                profileInfo.buildPlanURLTemplate,
                                this.programmingExercise.projectKey,
                                this.programmingExercise.templateParticipation.buildPlanId,
                            );
                        }
                        if (this.programmingExercise.projectKey && this.programmingExercise.solutionParticipation && this.programmingExercise.solutionParticipation.buildPlanId) {
                            this.programmingExercise.solutionParticipation.buildPlanUrl = createBuildPlanUrl(
                                profileInfo.buildPlanURLTemplate,
                                this.programmingExercise.projectKey,
                                this.programmingExercise.solutionParticipation.buildPlanId,
                            );
                        }
                        this.supportsAuxiliaryRepositories = profileInfo.externalUserManagementName?.toLowerCase().includes('jira') ?? false;
                    }
                });

                this.programmingExerciseSubmissionPolicyService.getSubmissionPolicyOfProgrammingExercise(exerciseId!).subscribe((submissionPolicy) => {
                    if (submissionPolicy) {
                        this.programmingExercise.submissionPolicy = submissionPolicy;
                    }
                });

                this.loadGitDiffReport();

                this.programmingExerciseService.getBuildLogStatistics(exerciseId!).subscribe((buildLogStatisticsDto) => {
                    this.programmingExercise.buildLogStatistics = buildLogStatisticsDto;
                });

                this.setLatestCoveredLineRatio();

                this.plagiarismCheckSupported = this.programmingLanguageFeatureService.getProgrammingLanguageFeature(
                    programmingExercise.programmingLanguage,
                ).plagiarismCheckSupported;
            });

            this.statisticsService.getExerciseStatistics(exerciseId!).subscribe((statistics: ExerciseManagementStatisticsDto) => {
                this.doughnutStats = statistics;
            });
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    onParticipationChange(): void {
        this.loadGitDiffReport();
        this.setLatestCoveredLineRatio();
    }

    /**
     * returns the latest result within the submissions array or undefined, sorting is based on the submission date and the result order retrieved from the server
     *
     * @param submissions
     */
    getLatestResult(submissions?: Submission[]) {
        if (submissions && submissions.length > 0) {
            // important: sort to get the latest submission (the order of the server can be random)
            this.sortService.sortByProperty(submissions, 'submissionDate', true);
            const results = submissions.sort().last()?.results;
            if (results && results.length > 0) {
                return results.last();
            }
        }
    }

    combineTemplateCommits() {
        this.programmingExerciseService.combineTemplateRepositoryCommits(this.programmingExercise.id!).subscribe({
            next: () => {
                this.alertService.success('artemisApp.programmingExercise.combineTemplateCommitsSuccess');
            },
            error: () => {
                this.alertService.error('artemisApp.programmingExercise.combineTemplateCommitsError');
            },
        });
    }

    generateStructureOracle() {
        this.programmingExerciseService.generateStructureOracle(this.programmingExercise.id!).subscribe({
            next: (res) => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: res,
                    disableTranslation: true,
                });
            },
            error: (error) => {
                const errorMessage = error.headers.get('X-artemisApp-alert');
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    message: errorMessage,
                    disableTranslation: true,
                });
            },
        });
    }

    /**
     * Deletes the template and solution build plans and recreates them from scratch.
     */
    recreateBuildPlans() {
        this.programmingExerciseService.recreateBuildPlans(this.programmingExercise.id!).subscribe({
            next: (res) => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: res,
                    disableTranslation: true,
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Cleans up programming exercise
     * @param event contains additional checks from the dialog
     */
    cleanupProgrammingExercise(event: { [key: string]: boolean }) {
        return this.exerciseService.cleanup(this.programmingExercise.id!, event.deleteRepositories).subscribe({
            next: () => {
                if (event.deleteRepositories) {
                    this.alertService.success('artemisApp.programmingExercise.cleanup.successMessageWithRepositories');
                } else {
                    this.alertService.success('artemisApp.programmingExercise.cleanup.successMessage');
                }
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    deleteProgrammingExercise(event: { [key: string]: boolean }) {
        this.programmingExerciseService.delete(this.programmingExercise.id!, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'programmingExerciseListModification',
                    content: 'Deleted a programming exercise',
                });
                this.dialogErrorSource.next('');

                if (!this.isExamExercise) {
                    this.router.navigateByUrl(`/course-management/${this.courseId}/exercises`);
                } else {
                    this.router.navigateByUrl(`/course-management/${this.courseId}/exams/${this.programmingExercise.exerciseGroup?.exam?.id}/exercise-groups`);
                }
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Unlock all repositories immediately. Asks for confirmation.
     */
    handleUnlockAllRepositories() {
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.title = 'artemisApp.programmingExercise.unlockAllRepositories';
        modalRef.componentInstance.text = this.translateService.instant('artemisApp.programmingExercise.unlockAllRepositoriesModalText');
        modalRef.result.then(() => {
            this.unlockAllRepositories();
        });
    }

    /**
     * Unlocks all repositories that belong to the exercise
     */
    unlockAllRepositories() {
        this.lockingOrUnlockingRepositories = true;
        this.programmingExerciseService.unlockAllRepositories(this.programmingExercise.id!).subscribe({
            next: (res) => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'artemisApp.programmingExercise.unlockAllRepositoriesSuccess',
                    translationParams: { number: res?.body },
                });
                this.lockingOrUnlockingRepositories = false;
            },
            error: (err: HttpErrorResponse) => {
                this.lockingOrUnlockingRepositories = false;
                this.onError(err);
            },
        });
    }

    /**
     * Lock all repositories immediately. Asks for confirmation.
     */
    handleLockAllRepositories() {
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.title = 'artemisApp.programmingExercise.lockAllRepositories';
        modalRef.componentInstance.text = this.translateService.instant('artemisApp.programmingExercise.lockAllRepositoriesModalText');
        modalRef.result.then(() => {
            this.lockAllRepositories();
        });
    }

    /**
     * Locks all repositories that belong to the exercise
     */
    private lockAllRepositories() {
        this.lockingOrUnlockingRepositories = true;
        this.programmingExerciseService.lockAllRepositories(this.programmingExercise.id!).subscribe({
            next: (res) => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'artemisApp.programmingExercise.lockAllRepositoriesSuccess',
                    translationParams: { number: res?.body },
                });
                this.lockingOrUnlockingRepositories = false;
            },
            error: (err: HttpErrorResponse) => {
                this.lockingOrUnlockingRepositories = false;
                this.onError(err);
            },
        });
    }

    /**
     * Opens modal and executes a consistency check for the given programming exercise
     * @param exercise the programming exercise to check
     */
    checkConsistencies(exercise: ProgrammingExercise) {
        const modalRef = this.modalService.open(ConsistencyCheckComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.exercisesToCheck = Array.of(exercise);
    }

    private onError(error: HttpErrorResponse) {
        this.alertService.error(error.message);
    }

    /**
     * Generates the link to any participation's submissions, used for the link to template and solution submissions
     * @param participationId of the participation
     */
    getParticipationSubmissionLink(participationId: number) {
        const link = [this.baseResource, 'participations', participationId];
        // For unknown reason normal exercises append /submissions to the submission view whereas exam exercises do not
        if (!this.isExamExercise) {
            link.push('submissions');
        }
        return link;
    }

    loadGitDiffReport(): void {
        this.programmingExerciseService.getDiffReport(this.programmingExercise.id!).subscribe((gitDiffReport) => {
            if (gitDiffReport) {
                this.programmingExercise.gitDiffReport = gitDiffReport;
                gitDiffReport.programmingExercise = this.programmingExercise;
                this.addedLineCount = gitDiffReport.entries
                    .map((entry) => entry.lineCount)
                    .filter((lineCount) => lineCount)
                    .map((lineCount) => lineCount!)
                    .reduce((lineCount1, lineCount2) => lineCount1 + lineCount2, 0);
                this.removedLineCount = gitDiffReport.entries
                    .map((entry) => entry.previousLineCount)
                    .filter((lineCount) => lineCount)
                    .map((lineCount) => lineCount!)
                    .reduce((lineCount1, lineCount2) => lineCount1 + lineCount2, 0);
            }
        });
    }

    /**
     * Shows the git-diff in a modal.
     */
    showGitDiff(): void {
        const modalRef = this.modalService.open(GitDiffReportModalComponent, { size: 'xl' });
        modalRef.componentInstance.report = this.programmingExercise.gitDiffReport;
    }

    createStructuralSolutionEntries() {
        this.programmingExerciseService.createStructuralSolutionEntries(this.programmingExercise.id!).subscribe({
            next: () => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'artemisApp.programmingExercise.createStructuralSolutionEntriesSuccess',
                });
            },
            error: (err) => {
                this.onError(err);
            },
        });
    }

    createBehavioralSolutionEntries() {
        this.programmingExerciseService.createBehavioralSolutionEntries(this.programmingExercise.id!).subscribe({
            next: () => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'artemisApp.programmingExercise.createBehavioralSolutionEntriesSuccess',
                });
            },
            error: (err) => {
                this.onError(err);
            },
        });
    }

    /**
     * Returns undefined if the last solution submission was not successful or no report exists yet
     */
    private setLatestCoveredLineRatio() {
        const latestSolutionSubmissionSuccessful = this.getLatestResult(this.programmingExercise?.solutionParticipation?.submissions)?.successful;
        if (this.programmingExercise.testwiseCoverageEnabled && !!latestSolutionSubmissionSuccessful) {
            this.programmingExerciseService.getLatestTestwiseCoverageReport(this.programmingExercise.id!).subscribe((coverageReport) => {
                this.programmingExercise.coveredLinesRatio = coverageReport.coveredLineRatio;
            });
        }
    }

    /**
     * Gets the testwise coverage reports from the server and displays it in a modal.
     */
    getAndShowTestwiseCoverage() {
        this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(this.programmingExercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.programmingExerciseService.getLatestFullTestwiseCoverageReport(this.programmingExercise.id!).subscribe({
                    next: (coverageReport) => {
                        const modalRef = this.modalService.open(TestwiseCoverageReportModalComponent, {
                            size: 'xl',
                            backdrop: 'static',
                        });
                        modalRef.componentInstance.report = coverageReport;
                        modalRef.componentInstance.fileContentByPath = response;
                    },
                    error: (err: HttpErrorResponse) => {
                        if (err.status === 404) {
                            this.alertService.error('artemisApp.programmingExercise.testwiseCoverageReport.404');
                        } else {
                            this.onError(err);
                        }
                    },
                });
            },
        });
    }
}
