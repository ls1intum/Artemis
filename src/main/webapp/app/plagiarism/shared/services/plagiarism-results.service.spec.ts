import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PlagiarismResultsService } from 'app/plagiarism/shared/services/plagiarism-results.service';

describe('Plagiarism Results Service', () => {
    setupTestBed({ zoneless: true });

    let service: PlagiarismResultsService;
    let httpMock: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(PlagiarismResultsService);
        httpMock = TestBed.inject(HttpTestingController);
    });
    it('should make GET request to retrieve number of plagiarism results', () => {
        const numberOfResults = 2;
        service.getNumberOfPlagiarismResultsForExercise(1).subscribe((resp) => expect(resp).toEqual(numberOfResults));
        const req = httpMock.expectOne({ method: 'GET', url: 'api/plagiarism/exercises/1/potential-plagiarism-count' });
        req.flush(numberOfResults);
    });
});
