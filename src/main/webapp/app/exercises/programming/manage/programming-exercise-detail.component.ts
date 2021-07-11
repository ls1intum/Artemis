import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable, of, Subject } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Result } from 'app/entities/result.model';
import { JhiAlertService } from 'ng-jhipster';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming-exercise-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { JhiEventManager } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-button.component';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import * as moment from 'moment';
import { AssessmentType } from 'app/entities/assessment-type.model';

@Component({
    selector: 'jhi-programming-exercise-detail',
    templateUrl: './programming-exercise-detail.component.html',
    styleUrls: ['./programming-exercise-detail.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ProgrammingExerciseDetailComponent implements OnInit, OnDestroy {
    readonly ActionType = ActionType;
    readonly ProgrammingExerciseParticipationType = ProgrammingExerciseParticipationType;
    readonly FeatureToggle = FeatureToggle;
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly moment = moment;
    assessmentType = AssessmentType.SEMI_AUTOMATIC;
    programmingExercise: ProgrammingExercise;
    isExamExercise: boolean;
    supportsAuxiliaryRepositories: boolean;
    baseResource: string;
    shortBaseResource: string;
    loadingTemplateParticipationResults = true;
    loadingSolutionParticipationResults = true;
    lockingOrUnlockingRepositories = false;
    courseId: number;
    doughnutStats: ExerciseManagementStatisticsDto;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private activatedRoute: ActivatedRoute,
        private accountService: AccountService,
        private programmingExerciseService: ProgrammingExerciseService,
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private eventManager: JhiEventManager,
        private modalService: NgbModal,
        private translateService: TranslateService,
        private profileService: ProfileService,
        private statisticsService: StatisticsService,
    ) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
            this.isExamExercise = !!this.programmingExercise.exerciseGroup;
            this.courseId = this.isExamExercise ? this.programmingExercise.exerciseGroup!.exam!.course!.id! : this.programmingExercise.course!.id!;
            this.programmingExercise.isAtLeastTutor = this.accountService.isAtLeastTutorForExercise(this.programmingExercise);
            this.programmingExercise.isAtLeastEditor = this.accountService.isAtLeastEditorForExercise(this.programmingExercise);
            this.programmingExercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorForExercise(this.programmingExercise);

            this.programmingExerciseService.findWithTemplateAndSolutionParticipation(programmingExercise.id!, true).subscribe((updatedProgrammingExercise) => {
                // TODO: the feedback would be missing here, is that a problem?
                this.programmingExercise = updatedProgrammingExercise.body!;
                // get the latest results for further processing
                if (this.programmingExercise.templateParticipation) {
                    const templateSubmissions = this.programmingExercise.templateParticipation.submissions;
                    if (templateSubmissions && templateSubmissions.length > 0) {
                        this.programmingExercise.templateParticipation.results = templateSubmissions[templateSubmissions.length - 1].results;
                    }
                }
                if (this.programmingExercise.solutionParticipation) {
                    const solutionSubmissions = this.programmingExercise.solutionParticipation.submissions;
                    if (solutionSubmissions && solutionSubmissions.length > 0) {
                        this.programmingExercise.solutionParticipation.results = solutionSubmissions[solutionSubmissions.length - 1].results;
                    }
                }
                this.loadingTemplateParticipationResults = false;
                this.loadingSolutionParticipationResults = false;
            });

            this.statisticsService.getExerciseStatistics(programmingExercise.id).subscribe((statistics: ExerciseManagementStatisticsDto) => {
                this.doughnutStats = statistics;
            });
            if (!this.isExamExercise) {
                this.baseResource = `/course-management/${this.courseId}/programming-exercises/${programmingExercise.id}/`;
                this.shortBaseResource = `/course-management/${this.courseId}/`;
            } else {
                this.baseResource =
                    `/course-management/${this.courseId}/exams/${this.programmingExercise.exerciseGroup?.exam?.id}` +
                    `/exercise-groups/${this.programmingExercise.exerciseGroup?.id}/programming-exercises/${this.programmingExercise.id}/`;
                this.shortBaseResource = `/course-management/${this.courseId}/exams/${this.programmingExercise.exerciseGroup?.exam?.id}/`;
            }
        });

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.supportsAuxiliaryRepositories = profileInfo.externalUserManagementName?.toLowerCase().includes('jira') ?? false;
            }
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    combineTemplateCommits() {
        this.programmingExerciseService.combineTemplateRepositoryCommits(this.programmingExercise.id!).subscribe(
            () => {
                this.jhiAlertService.success('artemisApp.programmingExercise.combineTemplateCommitsSuccess');
            },
            () => {
                this.jhiAlertService.error('artemisApp.programmingExercise.combineTemplateCommitsError');
            },
        );
    }

    generateStructureOracle() {
        this.programmingExerciseService.generateStructureOracle(this.programmingExercise.id!).subscribe(
            (res) => {
                const jhiAlert = this.jhiAlertService.success(res);
                jhiAlert.msg = res;
            },
            (error) => {
                const errorMessage = error.headers.get('X-artemisApp-alert');
                // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
                const jhiAlert = this.jhiAlertService.error(errorMessage);
                jhiAlert.msg = errorMessage;
            },
        );
    }

    recreateBuildPlans() {
        this.programmingExerciseService.recreateBuildPlans(this.programmingExercise.id!).subscribe(
            (res) => {
                const jhiAlert = this.jhiAlertService.success(res);
                jhiAlert.msg = res;
            },
            (error) => {
                const errorMessage = error.headers.get('X-artemisApp-alert');
                // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
                const jhiAlert = this.jhiAlertService.error(errorMessage);
                jhiAlert.msg = errorMessage;
            },
        );
    }

    /**
     * Cleans up programming exercise
     * @param event contains additional checks from the dialog
     */
    cleanupProgrammingExercise(event: { [key: string]: boolean }) {
        return this.exerciseService.cleanup(this.programmingExercise.id!, event.deleteRepositories).subscribe(
            () => {
                if (event.deleteRepositories) {
                    this.jhiAlertService.success('artemisApp.programmingExercise.cleanup.successMessageWithRepositories');
                } else {
                    this.jhiAlertService.success('artemisApp.programmingExercise.cleanup.successMessage');
                }
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    public deleteProgrammingExercise(event: { [key: string]: boolean }) {
        this.programmingExerciseService.delete(this.programmingExercise.id!, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'programmingExerciseListModification',
                    content: 'Deleted a programming exercise',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
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
    private unlockAllRepositories() {
        this.lockingOrUnlockingRepositories = true;
        this.programmingExerciseService.unlockAllRepositories(this.programmingExercise.id!).subscribe(
            (res) => {
                this.jhiAlertService.addAlert(
                    {
                        type: 'success',
                        msg: 'artemisApp.programmingExercise.unlockAllRepositoriesSuccess',
                        params: { number: res?.body },
                        timeout: 10000,
                    },
                    [],
                );
                this.lockingOrUnlockingRepositories = false;
            },
            (err: HttpErrorResponse) => {
                this.lockingOrUnlockingRepositories = false;
                this.onError(err);
            },
        );
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
        this.programmingExerciseService.lockAllRepositories(this.programmingExercise.id!).subscribe(
            (res) => {
                this.jhiAlertService.addAlert(
                    {
                        type: 'success',
                        msg: 'artemisApp.programmingExercise.lockAllRepositoriesSuccess',
                        params: { number: res?.body },
                        timeout: 10000,
                    },
                    [],
                );
                this.lockingOrUnlockingRepositories = false;
            },
            (err: HttpErrorResponse) => {
                this.lockingOrUnlockingRepositories = false;
                this.onError(err);
            },
        );
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }
}
