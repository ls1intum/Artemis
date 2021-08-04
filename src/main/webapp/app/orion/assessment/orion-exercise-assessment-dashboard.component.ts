import { Component, OnInit } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AlertService } from 'app/core/util/alert.service';
import { calculateSubmissionStatusIsDraft, Submission } from 'app/entities/submission.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ProgrammingAssessmentRepoExportService, RepositoryExportOptions } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';
import { ActivatedRoute } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { onError } from 'app/shared/util/global.utils';

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
        private repositoryExportService: ProgrammingAssessmentRepoExportService,
        private orionConnectorService: OrionConnectorService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.exerciseService.getForTutors(this.exerciseId).subscribe(
            (res) => (this.exercise = res.body!),
            (error) => onError(this.alertService, error),
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
     * Locks the given submission, exports it, transforms it to base64, and sends it to Orion
     *
     * @param exerciseId id of the exercise the submission belongs to
     * @param submissionId id of the submission to send to Orion
     * @param correctionRound correction round
     */
    private sendSubmissionToOrion(exerciseId: number, submissionId: number, correctionRound = 0) {
        this.orionConnectorService.isCloning(true);
        const exportOptions: RepositoryExportOptions = {
            exportAllParticipants: false,
            filterLateSubmissions: false,
            addParticipantName: false,
            combineStudentCommits: false,
            anonymizeStudentCommits: true,
            normalizeCodeStyle: false,
            hideStudentNameInZippedFolder: true,
        };
        this.programmingSubmissionService.lockAndGetProgrammingSubmissionParticipation(submissionId, correctionRound).subscribe((programmingSubmission) => {
            this.repositoryExportService.exportReposByParticipations(exerciseId, [programmingSubmission.participation!.id!], exportOptions).subscribe((response) => {
                const reader = new FileReader();
                reader.onloadend = () => {
                    const result = reader.result as string;
                    // remove prefix
                    const base64data = result.substr(result.indexOf(',') + 1);
                    this.orionConnectorService.downloadSubmission(submissionId, correctionRound, base64data);
                };
                reader.onerror = () => {
                    this.alertService.error('artemisApp.assessmentDashboard.orion.downloadFailed');
                };
                reader.readAsDataURL(response.body!);
            });
        });
    }

    /**
     * Retrieves a new submission if necessary and then delegates to sendSubmissionToOrion
     *
     * @param submission submission to send to Orion or 'new' if a new one should be loaded
     * @param correctionRound correction round
     */
    downloadSubmissionInOrion(submission: Submission | 'new', correctionRound = 0) {
        if (submission === 'new') {
            this.programmingSubmissionService
                .getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment(this.exerciseId, true, correctionRound)
                .subscribe((newSubmission) => this.sendSubmissionToOrion(this.exerciseId, newSubmission.id!, correctionRound));
        } else {
            this.sendSubmissionToOrion(this.exerciseId, submission.id!, correctionRound);
        }
    }
}
