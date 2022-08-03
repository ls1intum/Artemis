import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { Exam } from 'app/entities/exam.model';

type EntityResponseType = SearchResult<Exam>;

@Injectable({ providedIn: 'root' })
export class ExamImportPagingService {
    public resourceUrl = SERVER_API_URL + 'api/exams';

    constructor(private http: HttpClient) {}

    /**
     * Method to get (possible) exams for import from the server
     * @param pageable object specifying search parameters
     * @param withExercises if only exams with exercises should be included in the results
     */
    searchForExams(pageable: PageableSearch, withExercises: boolean): Observable<EntityResponseType> {
        const params = new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('searchTerm', pageable.searchTerm)
            .set('sortedColumn', pageable.sortedColumn);
        return this.http
            .get(`${this.resourceUrl}?withExercises=${withExercises}`, { params, observe: 'response' })
            .pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
