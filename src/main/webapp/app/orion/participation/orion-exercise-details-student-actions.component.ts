import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseOperationMode } from 'app/ExerciseOperationMode';

@Component({
    selector: 'jhi-orion-exercise-details-student-actions',
    templateUrl: './orion-exercise-details-student-actions.component.html',
    styleUrls: ['../../overview/course-overview.scss'],
})
export class OrionExerciseDetailsStudentActionsComponent implements OnInit {
    readonly ExerciseView = ExerciseView;
    readonly ExerciseOperationMode = ExerciseOperationMode;
    orionState: OrionState;
    FeatureToggle = FeatureToggle;

    @Input() exercise: Exercise;
    @Input() courseId: number;
    @Input() actionsOnly: boolean;
    @Input() smallButtons: boolean;
    @Input() showResult: boolean;
    @Input() examMode: boolean;

    constructor(private orionConnectorService: OrionConnectorService, private ideBuildAndTestService: OrionBuildAndTestService, private route: ActivatedRoute) {}

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
}
