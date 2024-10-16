import { Component, OnInit, inject } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AlertService } from 'app/core/util/alert.service';
import { Submission } from 'app/entities/submission.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ActivatedRoute } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { onError } from 'app/shared/util/global.utils';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';
import { OrionButtonType } from 'app/shared/orion/orion-button/orion-button.component';

@Component({
    selector: 'jhi-orion-exercise-assessment-dashboard',
    templateUrl: './orion-exercise-assessment-dashboard.component.html',
    providers: [CourseManagementService],
})
export class OrionExerciseAssessmentDashboardComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private exerciseService = inject(ExerciseService);
    private orionAssessmentService = inject(OrionAssessmentService);
    private orionConnectorService = inject(OrionConnectorService);
    private alertService = inject(AlertService);

    readonly ExerciseView = ExerciseView;
    readonly ExerciseType = ExerciseType;
    protected readonly OrionButtonType = OrionButtonType;

    orionState: OrionState;
    exerciseId: number;
    exercise: Exercise;

    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.exerciseService.getForTutors(this.exerciseId).subscribe({
            next: (res) => (this.exercise = res.body!),
            error: (error) => onError(this.alertService, error),
        });

        this.orionConnectorService.state().subscribe((state) => (this.orionState = state));
    }

    /**
     * Triggers downloading the test repository and opening it, allowing for submissions to be downloaded
     */
    openAssessmentInOrion() {
        this.orionConnectorService.assessExercise(this.exercise);
    }

    /**
     * Delegates to the {@link OrionAssessmentService} to load a new submission
     */
    downloadSubmissionInOrion(submission: Submission | 'new', correctionRound = 0, testRun = false) {
        this.orionAssessmentService.downloadSubmissionInOrion(this.exerciseId, submission, correctionRound, testRun);
    }
}
