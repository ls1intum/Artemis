import { Injectable } from '@angular/core';
import { ProgrammingAssessmentRepoExportService, RepositoryExportOptions } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { JhiAlertService } from 'ng-jhipster';

/**
 * Provides methods to retrieve information about running exercise builds.
 */
@Injectable({ providedIn: 'root' })
export class OrionAssessmentService {
    constructor(
        private orionConnectorService: OrionConnectorService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private repositoryExportService: ProgrammingAssessmentRepoExportService,
        private jhiAlertService: JhiAlertService,
    ) {}

    /**
     * Locks the given submission, exports it, transforms it to base64, and sends it to Orion
     *
     * @param exerciseId id of the exercise the submission belongs to
     * @param submissionId id of the submission to send to Orion
     * @param correctionRound correction round
     */
    sendSubmissionToOrion(exerciseId: number, submissionId: number, correctionRound = 0) {
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
