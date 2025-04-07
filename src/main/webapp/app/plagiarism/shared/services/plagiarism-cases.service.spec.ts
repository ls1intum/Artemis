import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { take } from 'rxjs/operators';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { TextSubmissionElement } from 'app/plagiarism/shared/entities/text/TextSubmissionElement';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { PlagiarismSubmission } from 'app/plagiarism/shared/entities/PlagiarismSubmission';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { provideHttpClient } from '@angular/common/http';

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
    } as PlagiarismComparison<TextSubmissionElement>;

    const textExercise = {
        id: 1,
        type: ExerciseType.TEXT,
        course: {
            id: 1,
        },
    } as TextExercise;
    const examTextExercise = { id: 1, type: ExerciseType.TEXT, exerciseGroup: { exam: { id: 1, course: { id: 1 } } } } as TextExercise;

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
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(PlagiarismCasesService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should get plagiarism cases for course for instructor', fakeAsync(() => {
        const returnedFromService = [plagiarismCase1, plagiarismCase2, plagiarismCase3];
        service.getCoursePlagiarismCasesForInstructor(1).pipe(take(1)).subscribe();

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
        service.saveVerdict(1, 1, { verdict: PlagiarismVerdict.PLAGIARISM }).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get plagiarism case for course and exercise for student', fakeAsync(() => {
        const returnedFromService = plagiarismCase1;
        service.getPlagiarismCaseInfoForStudent(1, 1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get plagiarism cases for course and multiple exercises for student', fakeAsync(() => {
        service.getPlagiarismCaseInfosForStudent(1, [1, 2]).pipe(take(1)).subscribe();

        httpMock.expectOne({ method: 'GET', url: 'api/plagiarism/courses/1/plagiarism-cases?exerciseId=1&exerciseId=2' });
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
        service.getPlagiarismComparisonForSplitView(1, 1).pipe(take(1)).subscribe();

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
    it.each([textExercise, examTextExercise])(
        'should make GET request to retrieve number of plagiarism cases',
        fakeAsync(() => {
            const numberOfResultsExercise = 2;
            service.getNumberOfPlagiarismCasesForExercise(textExercise).subscribe((resp) => expect(resp).toEqual(numberOfResultsExercise));
            const req = httpMock.expectOne({ method: 'GET', url: 'api/plagiarism/courses/1/exercises/1/plagiarism-cases-count' });
            req.flush(numberOfResultsExercise);
            tick();
        }),
    );
});
