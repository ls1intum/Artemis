import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
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

describe('Plagiarism Cases Service', () => {
    let service: PlagiarismCasesService;
    let httpMock: HttpTestingController;

    const studentLoginA = 'studentA';
    const studentLoginB = 'studentB';
    const studentLoginC = 'studentC';

    const plagiarismSubmission1 = {
        id: 1,
        studentLogin: studentLoginA,
    } as PlagiarismSubmission<TextSubmissionElement>;
    const plagiarismSubmission2 = {
        id: 2,
        studentLogin: studentLoginB,
    } as PlagiarismSubmission<TextSubmissionElement>;
    const plagiarismSubmission3 = {
        id: 3,
        studentLogin: studentLoginC,
    } as PlagiarismSubmission<TextSubmissionElement>;

    const plagiarismComparison1 = {
        id: 1,
        submissionA: plagiarismSubmission1,
        submissionB: plagiarismSubmission2,
        similarity: 0.5,
        status: PlagiarismStatus.CONFIRMED,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;

    const textExercise = { id: 1, type: ExerciseType.TEXT } as TextExercise;

    const plagiarismCase1 = {
        id: 1,
        student: { login: studentLoginA },
        exercise: textExercise,
        plagiarismSubmissions: [plagiarismSubmission1],
    } as PlagiarismCase;
    const plagiarismCase2 = {
        id: 2,
        student: { login: studentLoginB },
        exercise: textExercise,
        plagiarismSubmissions: [plagiarismSubmission2],
    } as PlagiarismCase;
    const plagiarismCase3 = {
        id: 3,
        student: { login: studentLoginC },
        exercise: textExercise,
        plagiarismSubmissions: [plagiarismSubmission3],
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

    it('should get plagiarism cases for course for instructor', fakeAsync(() => {
        const returnedFromService = [plagiarismCase1, plagiarismCase2, plagiarismCase3];
        service.getPlagiarismCasesForInstructor(1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get plagiarism case for course for instructor', fakeAsync(() => {
        const returnedFromService = plagiarismCase1;
        service.getPlagiarismCaseDetailForInstructor(1, 1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should save plagiarism case verdict', fakeAsync(() => {
        const returnedFromService = {
            ...plagiarismCase1,
            verdict: PlagiarismVerdict.PLAGIARISM,
        };
        service.savePlagiarismCaseVerdict(1, 1, { verdict: PlagiarismVerdict.PLAGIARISM }).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get plagiarism cases for course for student', fakeAsync(() => {
        const returnedFromService = plagiarismCase1;
        service.getPlagiarismCaseForStudent(1, 1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get plagiarism case for course for student', fakeAsync(() => {
        const returnedFromService = plagiarismCase1;
        service.getPlagiarismCaseDetailForStudent(1, 1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get plagiarism comparison for split view', fakeAsync(() => {
        const returnedFromService = plagiarismComparison1;
        service.getPlagiarismComparisonForSplitView(1, 1, studentLoginA).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
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

    it('should clean up plagiarism', fakeAsync(() => {
        const returnedFromService = {};
        service.cleanUpPlagiarism(1, 1, true).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush(returnedFromService);
        tick();
    }));
});
