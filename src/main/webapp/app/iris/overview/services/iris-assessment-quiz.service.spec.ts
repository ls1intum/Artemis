import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { IrisAssessmentQuizService } from 'app/iris/overview/services/iris-assessment-quiz.service';
import { IrisInClassQuizDTO } from 'app/iris/shared/entities/iris-in-class-quiz-dto.model';

describe('IrisAssessmentQuizService', () => {
    let service: IrisAssessmentQuizService;
    let httpMock: HttpTestingController;

    const exerciseId = 42;
    const completedUrl = `api/iris/programming-exercises/${exerciseId}/assessment-quiz/completed`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisAssessmentQuizService],
        });

        service = TestBed.inject(IrisAssessmentQuizService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should start the in-class quiz window and expose the active timer', () => {
        const expiresAt = dayjs().add(2, 'minutes');
        const activeInClassQuizzes: (IrisInClassQuizDTO | undefined)[] = [];
        const subscription = service.currentInClassQuizForExercise(exerciseId).subscribe((activeInClassQuiz) => activeInClassQuizzes.push(activeInClassQuiz));

        service.startInClassQuiz(exerciseId).subscribe((response) => {
            expect(response.body?.timerExpiresAt.isSame(expiresAt)).toBeTrue();
            expect(response.body?.timeLimit).toBeGreaterThan(0);
        });

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/assessment-quiz/in-class/start`);
        expect(request.request.method).toBe('PATCH');
        request.flush({ timerExpiresAt: expiresAt.toJSON() });

        expect(activeInClassQuizzes[0]).toBeUndefined();
        expect(activeInClassQuizzes.at(-1)?.timerExpiresAt.isSame(expiresAt)).toBeTrue();

        subscription.unsubscribe();
    });

    it('should get the active in-class quiz window from the server', () => {
        const expiresAt = dayjs().add(90, 'seconds');
        const timeLimit = 90;

        service.getActiveInClassQuiz(exerciseId).subscribe((response) => {
            expect(response.body?.timerExpiresAt.isSame(expiresAt)).toBeTrue();
            expect(response.body?.timeLimit).toBe(timeLimit);
        });

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/assessment-quiz/in-class`);
        expect(request.request.method).toBe('GET');
        request.flush({ timerExpiresAt: expiresAt.toJSON(), timeLimit });
    });

    it('should clear the active in-class quiz window', () => {
        const activeInClassQuizzes: (IrisInClassQuizDTO | undefined)[] = [];
        const subscription = service.currentInClassQuizForExercise(exerciseId).subscribe((activeInClassQuiz) => activeInClassQuizzes.push(activeInClassQuiz));

        service.clearActiveInClassQuiz(exerciseId);

        expect(activeInClassQuizzes.at(-1)).toBeUndefined();

        subscription.unsubscribe();
    });

    it('should expose whether in-class prompting mode has started for an exercise', () => {
        const startedInClassQuizzes: boolean[] = [];
        const subscription = service.currentStartedInClassQuizForExercise(exerciseId).subscribe((started) => startedInClassQuizzes.push(started));

        service.setInClassPromptingModeStarted(exerciseId, true);
        service.setInClassPromptingModeStarted(exerciseId, false);

        expect(startedInClassQuizzes).toEqual([false, true, false]);

        subscription.unsubscribe();
    });

    it('should check whether the latest submission has points', () => {
        service.latestSubmissionHasPoints(exerciseId).subscribe((hasPoints) => expect(hasPoints).toBeTrue());

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/assessment-quiz/latest-submission-has-points`);
        expect(request.request.method).toBe('GET');
        request.flush(true);
    });

    it('should check whether the in-class quiz is already done', () => {
        service.isQuizAlreadyDone(exerciseId, true).subscribe((quizAlreadyDone) => expect(quizAlreadyDone).toBeTrue());

        const request = httpMock.expectOne((req) => req.url === completedUrl && req.params.get('inClass') === 'true');
        expect(request.request.method).toBe('GET');
        request.flush(true);
    });

    it('should check whether the regular quiz is already done', () => {
        service.isQuizAlreadyDone(exerciseId, false).subscribe((quizAlreadyDone) => expect(quizAlreadyDone).toBeFalse());

        const request = httpMock.expectOne((req) => req.url === completedUrl && req.params.get('inClass') === 'false');
        expect(request.request.method).toBe('GET');
        request.flush(false);
    });
});
