import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingExercisePagingService } from 'app/exercises/modeling/manage/modeling-exercise-paging.service';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs/operators';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';

describe('ModelingExercise Service', () => {
    let injector: TestBed;
    let service: ModelingExercisePagingService;
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
        service = injector.get(ModelingExercisePagingService);
        service.resourceUrl = 'resourceUrl';
        httpMock = injector.get(HttpTestingController);
    });

    it('should find an element', fakeAsync(() => {
        const searchResult = { resultsOnPage: [new ModelingExercise(UMLDiagramType.Flowchart, undefined, undefined)], numberOfPages: 5 };
        const pageable = { pageSize: 2, page: 3, sortingOrder: SortingOrder.DESCENDING, searchTerm: 'testSearchTerm', sortedColumn: 'testSortedColumn' };
        service
            .searchForExercises(pageable)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(searchResult));
        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.params.get('pageSize')).toBe('2');
        expect(req.request.params.get('page')).toBe('3');
        expect(req.request.params.get('sortingOrder')).toBe(SortingOrder.DESCENDING);
        expect(req.request.params.get('searchTerm')).toBe('testSearchTerm');
        expect(req.request.params.get('sortedColumn')).toBe('testSortedColumn');
        req.flush(searchResult);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
