import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { IrisAskUserHttpService } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';

describe('IrisAskUserHttpService', () => {
    let service: IrisAskUserHttpService;
    let httpMock: HttpTestingController;

    const exerciseId = 42;
    const resourceUrl = `api/iris/programming-exercises/${exerciseId}/ask-user`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisAskUserHttpService],
        });

        service = TestBed.inject(IrisAskUserHttpService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should check whether ask-user mode is started', () => {
        service.currentStartedQuizForExercise(exerciseId).subscribe((started) => expect(started).toBe(true));

        const request = httpMock.expectOne(`${resourceUrl}/is-quiz-started`);
        expect(request.request.method).toBe('GET');
        request.flush(true);
    });

    it('should check whether in-class ask-user mode is started', () => {
        service.currentStartedInClassQuizForExercise(exerciseId).subscribe((started) => expect(started).toBe(true));

        const request = httpMock.expectOne(`${resourceUrl}/in-class/is-quiz-started`);
        expect(request.request.method).toBe('GET');
        request.flush(true);
    });

    it('should register a defocus event for the current session', () => {
        service.registerDefocusForCurrentSession(exerciseId).subscribe((response) => expect(response.status).toBe(204));

        const request = httpMock.expectOne(`${resourceUrl}/defocus`);
        expect(request.request.method).toBe('PATCH');
        expect(request.request.body).toEqual({});
        request.flush(null);
    });

    it('should start the ask-user timer', () => {
        const timerExpiresAt = dayjs().add(20, 'seconds').toJSON();

        service.startTimer(exerciseId).subscribe((response) => {
            expect(response.body).toEqual({ timerExpiresAt, timeLimit: 20 });
        });

        const request = httpMock.expectOne(`${resourceUrl}/start-timer`);
        expect(request.request.method).toBe('PATCH');
        expect(request.request.body).toEqual({});
        request.flush({ timerExpiresAt, timeLimit: 20 });
    });

    it('should start the regular ask-user quiz', () => {
        service.startQuiz(exerciseId).subscribe((response) => expect(response.status).toBe(204));

        const request = httpMock.expectOne(`${resourceUrl}/start`);
        expect(request.request.method).toBe('PATCH');
        expect(request.request.body).toBeNull();
        request.flush(null);
    });

    it('should start the in-class ask-user quiz', () => {
        service.startInClassQuiz(exerciseId).subscribe((response) => expect(response.status).toBe(204));

        const request = httpMock.expectOne(`${resourceUrl}/in-class/start`);
        expect(request.request.method).toBe('PATCH');
        expect(request.request.body).toBeNull();
        request.flush(null);
    });

    it('should check whether the latest submission has points', () => {
        service.latestSubmissionHasPoints(exerciseId).subscribe((hasPoints) => expect(hasPoints).toBe(true));

        const request = httpMock.expectOne(`${resourceUrl}/latest-submission-has-points`);
        expect(request.request.method).toBe('GET');
        request.flush(true);
    });

    it('should check whether the in-class quiz is already done', () => {
        service.isQuizAlreadyDone(exerciseId, true).subscribe((quizAlreadyDone) => expect(quizAlreadyDone).toBe(true));

        const request = httpMock.expectOne((req) => req.url === `${resourceUrl}/completed` && req.params.get('inClass') === 'true');
        expect(request.request.method).toBe('GET');
        request.flush(true);
    });

    it('should check whether the regular quiz is already done', () => {
        service.isQuizAlreadyDone(exerciseId, false).subscribe((quizAlreadyDone) => expect(quizAlreadyDone).toBe(false));

        const request = httpMock.expectOne((req) => req.url === `${resourceUrl}/completed` && req.params.get('inClass') === 'false');
        expect(request.request.method).toBe('GET');
        request.flush(false);
    });

    it('should stop the ask-user timer', () => {
        service.stopTimer(exerciseId).subscribe((response) => expect(response.status).toBe(204));

        const request = httpMock.expectOne(`${resourceUrl}/stop-timer`);
        expect(request.request.method).toBe('PATCH');
        expect(request.request.body).toEqual({});
        request.flush(null);
    });
});
