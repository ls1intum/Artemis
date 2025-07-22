import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ExercisePagingService } from 'app/exercise/services/exercise-paging.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { take } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DummyPagingService } from 'test/helpers/mocks/service/dummy-paging-service';

describe('Exercise Paging Service', () => {
    let service: ExercisePagingService<any>;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }, SessionStorageService, LocalStorageService],
        });
        httpMock = TestBed.inject(HttpTestingController);
        const httpClient = TestBed.inject(HttpClient);
        service = new DummyPagingService(httpClient);
    });

    it('should find an element', fakeAsync(() => {
        const searchResult = { resultsOnPage: [new QuizExercise(undefined, undefined)], numberOfPages: 5 };
        const pageable = { pageSize: 2, page: 3, sortingOrder: SortingOrder.DESCENDING, searchTerm: 'testSearchTerm', sortedColumn: 'testSortedColumn' };
        service
            .search(pageable, { isCourseFilter: true, isExamFilter: true })
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(searchResult));
        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.params.get('pageSize')).toBe('2');
        expect(req.request.params.get('page')).toBe('3');
        expect(req.request.params.get('sortingOrder')).toBe(SortingOrder.DESCENDING);
        expect(req.request.params.get('searchTerm')).toBe('testSearchTerm');
        expect(req.request.params.get('sortedColumn')).toBe('testSortedColumn');
        expect(req.request.params.get('isCourseFilter')).toBe('true');
        expect(req.request.params.get('isExamFilter')).toBe('true');
        req.flush(searchResult);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
