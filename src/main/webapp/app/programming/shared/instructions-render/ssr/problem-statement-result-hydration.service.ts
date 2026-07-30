import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, mergeMap, of } from 'rxjs';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ResultService } from 'app/exercise/result/result.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { findLatestResult } from 'app/foundation/util/utils';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

/**
 * Loads results and their feedback details for problem-statement rendering.
 *
 * The renderer must be able to distinguish "feedback not loaded yet" from "no feedback": the former must never be
 * sent to the server as an empty test-result list. Every result handed out by this service therefore has its
 * feedback details loaded (or is undefined).
 */
@Injectable({ providedIn: 'root' })
export class ProblemStatementResultHydrationService {
    private resultService = inject(ResultService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);

    /**
     * Resolves the latest result of the given participation, including its feedback details.
     */
    initialResult(participation: Participation | undefined): Observable<Result | undefined> {
        if (!participation?.id) {
            return of(undefined);
        }
        const results = getAllResultsOfAllSubmissions(participation.submissions);
        if (results.length) {
            const latest = findLatestResult(results);
            return latest ? this.withFeedbackDetails(participation, latest) : of(undefined);
        }
        return this.programmingExerciseParticipationService.getLatestResultWithFeedback(participation.id).pipe(
            catchError(() => of(undefined)),
            mergeMap((latest?: Result) => (latest ? this.withFeedbackDetails(participation, latest) : of(undefined))),
        );
    }

    /**
     * Ensures the given result carries its feedback list; fetches it when missing.
     *
     * On a fetch failure the result is deliberately **not** returned: an unloaded `feedbacks` array is
     * indistinguishable from "no feedback" downstream and would be rendered as "no result" instead of surfacing the
     * error. Callers must treat the error as a load failure.
     */
    withFeedbackDetails(participation: Participation | undefined, result: Result): Observable<Result> {
        if (result.feedbacks) {
            return of(result);
        }
        return this.resultService.getFeedbackDetailsForResult(participation?.id, result).pipe(
            map((response) => {
                result.feedbacks = response.body ?? [];
                return result;
            }),
        );
    }
}
