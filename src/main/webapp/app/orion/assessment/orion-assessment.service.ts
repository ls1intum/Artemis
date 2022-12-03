import { Injectable } from '@angular/core';
import { ProgrammingAssessmentRepoExportService, RepositoryExportOptions } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { Submission } from 'app/entities/submission.model';
import { OrionState } from 'app/shared/orion/orion';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { AlertService } from 'app/core/util/alert.service';

@Injectable({ providedIn: 'root' })
export class OrionAssessmentService {
    orionState: OrionState;
    // Stores which submission has been lastly opened
    activeSubmissionId: number | undefined = undefined;

    constructor(
        private orionConnectorService: OrionConnectorService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private repositoryExportService: ProgrammingAssessmentRepoExportService,
        private manualAssessmentService: ProgrammingAssessmentManualResultService,
        private alertService: AlertService,
    ) {
        this.orionConnectorService.state().subscribe((state) => {
            if (this.orionState?.cloning && !state.cloning && this.activeSubmissionId !== undefined) {
                // If the client sends a cloning = false the download was cancelled, unlock the pending submission
                this.manualAssessmentService.cancelAssessment(this.activeSubmissionId).subscribe();
            }
            this.orionState = { ...state };
        });
    }

    /**
     * Retrieves a new submission if necessary and then delegates to sendSubmissionToOrion
     * to download the submission
     *
     * @param exerciseId if of the exercise the submission belongs to
     * @param submission submission to send to Orion or 'new' if a new one should be loaded
     * @param correctionRound of the assessment
     * @param testRun true if in a test run, false otherwise
     */
    downloadSubmissionInOrion(exerciseId: number, submission: Submission | 'new', correctionRound = 0, testRun: boolean) {
        if (submission === 'new') {
            this.programmingSubmissionService
                .getSubmissionWithoutAssessment(exerciseId, true, correctionRound)
                .subscribe((newSubmission) => this.sendSubmissionToOrionCancellable(exerciseId, newSubmission.id!, correctionRound, testRun));
        } else {
            this.sendSubmissionToOrion(exerciseId, submission.id!, correctionRound, testRun);
        }
    }

    /**
     * Calls sendSubmissionToOrion but logs the submission id before so the lock will be freed if the download is cancelled
     */
    sendSubmissionToOrionCancellable(exerciseId: number, submissionId: number, correctionRound = 0, testRun: boolean) {
        this.activeSubmissionId = submissionId;
        this.sendSubmissionToOrion(exerciseId, submissionId, correctionRound, testRun);
    }

    /**
     * Locks the given submission, exports it, transforms it to base64, and sends it to Orion
     *
     * @param exerciseId id of the exercise the submission belongs to
     * @param submissionId id of the submission to send to Orion
     * @param correctionRound of the assessment
     * @param testRun true if in a test run, false otherwise
     */
    private sendSubmissionToOrion(exerciseId: number, submissionId: number, correctionRound = 0, testRun: boolean) {
        this.orionConnectorService.isCloning(true);
        const exportOptions: RepositoryExportOptions = {
            exportAllParticipants: false,
            filterLateSubmissions: false,
            addParticipantName: false,
            combineStudentCommits: true,
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
                    const base64data = result.slice(result.indexOf(',') + 1);
                    this.orionConnectorService.downloadSubmission(submissionId, correctionRound, testRun, base64data);
                };
                reader.onerror = () => {
                    this.alertService.error('artemisApp.assessmentDashboard.orion.downloadFailed');
                };
                reader.readAsDataURL(response.body!);
            });
        });
    }
}
