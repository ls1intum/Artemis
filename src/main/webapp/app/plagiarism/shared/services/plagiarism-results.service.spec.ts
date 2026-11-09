import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PlagiarismResultsService } from 'app/plagiarism/shared/services/plagiarism-results.service';

describe('Plagiarism Results Service', () => {
    let service: PlagiarismResultsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(PlagiarismResultsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should make GET request to retrieve number of plagiarism results', () => {
        let result: number | undefined;
        service.getNumberOfPlagiarismResultsForExercise(1).subscribe((count) => (result = count));

        httpMock.expectOne({ method: 'GET', url: '/api/plagiarism/exercises/1/potential-plagiarism-count' }).flush(2);

        expect(result).toBe(2);
    });
});
