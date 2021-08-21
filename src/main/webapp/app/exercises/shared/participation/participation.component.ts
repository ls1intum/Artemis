import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { Participation } from 'app/entities/participation/participation.model';
import { ParticipationService } from './participation.service';
import { ActivatedRoute } from '@angular/router';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/exercises/programming/participate/programming-submission.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { HttpErrorResponse } from '@angular/common/http';
import { tap } from 'rxjs/operators';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { areManualResultsAllowed } from 'app/exercises/shared/exercise/exercise-utils';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { formatTeamAsSearchResult } from 'app/exercises/shared/team/team.utils';
import { AccountService } from 'app/core/auth/account.service';
import * as moment from 'moment';
import { Moment } from 'moment';
import { defaultLongDateTimeFormat } from 'app/shared/pipes/artemis-date.pipe';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { setBuildPlanUrlForProgrammingParticipation } from 'app/exercises/shared/participation/participation.utils';

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

    constructor(
        private route: ActivatedRoute,
        private participationService: ParticipationService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
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
        this.loadAll();
        this.registerChangeInParticipations();
    }

    /**
     * Unsubscribe from all subscriptions and destroy eventSubscriber
     */
    ngOnDestroy() {
        this.programmingSubmissionService.unsubscribeAllWebsocketTopics(this.exercise);
        this.eventManager.destroy(this.eventSubscriber);
        this.dialogErrorSource.unsubscribe();
    }

    loadAll() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.isLoading = true;
            this.hasLoadedPendingSubmissions = false;
            this.exerciseService.find(params['exerciseId']).subscribe((exerciseResponse) => {
                this.exercise = exerciseResponse.body!;
                this.participationService.findAllParticipationsByExercise(params['exerciseId'], true).subscribe((participationsResponse) => {
                    this.participations = participationsResponse.body!;
                    if (this.exercise.type === ExerciseType.PROGRAMMING) {
                        const programmingExercise = this.exercise as ProgrammingExercise;
                        if (programmingExercise.projectKey) {
                            this.profileService.getProfileInfo().subscribe((profileInfo) => {
                                for (let i = 0; i < this.participations.length; i++) {
                                    this.participations[i] = setBuildPlanUrlForProgrammingParticipation(
                                        profileInfo,
                                        this.participations[i] as ProgrammingExerciseStudentParticipation,
                                        (this.exercise as ProgrammingExercise).projectKey,
                                    );
                                }
                            });
                        }
                    }
                    this.isLoading = false;
                });
                if (this.exercise.type === ExerciseType.PROGRAMMING) {
                    this.programmingSubmissionService
                        .getSubmissionStateOfExercise(this.exercise.id!)
                        .pipe(
                            tap((exerciseSubmissionState: ExerciseSubmissionState) => {
                                this.exerciseSubmissionState = exerciseSubmissionState;
                            }),
                        )
                        .subscribe(() => (this.hasLoadedPendingSubmissions = true));
                }
                this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
                this.presentationScoreEnabled = this.checkPresentationScoreConfig();
                this.hasAccessRights();
            });
        });
    }

    formatDate(date: Moment | Date | undefined) {
        // TODO: we should try to use the artemis date pipe here
        return date ? moment(date).format(defaultLongDateTimeFormat) : '';
    }

    hasAccessRights() {
        if (this.exercise.course) {
            this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course);
        } else if (this.exercise.exerciseGroup) {
            this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.exerciseGroup.exam?.course!);
        }
        this.isAdmin = this.accountService.isAdmin();
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
        this.eventSubscriber = this.eventManager.subscribe('participationListModification', () => this.loadAll());
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
        this.participationService.update(this.exercise.id!, participation).subscribe(
            () => {},
            () => {
                this.jhiAlertService.error('artemisApp.participation.addPresentation.error');
            },
        );
    }

    removePresentation(participation: StudentParticipation) {
        if (!this.presentationScoreEnabled) {
            return;
        }
        participation.presentationScore = 0;
        this.participationService.update(this.exercise.id!, participation).subscribe(
            () => {},
            () => {
                this.jhiAlertService.error('artemisApp.participation.removePresentation.error');
            },
        );
    }

    /**
     * Deletes participation
     * @param participationId the id of the participation that we want to delete
     * @param event passed from delete dialog to represent if checkboxes were checked
     */
    deleteParticipation(participationId: number, event: { [key: string]: boolean }) {
        const deleteBuildPlan = event.deleteBuildPlan ? event.deleteBuildPlan : false;
        const deleteRepository = event.deleteRepository ? event.deleteRepository : false;
        this.participationService.delete(participationId, { deleteBuildPlan, deleteRepository }).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'participationListModification',
                    content: 'Deleted an participation',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    /**
     * Cleans programming exercise participation
     * @param programmingExerciseParticipation the id of the participation that we want to delete
     */
    cleanupProgrammingExerciseParticipation(programmingExerciseParticipation: StudentParticipation) {
        this.participationService.cleanupBuildPlan(programmingExerciseParticipation).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'participationListModification',
                    content: 'Cleanup the build plan of an participation',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
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
    getRepositoryLink = (participation: StudentParticipation, repoUrl: String) => {
        if ((participation as ProgrammingExerciseStudentParticipation).repositoryUrl === repoUrl) {
            return (participation as ProgrammingExerciseStudentParticipation).userIndependentRepositoryUrl;
        }
        return repoUrl;
    };
}
