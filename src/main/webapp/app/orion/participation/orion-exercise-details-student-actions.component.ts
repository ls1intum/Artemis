import { Component, Input, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { Exercise } from 'app/entities/exercise.model';
import { Feedback } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-orion-exercise-details-student-actions',
    templateUrl: './orion-exercise-details-student-actions.component.html',
    styleUrls: ['../../overview/course-overview.scss'],
})
export class OrionExerciseDetailsStudentActionsComponent implements OnInit {
    readonly ExerciseView = ExerciseView;
    orionState: OrionState;
    FeatureToggle = FeatureToggle;

    @Input() exercise: Exercise;
    @Input() courseId: number;
    @Input() smallButtons: boolean;
    @Input() examMode: boolean;

    private feedbackRequestSubscription: Subscription;

    constructor(
        private orionConnectorService: OrionConnectorService,
        private ideBuildAndTestService: OrionBuildAndTestService,
        private route: ActivatedRoute,
    ) {}

    /**
     * get orionState and submit changes if withIdeSubmit set in route query
     */
    ngOnInit(): void {
        this.orionConnectorService.state().subscribe((orionState: OrionState) => (this.orionState = orionState));

        this.route.queryParams.subscribe((params) => {
            if (params['withIdeSubmit']) {
                this.submitChanges();
            }
        });
        this.feedbackRequestSubscription = this.orionConnectorService.getObservableForFeedback().subscribe(() => {
            this.initializeFeedback();
        });
    }

    get isOfflineIdeAllowed() {
        return (this.exercise as ProgrammingExercise).allowOfflineIde;
    }

    /**
     * Imports the current exercise in the user's IDE and triggers the opening of the new project in the IDE
     */
    importIntoIDE() {
        const repo = (this.exercise.studentParticipations![0] as ProgrammingExerciseStudentParticipation).repositoryUrl!;
        this.orionConnectorService.importParticipation(repo, this.exercise as ProgrammingExercise);
    }

    /**
     * Submits the changes made in the IDE by staging everything, committing the changes and pushing them to master.
     */
    submitChanges() {
        this.orionConnectorService.submit();
        this.ideBuildAndTestService.listenOnBuildOutputAndForwardChanges(this.exercise as ProgrammingExercise);
    }

    /**
     * returns feedback for an exercise.
     * Orion will handle the feedback and processes the last graded result
     * this ensures feedback changes won't break the plugin and the endpoint stays extensible
     */
    initializeFeedback() {
        const participations = this.exercise.studentParticipations as ProgrammingExerciseStudentParticipation[];
        const connectorService = this.orionConnectorService;
        participations?.forEach(function (participation) {
            participation.results?.forEach(function (result) {
                if (result.rated !== undefined && result.rated) {
                    if (result.feedbacks !== undefined) {
                        connectorService.initializeFeedbackArray(result.feedbacks);
                        return;
                    }
                }
            });
            connectorService.initializeFeedbackArray([] as Array<Feedback>);
        });
    }
}
