import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpContext } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { map } from 'rxjs/operators';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { PageableResult, toPageableResult } from 'app/foundation/pagination/pageable-table';
import { ExternalSubmissionCandidate } from 'app/exercise/external-submission/external-submission-candidate.model';

/**
 * Keeps the candidate search on HttpClient: it skips the global error alert, and the generated method takes no HttpContext.
 */
@Service()
export class ExternalSubmissionService {
    private http = inject(HttpClient);

    /**
     * Searches the students of the exercise's course that match the search term, each flagged with the reason they cannot receive an external result, if any.
     * Failures are shown next to the search field, so the request raises no global error alert.
     * @param exercise   the exercise the external result is for
     * @param searchTerm part of the login, name, email or registration number
     * @param page       zero-based page index
     * @param pageSize   number of students per page
     */
    searchCandidates(exercise: Exercise, searchTerm: string, page: number, pageSize: number): Observable<PageableResult<ExternalSubmissionCandidate>> {
        return this.http
            .get<ExternalSubmissionCandidate[]>(`api/assessment/exercises/${exercise.id}/external-submission-candidates`, {
                params: { searchTerm, page, pageSize },
                observe: 'response',
                context: new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true),
            })
            .pipe(map(toPageableResult));
    }

    /**
     * Generates an initial manual result with default values.
     */
    generateInitialManualResult() {
        const newResult = new Result();
        newResult.completionDate = dayjs();
        newResult.successful = true;
        newResult.score = 100;
        newResult.rated = true;
        return newResult;
    }
}
