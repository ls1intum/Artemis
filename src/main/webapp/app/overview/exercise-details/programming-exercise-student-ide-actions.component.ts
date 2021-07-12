import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { JhiAlertService } from 'ng-jhipster';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { ActivatedRoute } from '@angular/router';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { Participation } from 'app/entities/participation/participation.model';
import { ParticipationStatus } from 'app/entities/exercise.model';
import { isStartExerciseAvailable, participationStatus } from 'app/exercises/shared/exercise/exercise-utils';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { catchError, filter, finalize, tap } from 'rxjs/operators';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

@Component({
    selector: 'jhi-programming-exercise-student-ide-actions',
    templateUrl: './programming-exercise-student-ide-actions.component.html',
    styleUrls: ['../course-overview.scss'],
    providers: [SourceTreeService],
})
export class ProgrammingExerciseStudentIdeActionsComponent implements OnInit {
    readonly UNINITIALIZED = ParticipationStatus.UNINITIALIZED;
    readonly INITIALIZED = ParticipationStatus.INITIALIZED;
    readonly INACTIVE = ParticipationStatus.INACTIVE;
    readonly participationStatus = participationStatus;
    ideState: OrionState;
    FeatureToggle = FeatureToggle;

    @Input() @HostBinding('class.col') equalColumns = true;
    @Input() @HostBinding('class.col-auto') smallColumns = false;

    @Input() exercise: ProgrammingExercise;
    @Input() courseId: number;

    @Input() smallButtons: boolean;

    constructor(
        private jhiAlertService: JhiAlertService,
        private courseExerciseService: CourseExerciseService,
        private orionConnectorService: OrionConnectorService,
        private ideBuildAndTestService: OrionBuildAndTestService,
        private route: ActivatedRoute,
    ) {}

    /**
     * get ideState and submit changes if withIdeSubmit set in route query
     */
    ngOnInit(): void {
        this.orionConnectorService.state().subscribe((ideState: OrionState) => (this.ideState = ideState));
        this.route.queryParams.subscribe((params) => {
            if (params['withIdeSubmit']) {
                this.submitChanges();
            }
        });
    }

    /**
     * see exercise-utils -> isStartExerciseAvailable
     */
    isStartExerciseAvailable(): boolean {
        return isStartExerciseAvailable(this.exercise as ProgrammingExercise);
    }

    /**
     * Get the repo URL of a participation. Can be used to clone from the repo or push to it.
     *
     * @param participation The participation for which to get the repository URL
     * @return The URL of the remote repository in which the user's code referring the the current exercise is stored.
     */
    repositoryUrl(participation?: Participation) {
        return (participation as ProgrammingExerciseStudentParticipation)?.repositoryUrl;
    }

    /**
     * Starts the exercise by initializing a new participation and creating a new personal repository.
     */
    startExercise() {
        this.exercise.loading = true;

        this.courseExerciseService
            .startExercise(this.courseId, this.exercise.id!)
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe(
                (participation: StudentParticipation) => {
                    if (participation) {
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = this.participationStatus(this.exercise);
                    }
                    if (this.exercise.allowOfflineIde) {
                        this.jhiAlertService.success('artemisApp.exercise.personalRepositoryClone');
                    } else {
                        this.jhiAlertService.success('artemisApp.exercise.personalRepositoryOnline');
                    }
                },
                () => {
                    this.jhiAlertService.warning('artemisApp.exercise.startError');
                },
            );
    }

    /**
     * Imports the current exercise in the user's IDE and triggers the opening of the new project in the IDE
     */
    importIntoIDE() {
        const repo = this.repositoryUrl(this.exercise.studentParticipations![0])!;
        this.orionConnectorService.importParticipation(repo, this.exercise as ProgrammingExercise);
    }

    /**
     * Submits the changes made in the IDE by staging everything, committing the changes and pushing them to master.
     */
    submitChanges() {
        this.orionConnectorService.submit();
        this.ideBuildAndTestService.listenOnBuildOutputAndForwardChanges(this.exercise as ProgrammingExercise);
    }

    get canImport(): boolean {
        const notOpenedOrNotStudent = this.ideState.view !== ExerciseView.STUDENT || this.ideState.opened !== this.exercise.id;

        return this.hasInitializedParticipation() && notOpenedOrNotStudent;
    }

    get canSubmit(): boolean {
        const openedAndStudent = this.ideState.view === ExerciseView.STUDENT && this.ideState.opened === this.exercise.id;

        return this.hasInitializedParticipation() && openedAndStudent;
    }

    private hasInitializedParticipation(): boolean {
        return this.exercise.studentParticipations !== undefined && this.participationStatus(this.exercise) === this.INITIALIZED && this.exercise.studentParticipations.length > 0;
    }

    /**
     * resume programming exercise
     */
    resumeProgrammingExercise() {
        this.exercise.loading = true;
        this.courseExerciseService
            .resumeProgrammingExercise(this.courseId, this.exercise.id!)
            .pipe(
                filter(Boolean),
                tap((participation: StudentParticipation) => {
                    participation.results = this.exercise.studentParticipations![0] ? this.exercise.studentParticipations![0].results : [];
                    this.exercise.studentParticipations = [participation];
                    this.exercise.participationStatus = participationStatus(this.exercise);
                }),
                catchError((error) => {
                    this.jhiAlertService.error('artemisApp.exerciseActions.resumeExercise', { error });
                    return error;
                }),
            )
            .pipe(finalize(() => (this.exercise.loading = false)))
            .subscribe();
    }
}
