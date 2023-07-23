import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { take } from 'rxjs/operators';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { PlagiarismResultsService } from 'app/course/plagiarism-cases/shared/plagiarism-results.service';
import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';

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
        const req = httpMock.expectOne({ method: 'GET', url: 'api/exercises/1/plagiarism-results' });
        req.flush(numberOfResults);
        tick();
    }));
});
