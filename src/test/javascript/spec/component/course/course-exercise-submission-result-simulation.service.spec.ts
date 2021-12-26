import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { Result } from 'app/entities/result.model';
import { CourseExerciseSubmissionResultSimulationService } from 'app/course/manage/course-exercise-submission-result-simulation.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';

describe('Participation Service', () => {
    let injector: TestBed;
    let service: CourseExerciseSubmissionResultSimulationService;
    let httpMock: HttpTestingController;
    let exerciseId: number;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(CourseExerciseSubmissionResultSimulationService);
        httpMock = injector.get(HttpTestingController);
        exerciseId = 123;
    });

    it('should simulate submission', fakeAsync(() => {
        const mockSubmission = new ProgrammingSubmission();
        service.simulateSubmission(exerciseId).subscribe((res) => expect(res.body).toEqual(mockSubmission));

        const req = httpMock.expectOne({ method: 'POST', url: `api/exercises/${exerciseId}/submissions/no-vcs-and-ci-available` });
        req.flush(mockSubmission);
        tick();
    }));

    it('should simulate result', fakeAsync(() => {
        const mockResult = new Result();
        service.simulateResult(exerciseId).subscribe((res) => expect(res.body).toEqual(mockResult));

        const req = httpMock.expectOne({ method: 'POST', url: `api/exercises/${exerciseId}/results/no-vcs-and-ci-available` });
        req.flush(mockResult);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
