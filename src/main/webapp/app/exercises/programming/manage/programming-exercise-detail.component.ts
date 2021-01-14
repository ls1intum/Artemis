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

    programmingExercise: ProgrammingExercise;
    isExamExercise: boolean;

    loadingTemplateParticipationResults = true;
    loadingSolutionParticipationResults = true;
    lockingOrUnlockingRepositories = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    checkPlagiarismInProgress: boolean;

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
    ) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
            this.isExamExercise = !!this.programmingExercise.exerciseGroup;

            this.programmingExercise.isAtLeastTutor = this.accountService.isAtLeastTutorForExercise(this.programmingExercise);
            this.programmingExercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorForExercise(this.programmingExercise);

            if (this.programmingExercise.categories) {
                this.programmingExercise.categories = this.programmingExercise.categories.map((category) => JSON.parse(category));
            }

            if (this.programmingExercise.templateParticipation) {
                this.programmingExercise.templateParticipation.programmingExercise = this.programmingExercise;
                this.loadLatestResultWithFeedback(this.programmingExercise.templateParticipation.id!).subscribe((results) => {
                    this.programmingExercise.templateParticipation!.results = results;
                    this.loadingTemplateParticipationResults = false;
                });
            }

            if (this.programmingExercise.solutionParticipation) {
                this.programmingExercise.solutionParticipation.programmingExercise = this.programmingExercise;

                this.loadLatestResultWithFeedback(this.programmingExercise.solutionParticipation.id!).subscribe((results) => {
                    this.programmingExercise.solutionParticipation!.results = results;
                    this.loadingSolutionParticipationResults = false;
                });
            }
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Load the latest result for the given participation. Will return [result] if there is a result, [] if not.
     * @param participationId of the given participation.
     * @return an empty array if there is no result or an array with the single latest result.
     */
    private loadLatestResultWithFeedback(participationId: number): Observable<Result[]> {
        return this.programmingExerciseParticipationService.getLatestResultWithFeedback(participationId).pipe(
            catchError(() => of(null)),
            map((result: Result | null) => {
                return result ? [result] : [];
            }),
        );
    }

    /**
     * Returns the route for editing the exercise. Exam and course exercises have different routes.
     */
    getEditRoute() {
        if (this.isExamExercise) {
            return [
                '/course-management',
                this.programmingExercise.exerciseGroup?.exam?.course?.id,
                'exams',
                this.programmingExercise.exerciseGroup?.exam?.id,
                'exercise-groups',
                this.programmingExercise.exerciseGroup?.id,
                'programming-exercises',
                this.programmingExercise.id,
                'edit',
            ];
        } else {
            return ['/course-management', this.programmingExercise.course?.id, 'programming-exercises', this.programmingExercise.id, 'edit'];
        }
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

    /**
     * Cleans up programming exercise
     * @param $event contains additional checks from the dialog
     */
    cleanupProgrammingExercise($event: { [key: string]: boolean }) {
        return this.exerciseService.cleanup(this.programmingExercise.id!, $event.deleteRepositories).subscribe(
            () => {
                if ($event.deleteRepositories) {
                    this.jhiAlertService.success('artemisApp.programmingExercise.cleanup.successMessageWithRepositories');
                } else {
                    this.jhiAlertService.success('artemisApp.programmingExercise.cleanup.successMessage');
                }
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    public deleteProgrammingExercise($event: { [key: string]: boolean }) {
        this.programmingExerciseService.delete(this.programmingExercise.id!, $event.deleteStudentReposBuildPlans, $event.deleteBaseReposBuildPlans).subscribe(
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
