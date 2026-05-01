import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProofSubmissionService } from 'app/proof/participate/service/proof-submission.service';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';

@Injectable({ providedIn: 'root' })
export class ProofSubmissionAssessmentResolverService implements Resolve<StudentParticipation | undefined> {
    private proofSubmissionService = inject(ProofSubmissionService);

    resolve(route: ActivatedRouteSnapshot): Observable<StudentParticipation | undefined> {
        const submissionId = Number(route.paramMap.get('submissionId'));
        if (submissionId) {
            return this.proofSubmissionService.getProofSubmissionForAssessment(submissionId).pipe(
                map((submission: ProofSubmission) => <StudentParticipation | undefined>submission.participation),
                catchError(() => of(undefined))
            );
        }
        return of(undefined);
    }
}
