import { expect } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { take } from 'rxjs/operators';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExampleSubmissionImportPagingService } from 'app/exercise/example-submission/example-submission-import/example-submission-import-paging.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { provideHttpClient } from '@angular/common/http';

describe('Example Submission Import Paging Service', () => {
    setupTestBed({ zoneless: true });
    let service: ExampleSubmissionImportPagingService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }, SessionStorageService, LocalStorageService],
        });
        service = TestBed.inject(ExampleSubmissionImportPagingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should find submission', () => {
        const exercise = {
            id: 1,
        } as Exercise;
        const searchResult = { resultsOnPage: [new TextSubmission()], numberOfPages: 4 };
        const pageable = { pageSize: 2, page: 3, sortingOrder: SortingOrder.DESCENDING, searchTerm: 'testSearchTerm', sortedColumn: 'testSortedColumn' };
        service
            .search(pageable, { exerciseId: exercise.id! })
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(searchResult));
        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.params.get('pageSize')).toBe('2');
        expect(req.request.params.get('page')).toBe('3');
        expect(req.request.params.get('sortingOrder')).toEqual(SortingOrder.DESCENDING);
        expect(req.request.params.get('searchTerm')).toBe('testSearchTerm');
        expect(req.request.params.get('sortedColumn')).toBe('testSortedColumn');

        req.flush(searchResult);
    });
});
