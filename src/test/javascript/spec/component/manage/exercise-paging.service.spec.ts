import { HttpClient } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { DummyPagingService } from './dummy-paging-service';

describe('Exercise Paging Service', () => {
    let service: ExercisePagingService<any>;
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
        httpMock = TestBed.inject(HttpTestingController);
        const httpClient = TestBed.get(HttpClient);
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
