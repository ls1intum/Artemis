import { Service, inject } from '@angular/core';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { PagingService } from 'app/exercise/services/paging.service';
import { SearchResult, SearchTermPageableSearch } from 'app/foundation/pagination/pageable-table';
import { Observable, map } from 'rxjs';
import { LectureApi } from 'app/openapi/api/lecture-api';
import { toLecture } from 'app/lecture/shared/util/generated-lecture.util';

@Service()
export class LecturePagingService extends PagingService<Lecture> {
    private readonly lectureApi = inject(LectureApi);

    override search(pageable: SearchTermPageableSearch): Observable<SearchResult<Lecture>> {
        return this.lectureApi.getAllLecturesOnPage(pageable.page, pageable.pageSize, pageable.sortingOrder, pageable.sortedColumn, pageable.searchTerm).pipe(
            map((page) => ({
                resultsOnPage: (page.resultsOnPage ?? []).map(toLecture),
                numberOfPages: page.numberOfPages ?? 0,
            })),
        );
    }
}
