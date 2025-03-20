import { Component, OnInit, inject } from '@angular/core';
import { CourseManagementService } from 'app/core/course/manage/course-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { Submission } from 'app/entities/submission.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ActivatedRoute } from '@angular/router';
import { ExerciseService } from 'app/exercise/exercise.service';
import { onError } from 'app/shared/util/global.utils';
import { OrionAssessmentService } from 'app/orion/manage/assessment/orion-assessment.service';
import { OrionButtonComponent, OrionButtonType } from 'app/shared/orion/orion-button/orion-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExerciseAssessmentDashboardComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/exercise-assessment-dashboard.component';

@Component({
    selector: 'jhi-orion-exercise-assessment-dashboard',
    templateUrl: './orion-exercise-assessment-dashboard.component.html',
    providers: [CourseManagementService],
    imports: [ExerciseAssessmentDashboardComponent, OrionButtonComponent, ArtemisTranslatePipe],
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
        if (this.orionConnectorService && this.orionConnectorService.state()) {
            this.orionConnectorService.state().subscribe((state) => (this.orionState = state));
        }
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
