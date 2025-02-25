import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { VcsRepositoryAccessLogService } from '../../../../main/webapp/app/localvc/vcs-repository-access-log-view/vcs-repository-access-log.service';
import { VcsAccessLogDTO } from '../../../../main/webapp/app/entities/vcs-access-log-entry.model';
import { SearchResult, SearchTermPageableSearch } from '../../../../main/webapp/app/shared/table/pageable-table';
import { RepositoryType } from '../../../../main/webapp/app/exercises/programming/shared/code-editor/model/code-editor.model';

describe('VcsRepositoryAccessLogService', () => {
    let service: VcsRepositoryAccessLogService;
    let httpMock: HttpTestingController;

    const mockVcsAccessLog: VcsAccessLogDTO[] = [
        {
            id: 1,
            userId: 4,
            name: 'authorName',
            email: 'authorEmail',
            commitHash: 'abcde',
            authenticationMechanism: 'SSH',
            repositoryActionType: 'WRITE',
            timestamp: '2021-01-02T00:00:00Z',
        },
        {
            id: 2,
            userId: 4,
            name: 'authorName',
            email: 'authorEmail',
            commitHash: 'fffee',
            authenticationMechanism: 'SSH',
            repositoryActionType: 'READ',
            timestamp: '2021-01-03T00:00:00Z',
        },
    ];

    const pageable: SearchTermPageableSearch = {
        page: 1,
        pageSize: 10,
        sortingOrder: 'ASCENDING',
        sortedColumn: 'id',
    };

    const options = {
        exerciseId: 4,
        repositoryId: 5,
        repositoryType: RepositoryType.USER,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [VcsRepositoryAccessLogService],
        });

        service = TestBed.inject(VcsRepositoryAccessLogService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should send a GET request with correct query parameters', async () => {
        const expectedResponse: SearchResult<VcsAccessLogDTO> = {
            resultsOnPage: mockVcsAccessLog,
            numberOfPages: 1,
        };

        const requestPromise = service.search(pageable, options);

        const req = httpMock.expectOne((request) => {
            const urlMatches = request.url === `api/programming-exercises/${options.exerciseId}/repository/${options.repositoryType}/vcs-access-log`;

            const params = request.params;
            const paramsMatch =
                params.get('page') === pageable.page.toString() &&
                params.get('pageSize') === pageable.pageSize.toString() &&
                params.get('sortingOrder') === pageable.sortingOrder && // Ensure this matches ASCENDING
                params.get('sortedColumn') === pageable.sortedColumn &&
                params.get('repositoryId') === options.repositoryId.toString();

            return urlMatches && paramsMatch;
        });

        expect(req.request.method).toBe('GET');
        req.flush(expectedResponse);

        const result = await requestPromise;
        expect(result.resultsOnPage).toEqual(mockVcsAccessLog);
    });
});
