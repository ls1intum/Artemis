import { Component, OnInit } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { JhiAlertService } from 'ng-jhipster';
import { calculateSubmissionStatusIsDraft, Submission } from 'app/entities/submission.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ActivatedRoute } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { onError } from 'app/shared/util/global.utils';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';

@Component({
    selector: 'jhi-orion-exercise-assessment-dashboard',
    templateUrl: './orion-exercise-assessment-dashboard.component.html',
    providers: [CourseManagementService],
})
export class OrionExerciseAssessmentDashboardComponent implements OnInit {
    readonly ExerciseView = ExerciseView;
    readonly ExerciseType = ExerciseType;
    calculateSubmissionStatusIsDraft = calculateSubmissionStatusIsDraft;
    orionState: OrionState;

    exerciseId: number;
    exercise: Exercise;
    // Stores which submission has been lastly opened
    activeSubmissionId: number | undefined = undefined;

    constructor(
        private route: ActivatedRoute,
        private exerciseService: ExerciseService,
        private manualAssessmentService: ProgrammingAssessmentManualResultService,
        private orionAssessmentService: OrionAssessmentService,
        private orionConnectorService: OrionConnectorService,
        private jhiAlertService: JhiAlertService,
    ) {}

    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.exerciseService.getForTutors(this.exerciseId).subscribe(
            (res) => (this.exercise = res.body!),
            (error) => onError(this.jhiAlertService, error),
        );

        this.orionConnectorService.state().subscribe((state) => {
            if (this.orionState.cloning && !state.cloning && this.activeSubmissionId) {
                // If the client sends a cloning = false the download was cancelled, unlock the pending submission
                this.manualAssessmentService.cancelAssessment(this.activeSubmissionId);
            }
            this.orionState = state;
        });
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
    downloadSubmissionInOrion(submission: Submission | 'new', correctionRound = 0) {
        this.orionAssessmentService.downloadSubmissionInOrion(this.exerciseId, submission, correctionRound, this.setActiveSubmissionId.bind(this));
    }

    private setActiveSubmissionId(submissionId: number) {
        this.activeSubmissionId = submissionId;
    }
}
