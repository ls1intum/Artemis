import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';

@Injectable({ providedIn: 'root' })
export class MathSubmissionAssessmentResolverService implements Resolve<MathSubmission | undefined> {
    private mathSubmissionService = inject(MathSubmissionService);

    resolve(route: ActivatedRouteSnapshot): Observable<MathSubmission | undefined> {
        const submissionId = Number(route.paramMap.get('submissionId'));
        if (submissionId) {
            return this.mathSubmissionService.getMathSubmissionForAssessment(submissionId).pipe(catchError(() => of(undefined)));
        }
        return of(undefined);
    }
}
