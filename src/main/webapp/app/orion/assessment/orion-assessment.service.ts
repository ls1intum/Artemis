import { Injectable } from '@angular/core';
import { ProgrammingAssessmentRepoExportService, RepositoryExportOptions } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { JhiAlertService } from 'ng-jhipster';
import { Submission } from 'app/entities/submission.model';
import { OrionState } from 'app/shared/orion/orion';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';

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
        private jhiAlertService: JhiAlertService,
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
     * @param correctionRound correction round
     */
    downloadSubmissionInOrion(exerciseId: number, submission: Submission | 'new', correctionRound = 0) {
        if (submission === 'new') {
            this.programmingSubmissionService
                .getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment(exerciseId, true, correctionRound)
                .subscribe((newSubmission) => this.sendSubmissionToOrionCancellable(exerciseId, newSubmission.id!, correctionRound));
        } else {
            this.sendSubmissionToOrion(exerciseId, submission.id!, correctionRound);
        }
    }

    /**
     * Calls sendSubmissionToOrion but logs the submission id before so the lock will be freed if the download is cancelled
     */
    sendSubmissionToOrionCancellable(exerciseId: number, submissionId: number, correctionRound = 0) {
        this.activeSubmissionId = submissionId;
        this.sendSubmissionToOrion(exerciseId, submissionId, correctionRound);
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
}
