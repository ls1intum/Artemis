import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ProofSubmissionService } from 'app/proof/participate/service/proof-submission.service';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';

@Injectable({ providedIn: 'root' })
export class ProofSubmissionAssessmentResolverService implements Resolve<ProofSubmission | undefined> {
    private proofSubmissionService = inject(ProofSubmissionService);

    resolve(route: ActivatedRouteSnapshot): Observable<ProofSubmission | undefined> {
        const submissionId = Number(route.paramMap.get('submissionId'));
        if (submissionId) {
            return this.proofSubmissionService.getProofSubmissionForAssessment(submissionId).pipe(catchError(() => of(undefined)));
        }
        return of(undefined);
    }
}
