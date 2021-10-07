import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs/operators';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ExampleSubmissionImportPagingService } from 'app/exercises/shared/example-submission/example-submission-import/example-submission-import-paging.service';
import { Exercise } from 'app/entities/exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';

describe('Example Submission Import Paging Service', () => {
    let injector: TestBed;
    let service: ExampleSubmissionImportPagingService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        });
        injector = getTestBed();
        service = injector.get(ExampleSubmissionImportPagingService);
        httpMock = injector.get(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should find submission', fakeAsync(() => {
        const exercise = {
            id: 1,
        } as Exercise;
        const searchResult = { resultsOnPage: [new TextSubmission()], numberOfPages: 4 };
        const pageable = { pageSize: 2, page: 3, sortingOrder: SortingOrder.DESCENDING, searchTerm: 'testSearchTerm', sortedColumn: 'testSortedColumn' };
        service
            .searchForSubmissions(pageable, exercise.id!)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(searchResult));
        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.params.get('pageSize')).toEqual('2');
        expect(req.request.params.get('page')).toEqual('3');
        expect(req.request.params.get('sortingOrder')).toEqual(SortingOrder.DESCENDING);
        expect(req.request.params.get('searchTerm')).toEqual('testSearchTerm');
        expect(req.request.params.get('sortedColumn')).toEqual('testSortedColumn');

        req.flush(searchResult);
        tick();
    }));
});
