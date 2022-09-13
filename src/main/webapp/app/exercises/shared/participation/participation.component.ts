import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { Participation } from 'app/entities/participation/participation.model';
import { ParticipationService } from './participation.service';
import { ActivatedRoute } from '@angular/router';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/exercises/programming/participate/programming-submission.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { HttpErrorResponse } from '@angular/common/http';
import { tap } from 'rxjs/operators';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { areManualResultsAllowed } from 'app/exercises/shared/exercise/exercise.utils';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { formatTeamAsSearchResult } from 'app/exercises/shared/team/team.utils';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { defaultLongDateTimeFormat } from 'app/shared/pipes/artemis-date.pipe';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { setBuildPlanUrlForProgrammingParticipations } from 'app/exercises/shared/participation/participation.utils';
import { faCircleNotch, faEraser, faFilePowerpoint, faTimes } from '@fortawesome/free-solid-svg-icons';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

enum FilterProp {
    ALL = 'all',
    FAILED = 'failed',
    NO_SUBMISSIONS = 'no-submissions',
}

@Component({
    selector: 'jhi-participation',
    templateUrl: './participation.component.html',
})
export class ParticipationComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly FilterProp = FilterProp;

    readonly ExerciseType = ExerciseType;
    readonly ActionType = ActionType;
    readonly FeatureToggle = FeatureToggle;

    participations: StudentParticipation[] = [];
    participationsChangedDueDate: Map<number, StudentParticipation> = new Map<number, StudentParticipation>();
    filteredParticipationsSize = 0;
    eventSubscriber: Subscription;
    paramSub: Subscription;
    exercise: Exercise;
    newManualResultAllowed: boolean;
    hasLoadedPendingSubmissions = false;
    presentationScoreEnabled = false;

    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    participationCriteria: {
        filterProp: FilterProp;
    };

    exerciseSubmissionState: ExerciseSubmissionState;

    isAdmin = false;

    isLoading: boolean;

    isSaving: boolean;

    public practiceMode = false;

    // Icons
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faEraser = faEraser;
    faFilePowerpoint = faFilePowerpoint;

    constructor(
        private route: ActivatedRoute,
        private participationService: ParticipationService,
        private alertService: AlertService,
        private eventManager: EventManager,
        private exerciseService: ExerciseService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private accountService: AccountService,
        private profileService: ProfileService,
    ) {
        this.participationCriteria = {
            filterProp: FilterProp.ALL,
        };
    }

    /**
     * Initialize component by calling loadAll and registerChangeInParticipation
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => this.loadExercise(params['exerciseId']));
        this.registerChangeInParticipations();
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
    }

    public isPracticeModeAvailable(): boolean {
        switch (this.exercise.type) {
            case ExerciseType.QUIZ:
                const quizExercise: QuizExercise = this.exercise as QuizExercise;
                return quizExercise.isOpenForPractice! && quizExercise.quizEnded!;
            case ExerciseType.PROGRAMMING:
                const programmingExercise: ProgrammingExercise = this.exercise as ProgrammingExercise;
                return dayjs().isAfter(dayjs(programmingExercise.dueDate));
            default:
                return false;
        }
    }

    public isInPracticeMode(): boolean {
        return this.practiceMode;
    }

    public togglePracticeMode(toggle: boolean): void {
        if (this.isPracticeModeAvailable()) {
            this.practiceMode = toggle;
            this.ngOnInit();
        }
    }

    private loadExercise(exerciseId: number) {
        this.isLoading = true;
        this.hasLoadedPendingSubmissions = false;
        this.exerciseService.find(exerciseId).subscribe((exerciseResponse) => {
            this.exercise = exerciseResponse.body!;
            this.loadParticipations(exerciseId);
            if (this.exercise.type === ExerciseType.PROGRAMMING) {
                this.loadSubmissions(exerciseId);
            }
            this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
            this.presentationScoreEnabled = this.checkPresentationScoreConfig();
            this.isAdmin = this.accountService.isAdmin();
        });
    }

    private loadParticipations(exerciseId: number) {
        this.participationService.findAllParticipationsByExercise(exerciseId, true).subscribe((participationsResponse) => {
            this.participations = participationsResponse.body!;
            switch (this.exercise.type) {
                case ExerciseType.QUIZ:
                case ExerciseType.PROGRAMMING:
                    this.participations = this.participations.filter((participation) => participation!['testRun'] === this.practiceMode);
                    break;
                default:
                    break;
            }
            if (this.exercise.type === ExerciseType.PROGRAMMING) {
                const programmingExercise = this.exercise as ProgrammingExercise;
                if (programmingExercise.projectKey) {
                    this.profileService.getProfileInfo().subscribe((profileInfo) => {
                        setBuildPlanUrlForProgrammingParticipations(profileInfo, this.participations, (this.exercise as ProgrammingExercise).projectKey);
                    });
                }
            }
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

    formatDate(date: dayjs.Dayjs | Date | undefined) {
        // TODO: we should try to use the artemis date pipe here
        return date ? dayjs(date).format(defaultLongDateTimeFormat) : '';
    }

    updateParticipationFilter(newValue: FilterProp) {
        this.isLoading = true;
        setTimeout(() => {
            this.participationCriteria.filterProp = newValue;
            this.isLoading = false;
        });
    }

    filterParticipationByProp = (participation: Participation) => {
        switch (this.participationCriteria.filterProp) {
            case FilterProp.FAILED:
                return this.hasFailedSubmission(participation);
            case FilterProp.NO_SUBMISSIONS:
                return participation.submissionCount === 0;
            case FilterProp.ALL:
            default:
                return true;
        }
    };

    private hasFailedSubmission(participation: Participation) {
        const submissionStateObj = this.exerciseSubmissionState[participation.id!];
        if (submissionStateObj) {
            const { submissionState } = submissionStateObj;
            return submissionState === ProgrammingSubmissionState.HAS_FAILED_SUBMISSION;
        }
        return false;
    }

    trackId(index: number, item: Participation) {
        return item.id;
    }

    registerChangeInParticipations() {
        this.eventSubscriber = this.eventManager.subscribe('participationListModification', () => this.loadExercise(this.exercise.id!));
    }

    checkPresentationScoreConfig(): boolean {
        if (!this.exercise.course) {
            return false;
        }
        return this.exercise.isAtLeastTutor === true && this.exercise.course.presentationScore !== 0 && this.exercise.presentationScoreEnabled === true;
    }

    addPresentation(participation: StudentParticipation) {
        if (!this.presentationScoreEnabled) {
            return;
        }
        participation.presentationScore = 1;
        this.participationService.update(this.exercise, participation).subscribe({
            error: () => this.alertService.error('artemisApp.participation.addPresentation.error'),
        });
    }

    removePresentation(participation: StudentParticipation) {
        if (!this.presentationScoreEnabled) {
            return;
        }
        participation.presentationScore = 0;
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
     * @param event passed from delete dialog to represent if checkboxes were checked
     */
    deleteParticipation(participationId: number, event: { [key: string]: boolean }) {
        const deleteBuildPlan = event.deleteBuildPlan ? event.deleteBuildPlan : false;
        const deleteRepository = event.deleteRepository ? event.deleteRepository : false;
        this.participationService.delete(participationId, { deleteBuildPlan, deleteRepository }).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'participationListModification',
                    content: 'Deleted an participation',
                });
                this.dialogErrorSource.next('');
                this.participationsChangedDueDate.delete(participationId);
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
     * Removes the login from the repositoryURL
     *
     * @param participation Student participation
     * @param repoUrl original repository url
     */
    getRepositoryLink = (participation: StudentParticipation, repoUrl: string) => {
        if ((participation as ProgrammingExerciseStudentParticipation).repositoryUrl === repoUrl) {
            return (participation as ProgrammingExerciseStudentParticipation).userIndependentRepositoryUrl;
        }
        return repoUrl;
    };
}
