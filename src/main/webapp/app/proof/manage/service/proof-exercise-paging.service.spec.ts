import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProofExercisePagingService } from 'app/proof/manage/service/proof-exercise-paging.service';

describe('ProofExercisePagingService', () => {
    setupTestBed({ zoneless: true });

    let service: ProofExercisePagingService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), ProofExercisePagingService],
        });
        service = TestBed.inject(ProofExercisePagingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('is wired to the proof-exercises resource URL', async () => {
        const pageable = { pageSize: 10, page: 0, sortingOrder: 'ASCENDING', sortedColumn: 'ID', searchTerm: '' } as any;
        const options = { isCourseFilter: true, isExamFilter: false } as any;
        const promise = service.search(pageable, options).toPromise();
        const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === 'api/proof/proof-exercises');
        req.flush({ resultsOnPage: [], numberOfPages: 0 });
        await promise;
        expect(req.request.url).toBe('api/proof/proof-exercises');
    });
});
