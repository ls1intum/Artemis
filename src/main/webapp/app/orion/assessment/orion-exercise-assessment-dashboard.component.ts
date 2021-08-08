import { Component, OnInit } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { JhiAlertService } from 'ng-jhipster';
import { calculateSubmissionStatusIsDraft, Submission } from 'app/entities/submission.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ActivatedRoute } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { onError } from 'app/shared/util/global.utils';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';

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

    constructor(
        private route: ActivatedRoute,
        private exerciseService: ExerciseService,
        private programmingSubmissionService: ProgrammingSubmissionService,
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
     * Retrieves a new submission if necessary and then delegates to the
     * {@link OrionAssessmentService} to download the submission
     *
     * @param submission submission to send to Orion or 'new' if a new one should be loaded
     * @param correctionRound correction round
     */
    downloadSubmissionInOrion(submission: Submission | 'new', correctionRound = 0) {
        if (submission === 'new') {
            this.programmingSubmissionService
                .getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment(this.exerciseId, true, correctionRound)
                .subscribe((newSubmission) => this.orionAssessmentService.sendSubmissionToOrion(this.exerciseId, newSubmission.id!, correctionRound));
        } else {
            this.orionAssessmentService.sendSubmissionToOrion(this.exerciseId, submission.id!, correctionRound);
        }
    }
}
