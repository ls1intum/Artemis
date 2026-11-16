import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpContext } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { User } from 'app/account/user/user.model';
import { EntityResponseType, ResultService } from 'app/exercise/result/result.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { map } from 'rxjs/operators';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { PageableResult, toPageableResult } from 'app/foundation/pagination/pageable-table';
import { ExternalSubmissionCandidate } from 'app/exercise/external-submission/external-submission-candidate.model';

@Service()
export class ExternalSubmissionService {
    private http = inject(HttpClient);
    private resultService = inject(ResultService);

    // TODO: It would be good to refactor the convertDate methods into a separate service, so that we don't have to import the result service here.

    /**
     * Persist a new result for the provided exercise and student (a participation and an empty submission will also be created if they do not exist yet)
     * @param { Exercise } exercise - Exercise for which a new result is created
     * @param { User } student - Student for whom a result is created
     * @param { Result } result - Result that is added
     */
    create(exercise: Exercise, student: Pick<User, 'login'>, result: Result): Observable<EntityResponseType> {
        const copy = this.resultService.convertResultDatesFromClient(result);
        return this.http
            .post<Result>(`api/assessment/exercises/${exercise.id}/external-submission-results?studentLogin=${student.login}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.resultService.convertResultResponseDatesFromServer(res)));
    }

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
