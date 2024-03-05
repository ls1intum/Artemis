import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { LearningPathPagingService } from 'app/course/learning-paths/learning-path-paging.service';
import { SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { HttpClient, HttpParams } from '@angular/common/http';
import { TableColumn } from 'app/course/learning-paths/learning-path-management/learning-path-management.component';
import { ArtemisTestModule } from '../test.module';
import { TestBed } from '@angular/core/testing';

describe('LearningPathPagingService', () => {
    let learningPathPagingService: LearningPathPagingService;
    let httpService: HttpClient;
    let getStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [{ provide: HttpClient, useClass: MockHttpService }],
        })
            .compileComponents()
            .then(() => {
                httpService = TestBed.inject(HttpClient);
                learningPathPagingService = new LearningPathPagingService(httpService);
                getStub = jest.spyOn(httpService, 'get');
            });
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
        } as SearchTermPageableSearch;
        learningPathPagingService.search(pageable, { courseId: 1 }).subscribe();
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
