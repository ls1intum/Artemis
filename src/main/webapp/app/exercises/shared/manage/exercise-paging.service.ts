import { HttpClient, HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { Observable, map } from 'rxjs';

export abstract class ExercisePagingService<T extends Exercise> extends PagingService<T> {
    protected constructor(
        protected http: HttpClient,
        protected resourceUrl: string,
    ) {
        super();
    }

    /**
     * Allows to search for exercises matching the given criteria
     * @param pageable the search settings like search term and sort order
     * @param options special parameters for exercise search:
     * - isCourseFilter if course exercises should be included
     * - isExamFilter if exam exercises should be included
     * - programmingLanguage set to a language if only programming exercises of this language should be included. undefined for other exercise types.
     */
    public override search(
        pageable: SearchTermPageableSearch,
        options: { isCourseFilter: boolean; isExamFilter: boolean; programmingLanguage?: ProgrammingLanguage },
    ): Observable<SearchResult<T>> {
        let params = this.createHttpParams(pageable);
        params = params.set('isCourseFilter', String(options.isCourseFilter)).set('isExamFilter', String(options.isExamFilter));
        if (options.programmingLanguage) {
            params = params.set('programmingLanguage', options.programmingLanguage);
        }
        return this.http.get(`${this.resourceUrl}`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<SearchResult<T>>) => resp && resp.body!));
    }
}
