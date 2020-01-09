import { Component, HostBinding, Input, OnInit } from '@angular/core';
import { Exercise, isStartExerciseAvailable, ParticipationStatus } from 'app/entities/exercise';
import { InitializationState, Participation, ProgrammingExerciseStudentParticipation } from 'app/entities/participation';
import { CourseExerciseService } from 'app/entities/course/course.service';
import { JhiAlertService } from 'ng-jhipster';
import { SourceTreeService } from 'app/components/util/sourceTree.service';
import { IntelliJState } from 'app/intellij/intellij';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { IdeBuildAndTestService } from 'app/intellij/ide-build-and-test.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { ActivatedRoute } from '@angular/router';
import { FeatureToggle } from 'app/feature-toggle';
import { stringifyCircular } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-programming-exercise-student-ide-actions',
    templateUrl: './programming-exercise-student-ide-actions.component.html',
    styleUrls: ['../course-overview.scss'],
    providers: [JhiAlertService, SourceTreeService],
})
export class ProgrammingExerciseStudentIdeActionsComponent implements OnInit {
    readonly UNINITIALIZED = ParticipationStatus.UNINITIALIZED;
    readonly INITIALIZED = ParticipationStatus.INITIALIZED;
    readonly INACTIVE = ParticipationStatus.INACTIVE;
    ideState: IntelliJState;
    FeatureToggle = FeatureToggle;

    @Input() @HostBinding('class.col') equalColumns = true;
    @Input() @HostBinding('class.col-auto') smallColumns = false;

    @Input() exercise: Exercise;
    @Input() courseId: number;

    @Input() smallButtons: boolean;

    constructor(
        private jhiAlertService: JhiAlertService,
        private courseExerciseService: CourseExerciseService,
        private javaBridge: JavaBridgeService,
        private ideBuildAndTestService: IdeBuildAndTestService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit(): void {
        this.javaBridge.state().subscribe((ideState: IntelliJState) => (this.ideState = ideState));
        this.route.queryParams.subscribe(params => {
            if (params['withIdeSubmit']) {
                this.submitChanges();
            }
        });
    }

    /**
     * Get the participation status of the current exercise depending on if the user has already started the exercise
     * or not.
     *
     * @return The participation status of the current exercise
     */
    participationStatus(): ParticipationStatus {
        if (!this.hasParticipations()) {
            return ParticipationStatus.UNINITIALIZED;
        } else if (this.exercise.studentParticipations[0].initializationState === InitializationState.INITIALIZED) {
            return ParticipationStatus.INITIALIZED;
        }
        return ParticipationStatus.INACTIVE;
    }

    /**
     * see exercise-utils -> isStartExerciseAvailable
     */
    isStartExerciseAvailable(): boolean {
        return isStartExerciseAvailable(this.exercise as ProgrammingExercise);
    }

    /**
     * Determines if the current exercise has any participation. This indirectly reflects, whether the exercise has
     * already been started or not.
     *
     * @return True, if the exercise has any participation, false otherwise
     */
    hasParticipations(): boolean {
        return this.exercise.studentParticipations && this.exercise.studentParticipations.length > 0;
    }

    /**
     * Get the repo URL of a participation. Can be used to clone from the repo or push to it.
     *
     * @param participation The participation for which to get the repository URL
     * @return The URL of the remote repository in which the user's code referring the the current exercise is stored.
     */
    repositoryUrl(participation: Participation) {
        return (participation as ProgrammingExerciseStudentParticipation).repositoryUrl;
    }

    /**
     * Starts the exercise by initializing a new participation and creating a new personal repository.
     */
    startExercise() {
        this.exercise.loading = true;

        this.courseExerciseService
            .startExercise(this.courseId, this.exercise.id)
            .finally(() => (this.exercise.loading = false))
            .subscribe(
                participation => {
                    if (participation) {
                        this.exercise.studentParticipations = [participation];
                        this.exercise.participationStatus = this.participationStatus();
                    }
                    this.jhiAlertService.success('artemisApp.exercise.personalRepository');
                },
                error => {
                    console.log('Error: ' + error);
                    this.jhiAlertService.warning('artemisApp.exercise.startError');
                },
            );
    }

    /**
     * Imports the current exercise in the user's IDE and triggers the opening of the new project in the IDE
     */
    importIntoIntelliJ() {
        const repo = this.repositoryUrl(this.exercise.studentParticipations[0]);
        this.javaBridge.clone(repo, stringifyCircular(this.exercise));
    }

    /**
     * Submits the changes made in the IDE by staging everything, committing the changes and pushing them to master.
     */
    submitChanges() {
        this.javaBridge.submit();
        this.ideBuildAndTestService.listenOnBuildOutputAndForwardChanges(this.exercise as ProgrammingExercise);
    }

    get canImport(): boolean {
        const notOpenedOrInstructor = this.ideState.inInstructorView || this.ideState.opened !== this.exercise.id;

        return this.hasInitializedParticipation() && notOpenedOrInstructor;
    }

    get canSubmit(): boolean {
        const openedAndNotInstructor = !this.ideState.inInstructorView && this.ideState.opened === this.exercise.id;

        return this.hasInitializedParticipation() && openedAndNotInstructor;
    }

    private hasInitializedParticipation(): boolean {
        return this.exercise.studentParticipations && this.participationStatus() === this.INITIALIZED && this.exercise.studentParticipations.length > 0;
    }
}
