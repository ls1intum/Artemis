import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { take } from 'rxjs/operators';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';

describe('Plagiarism Cases Service', () => {
    let service: PlagiarismCasesService;
    let httpMock: HttpTestingController;

    const studentLoginA = 'student1A';
    const studentLoginB = 'student1B';

    const plagiarismComparison1 = {
        id: 1,
        submissionA: { studentLogin: studentLoginA },
        submissionB: { studentLogin: studentLoginB },
        similarity: 0.5,
        status: PlagiarismStatus.CONFIRMED,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    const plagiarismComparison2 = {
        id: 2,
        submissionA: { studentLogin: studentLoginA },
        submissionB: { studentLogin: studentLoginB },
        similarity: 0.7,
        status: PlagiarismStatus.DENIED,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;

    const textExercise = { id: 1, type: ExerciseType.TEXT } as TextExercise;

    const plagiarismCase = {
        exercise: textExercise,
        comparisons: [plagiarismComparison1, plagiarismComparison2],
    } as PlagiarismCase;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(PlagiarismCasesService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should get plagiarism cases for course', fakeAsync(() => {
        const returnedFromService = [plagiarismCase];
        service.getPlagiarismCases(1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should save instructor statement', fakeAsync(() => {
        const returnedFromService = 'statement';
        service.saveInstructorStatement(1, 1, studentLoginA, 'statement').pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get plagiarism comparison for student', fakeAsync(() => {
        const returnedFromService = Object.assign({}, plagiarismCase);
        service.getPlagiarismComparisonForStudent(1, 1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should save student statement', fakeAsync(() => {
        const returnedFromService = 'statement';
        service.saveStudentStatement(1, 1, 'statement').pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should update plagiarism comparison status', fakeAsync(() => {
        const returnedFromService = {};
        service.updatePlagiarismComparisonStatus(1, 1, PlagiarismStatus.CONFIRMED).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should update plagiarism comparison final status', fakeAsync(() => {
        const returnedFromService = {};
        service.updatePlagiarismComparisonFinalStatus(1, 1, true, studentLoginA).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));
});
