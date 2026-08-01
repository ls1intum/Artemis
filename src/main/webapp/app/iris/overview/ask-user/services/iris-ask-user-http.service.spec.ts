import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { vi } from 'vitest';

import { IrisAskUserHttpService, IrisAskUserQuizType } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';

describe('IrisAskUserHttpService', () => {
    let service: IrisAskUserHttpService;
    let httpMock: HttpTestingController;
    let clearChatSpy: ReturnType<typeof vi.fn>;

    const exerciseId = 42;
    const completedUrl = `api/iris/programming-exercises/${exerciseId}/ask-user/completed`;

    beforeEach(() => {
        clearChatSpy = vi.fn(() => Promise.resolve());
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisAskUserHttpService, { provide: IrisChatService, useValue: { clearChat: clearChatSpy } }],
        });

        service = TestBed.inject(IrisAskUserHttpService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should clear the chat before starting ask-user mode', async () => {
        const result = firstValueFrom(service.startQuiz(exerciseId));

        expect(clearChatSpy).toHaveBeenCalledOnce();
        await Promise.resolve();

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/start`);
        expect(request.request.method).toBe('PATCH');
        request.flush(null);

        await expect(result).resolves.toBeUndefined();
    });

    it('should clear the chat before starting in-class ask-user mode', async () => {
        const result = firstValueFrom(service.startInClassQuiz(exerciseId));

        expect(clearChatSpy).toHaveBeenCalledOnce();
        await Promise.resolve();

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class/start`);
        expect(request.request.method).toBe('PATCH');
        request.flush(null);

        await expect(result).resolves.toBeUndefined();
    });

    it('should check whether ask-user mode is started', () => {
        service.currentStartedQuizForExercise(exerciseId).subscribe((started) => expect(started).toBeTrue());

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/is-quiz-started`);
        expect(request.request.method).toBe('GET');
        request.flush(true);
    });

    it('should check whether in-class ask-user mode is started', () => {
        service.currentStartedInClassQuizForExercise(exerciseId).subscribe((started) => expect(started).toBeTrue());

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class/is-quiz-started`);
        expect(request.request.method).toBe('GET');
        request.flush(true);
    });

    it('should expose the active ask-user quiz type', () => {
        const activeQuizTypes: (IrisAskUserQuizType | undefined)[] = [];
        const subscription = service.activeQuizTypeForExercise(exerciseId).subscribe((activeQuizType) => activeQuizTypes.push(activeQuizType));

        service.setActiveQuizTypeForExercise(exerciseId, 'regular');
        service.setActiveQuizTypeForExercise(exerciseId, 'inClass');
        service.clearActiveQuizTypeForExercise(exerciseId);

        expect(activeQuizTypes).toEqual([undefined, 'regular', 'inClass', undefined]);
        subscription.unsubscribe();
    });

    it('should check whether the latest submission has points', () => {
        service.latestSubmissionHasPoints(exerciseId).subscribe((hasPoints) => expect(hasPoints).toBeTrue());

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/latest-submission-has-points`);
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
