import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { take } from 'rxjs/operators';
import { PlagiarismCase } from 'app/course/plagiarism-cases/types/PlagiarismCase';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { Notification } from 'app/entities/notification.model';

describe('Plagiarism Cases Service', () => {
    let injector: TestBed;
    let service: PlagiarismCasesService;
    let httpMock: HttpTestingController;

    const studentLoginA = 'student1A';
    const studentLoginB = 'student1B';

    const notification = {
        text: 'message',
    } as Notification;

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
        injector = getTestBed();
        service = injector.get(PlagiarismCasesService);
        httpMock = injector.get(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should get plagiarism cases', fakeAsync(() => {
        const returnedFromService = [plagiarismCase];
        service.getPlagiarismCases(1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should send plagiarism notification', fakeAsync(() => {
        const returnedFromService = Object.assign({}, notification);
        service.sendPlagiarismNotification(studentLoginA, 1, 'message').pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get anonymous plagiarism comparison', fakeAsync(() => {
        const returnedFromService = Object.assign({}, plagiarismCase);
        service.getAnonymousPlagiarismComparison(1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should send statement', fakeAsync(() => {
        const returnedFromService = 'statement';
        service.sendStatement(1, 'statement').pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should update plagiarism status', fakeAsync(() => {
        const returnedFromService = {};
        service.updatePlagiarismStatus(true, 1, studentLoginA).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));
});
