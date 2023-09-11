import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PlagiarismResultsService } from 'app/course/plagiarism-cases/shared/plagiarism-results.service';

describe('Plagiarism Results Service', () => {
    let service: PlagiarismResultsService;
    let httpMock: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(PlagiarismResultsService);
        httpMock = TestBed.inject(HttpTestingController);
    });
    it('should make GET request to retrieve number of plagiarism results', fakeAsync(() => {
        const numberOfResults = 2;
        service.getNumberOfPlagiarismResultsForExercise(1).subscribe((resp) => expect(resp).toEqual(numberOfResults));
        const req = httpMock.expectOne({ method: 'GET', url: 'api/exercises/1/potential-plagiarism-count' });
        req.flush(numberOfResults);
        tick();
    }));
});
