import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { LearningPathPagingService } from 'app/course/learning-paths/learning-path-paging.service';
import { PageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { HttpParams } from '@angular/common/http';
import { TableColumn } from 'app/course/learning-paths/learning-path-management/learning-path-management.component';

describe('LearningPathPagingService', () => {
    let learningPathPagingService: LearningPathPagingService;
    let httpService: MockHttpService;
    let getStub: jest.SpyInstance;

    beforeEach(() => {
        httpService = new MockHttpService();
        learningPathPagingService = new LearningPathPagingService(httpService);
        getStub = jest.spyOn(httpService, 'get');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should send a request to the server to activate the user', () => {
        const pageable = {
            page: 1,
            pageSize: 10,
            searchTerm: 'initialSearchTerm',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: TableColumn.ID,
        } as PageableSearch;
        learningPathPagingService.searchForLearningPaths(pageable, 1).subscribe();
        const params = new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('searchTerm', pageable.searchTerm)
            .set('sortedColumn', pageable.sortedColumn);
        expect(getStub).toHaveBeenCalledOnce();
        expect(getStub).toHaveBeenCalledWith('api/courses/1/learning-paths', { params, observe: 'response' });
    });
});
