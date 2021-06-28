import { Component, OnInit } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { JhiAlertService } from 'ng-jhipster';
import { Submission } from 'app/entities/submission.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ExerciseAssessmentDashboardComponent } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.component';
import { ProgrammingAssessmentRepoExportService, RepositoryExportOptions } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';

@Component({
    selector: 'jhi-orion-exercise-assessment-dashboard',
    templateUrl: './orion-exercise-assessment-dashboard.component.html',
    providers: [CourseManagementService],
})
export class OrionExerciseAssessmentDashboardComponent implements OnInit {
    readonly ExerciseView = ExerciseView;
    readonly ExerciseType = ExerciseType;
    orionState: OrionState;
    isOrionAndProgramming = false;

    exercise: Exercise;

    constructor(
        private exerciseAssessmentDashboard: ExerciseAssessmentDashboardComponent,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private repositoryExportService: ProgrammingAssessmentRepoExportService,
        private orionConnectorService: OrionConnectorService,
        private jhiAlertService: JhiAlertService,
    ) {
        this.exercise = exerciseAssessmentDashboard.exercise;
    }

    ngOnInit(): void {
        this.orionConnectorService.state().subscribe((state) => {
            this.orionState = state;
        });

        this.isOrionAndProgramming = this.exercise.type === ExerciseType.PROGRAMMING;
    }

    calculateSubmissionStatus(submission: Submission, correctionRound?: number): 'DONE' | 'DRAFT' {
        return this.exerciseAssessmentDashboard.calculateSubmissionStatus(submission, correctionRound);
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
                    this.jhiAlertService.error('artemisApp.assessmentDashboard.orion.downloadFailed');
                };
                reader.readAsDataURL(response.body!);
            });
        });
    }

    /**
     * Retrieves a new submission if necessary and then delegates to the
     * {@link programmingSubmissionService} to download the submission
     *
     * @param submission submission to send to Orion or 'new' if a new one should be loaded
     * @param correctionRound correction round
     */
    downloadSubmissionInOrion(submission: Submission | 'new', correctionRound = 0) {
        if (submission === 'new') {
            this.programmingSubmissionService
                .getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment(this.exercise.id!, true, correctionRound)
                .subscribe((newSubmission) => this.sendSubmissionToOrion(this.exercise.id!, newSubmission.id!, correctionRound));
        } else {
            this.sendSubmissionToOrion(this.exercise.id!, submission.id!, correctionRound);
        }
    }
}
