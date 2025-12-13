import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { Subject, Subscription } from 'rxjs';
import { ParticipationService } from './participation.service';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StudentParticipation, isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming/shared/services/programming-submission.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { HttpErrorResponse } from '@angular/common/http';
import { tap } from 'rxjs/operators';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { formatTeamAsSearchResult } from 'app/exercise/team/team.utils';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { faCircleNotch, faEraser, faFilePowerpoint, faTable, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { GradingSystemService } from 'app/assessment/manage/grading-system/grading-system.service';
import { GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ProgrammingExerciseInstructorSubmissionStateComponent } from 'app/programming/shared/actions/instructor-submission-state/programming-exercise-instructor-submission-state.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { CodeButtonComponent } from 'app/shared/components/buttons/code-button/code-button.component';
import { TeamStudentsListComponent } from 'app/exercise/team/team-participate/team-students-list.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { isProgrammingExerciseStudentParticipation } from 'app/programming/shared/utils/programming-exercise.utils';

enum FilterProp {
    ALL = 'all',
    FAILED = 'failed',
    NO_SUBMISSIONS = 'no-submissions',
    NO_PRACTICE = 'no-practice',
}

@Component({
    selector: 'jhi-participation',
    templateUrl: './participation.component.html',
    imports: [
        TranslateDirective,
        FormsModule,
        ProgrammingExerciseInstructorSubmissionStateComponent,
        RouterLink,
        FaIconComponent,
        DataTableComponent,
        NgxDatatableModule,
        CodeButtonComponent,
        TeamStudentsListComponent,
        FormDateTimePickerComponent,
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        DeleteButtonDirective,
        FeatureToggleDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
})
export class ParticipationComponent implements OnInit, OnDestroy {
    private readonly route = inject(ActivatedRoute);
    private readonly participationService = inject(ParticipationService);
    private readonly alertService = inject(AlertService);
    private readonly eventManager = inject(EventManager);
    private readonly exerciseService = inject(ExerciseService);
    private readonly programmingSubmissionService = inject(ProgrammingSubmissionService);
    private readonly accountService = inject(AccountService);
    private readonly profileService = inject(ProfileService);
    private readonly gradingSystemService = inject(GradingSystemService);

    protected readonly faTable = faTable;
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faEraser = faEraser;
    protected readonly faFilePowerpoint = faFilePowerpoint;

    protected readonly ExerciseType = ExerciseType;
    protected readonly ActionType = ActionType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly RepositoryType = RepositoryType;
    readonly FilterProp = FilterProp;

    participations: StudentParticipation[] = [];
    participationsChangedDueDate: Map<number, StudentParticipation> = new Map<number, StudentParticipation>();
    participationsChangedPresentation: Map<number, StudentParticipation> = new Map<number, StudentParticipation>();
    filteredParticipationsSize = 0;
    eventSubscriber: Subscription;
    paramSub: Subscription;
    exercise: Exercise;
    hasLoadedPendingSubmissions = false;
    basicPresentationEnabled = false;
    gradedPresentationEnabled = false;

    gradeStepsDTO?: GradeStepsDTO;
    gradeStepsDTOSub: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    participationCriteria: {
        filterProp: FilterProp;
    };

    exerciseSubmissionState: ExerciseSubmissionState;

    isLocalCIEnabled = true;
    isAdmin = false;
    isLoading: boolean;
    isSaving: boolean;
    afterDueDate = false;

    constructor() {
        this.participationCriteria = {
            filterProp: FilterProp.ALL,
        };
    }

    /**
     * Initialize component by calling loadAll and registerChangeInParticipation
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => this.loadExercise(+params['exerciseId']));
        this.registerChangeInParticipations();
        this.isAdmin = this.accountService.isAdmin();
        this.isLocalCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
    }

    /**
     * Unsubscribe from all subscriptions and destroy eventSubscriber
     */
    ngOnDestroy() {
        this.programmingSubmissionService.unsubscribeAllWebsocketTopics(this.exercise);
        this.eventManager.destroy(this.eventSubscriber);
        this.dialogErrorSource.unsubscribe();
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        if (this.gradeStepsDTOSub) {
            this.gradeStepsDTOSub.unsubscribe();
        }
    }

    private loadExercise(exerciseId: number) {
        this.isLoading = true;
        this.hasLoadedPendingSubmissions = false;
        this.exerciseService.find(exerciseId).subscribe((exerciseResponse) => {
            this.exercise = exerciseResponse.body!;
            this.afterDueDate = !!this.exercise.dueDate && dayjs().isAfter(this.exercise.dueDate);
            this.loadGradingScale(this.exercise.course?.id);
            this.loadParticipations(exerciseId);
            if (this.exercise.type === ExerciseType.PROGRAMMING) {
                this.loadSubmissions(exerciseId);
            }
            this.basicPresentationEnabled = this.checkBasicPresentationConfig();
        });
    }

    private loadGradingScale(courseId?: number) {
        if (courseId) {
            this.gradeStepsDTOSub = this.gradingSystemService.findGradeStepsForCourse(courseId).subscribe((gradeStepsDTO) => {
                if (gradeStepsDTO.body) {
                    this.gradeStepsDTO = gradeStepsDTO.body;
                }
                this.gradedPresentationEnabled = this.checkGradedPresentationConfig();
            });
        }
    }

    private loadParticipations(exerciseId: number) {
        this.participationService.findAllParticipationsByExercise(exerciseId, false).subscribe((participationsResponse) => {
            this.participations = participationsResponse.body!;
            this.isLoading = false;
        });
    }

    private loadSubmissions(exerciseId: number) {
        this.programmingSubmissionService
            .getSubmissionStateOfExercise(exerciseId)
            .pipe(
                tap((exerciseSubmissionState: ExerciseSubmissionState) => {
                    this.exerciseSubmissionState = exerciseSubmissionState;
                }),
            )
            .subscribe(() => (this.hasLoadedPendingSubmissions = true));
    }

    updateParticipationFilter(newValue: FilterProp) {
        this.isLoading = true;
        setTimeout(() => {
            this.participationCriteria.filterProp = newValue;
            this.isLoading = false;
        });
    }

    filterParticipationByProp = (participation: StudentParticipation) => {
        switch (this.participationCriteria.filterProp) {
            case FilterProp.FAILED:
                return this.hasFailedSubmission(participation);
            case FilterProp.NO_SUBMISSIONS:
                return participation.submissionCount === 0;
            case FilterProp.NO_PRACTICE:
                return !isPracticeMode(participation);
            case FilterProp.ALL:
            default:
                return true;
        }
    };

    private hasFailedSubmission(participation: StudentParticipation) {
        const submissionStateObj = this.exerciseSubmissionState[participation.id!];
        if (submissionStateObj) {
            const { submissionState } = submissionStateObj;
            return submissionState === ProgrammingSubmissionState.HAS_FAILED_SUBMISSION;
        }
        return false;
    }

    trackId(index: number, item: StudentParticipation) {
        return item.id;
    }

    registerChangeInParticipations() {
        this.eventSubscriber = this.eventManager.subscribe('participationListModification', () => this.loadExercise(this.exercise.id!));
    }

    checkBasicPresentationConfig(): boolean {
        if (!this.exercise.course) {
            return false;
        }
        return this.exercise.isAtLeastTutor === true && (this.exercise.course.presentationScore ?? 0) > 0 && this.exercise.presentationScoreEnabled === true;
    }

    checkGradedPresentationConfig(): boolean {
        return !!(this.exercise.course && this.exercise.isAtLeastTutor && (this.gradeStepsDTO?.presentationsNumber ?? 0) > 0 && this.exercise.presentationScoreEnabled === true);
    }

    addBasicPresentation(participation: StudentParticipation) {
        if (!this.basicPresentationEnabled) {
            return;
        }
        participation.presentationScore = 1;
        this.participationService.update(this.exercise, participation).subscribe({
            error: () => this.alertService.error('artemisApp.participation.addPresentation.error'),
        });
    }

    addGradedPresentation(participation: StudentParticipation) {
        if (!this.gradedPresentationEnabled || (participation.presentationScore ?? 0) > 100 || (participation.presentationScore ?? 0) < 0) {
            return;
        }
        this.participationService.update(this.exercise, participation).subscribe({
            error: (res: HttpErrorResponse) => {
                const error = res.error;
                if (error && error.errorKey && error.errorKey === 'invalid.presentations.maxNumberOfPresentationsExceeded') {
                    participation.presentationScore = undefined;
                } else {
                    this.alertService.error('artemisApp.participation.savePresentation.error');
                }
            },

            complete: () => {
                this.participationsChangedPresentation.delete(participation.id!);
            },
        });
    }

    hasGradedPresentationChanged(participation: StudentParticipation) {
        return this.participationsChangedPresentation.has(participation.id!);
    }

    changeGradedPresentation(participation: StudentParticipation) {
        this.participationsChangedPresentation.set(participation.id!, participation);
    }

    removePresentation(participation: StudentParticipation) {
        if (!this.basicPresentationEnabled && !this.gradedPresentationEnabled) {
            return;
        }
        participation.presentationScore = undefined;
        this.participationService.update(this.exercise, participation).subscribe({
            error: () => this.alertService.error('artemisApp.participation.removePresentation.error'),
        });
    }

    /**
     * Save that the due date of the given participation has changed.
     *
     * Does not issue an immediate update of the due date to the server.
     * The actual update is performed with {@link saveChangedDueDates}.
     * @param participation of which the individual due date has changed.
     */
    changedIndividualDueDate(participation: StudentParticipation) {
        this.participationsChangedDueDate.set(participation.id!, participation);
    }

    /**
     * Removes the individual due date from the given participation.
     *
     * Does not issue an immediate update of the due date to the server.
     * The actual update is performed with {@link saveChangedDueDates}.
     * @param participation of which the individual due date should be removed.
     */
    removeIndividualDueDate(participation: StudentParticipation) {
        participation.individualDueDate = undefined;
        this.participationsChangedDueDate.set(participation.id!, participation);
    }

    /**
     * Saves the updated individual due dates for all participants.
     *
     * Changes are not updated directly when changing just a single due date, as
     * an update here might require a full update of the scheduling of the
     * exercise. Therefore, an explicit save action which can also update the
     * due date for multiple participants at the same time is preferred.
     */
    saveChangedDueDates() {
        this.isSaving = true;

        const changedParticipations = Array.from(this.participationsChangedDueDate.values());
        this.participationService.updateIndividualDueDates(this.exercise, changedParticipations).subscribe({
            next: (response) => {
                response.body!.forEach((updatedParticipation) => {
                    const changedIndex = this.participations.findIndex((participation) => participation.id! === updatedParticipation.id!);
                    this.participations[changedIndex] = updatedParticipation;
                });

                this.participationsChangedDueDate.clear();
                this.isSaving = false;
                this.alertService.success('artemisApp.participation.updateDueDates.success', { count: response.body!.length });
            },
            error: () => {
                this.alertService.error('artemisApp.participation.updateDueDates.error');
                this.isSaving = false;
            },
        });
    }

    /**
     * Deletes participation
     * @param participationId the id of the participation that we want to delete
     */
    deleteParticipation(participationId: number) {
        const deleteBuildPlan = true;
        const deleteRepository = true;
        this.participationService.delete(participationId, { deleteBuildPlan, deleteRepository }).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'participationListModification',
                    content: 'Deleted an participation',
                });
                this.dialogErrorSource.next('');
                this.participationsChangedDueDate.delete(participationId);
                this.participationsChangedPresentation.delete(participationId);
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Cleans programming exercise participation
     * @param programmingExerciseParticipation the id of the participation that we want to delete
     */
    cleanupProgrammingExerciseParticipation(programmingExerciseParticipation: StudentParticipation) {
        this.participationService.cleanupBuildPlan(programmingExerciseParticipation).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'participationListModification',
                    content: 'Cleanup the build plan of an participation',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Update the number of filtered participations
     *
     * @param filteredParticipationsSize Total number of participations after filters have been applied
     */
    handleParticipationsSizeChange = (filteredParticipationsSize: number) => {
        this.filteredParticipationsSize = filteredParticipationsSize;
    };

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param participation
     */
    searchResultFormatter = (participation: StudentParticipation) => {
        if (participation.student) {
            const { login, name } = participation.student;
            return `${login} (${name})`;
        } else if (participation.team) {
            return formatTeamAsSearchResult(participation.team);
        }
        return `${participation.id!}`;
    };

    /**
     * Converts a participation object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param participation Student participation
     */
    searchTextFromParticipation = (participation: StudentParticipation): string => {
        return participation.student?.login || participation.team?.shortName || '';
    };

    /**
     * Removes the login from the repositoryUri
     *
     * @param participation Student participation
     * @param repoUri original repository uri
     */
    getRepositoryLink = (participation: Participation, repoUri: string) => {
        if (isProgrammingExerciseStudentParticipation(participation) && participation.repositoryUri === repoUri) {
            return participation.userIndependentRepositoryUri;
        }
        return repoUri;
    };

    /**
     * Get the route for the exercise's scores page.
     *
     * @param exercise the exercise for which the scores route should be extracted
     */
    getScoresRoute(exercise: Exercise): any[] {
        let route: any[] = ['/course-management'];
        const exam = exercise.exerciseGroup?.exam;
        if (exam) {
            route = [...route, exam.course!.id, 'exams', exam.id, 'exercise-groups', exercise.exerciseGroup!.id];
        } else {
            route = [...route, exercise.course!.id];
        }
        route = [...route, exercise.type + '-exercises', exercise.id, 'scores'];
        return route;
    }
}
