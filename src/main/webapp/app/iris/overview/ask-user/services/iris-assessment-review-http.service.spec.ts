import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { take } from 'rxjs';
import dayjs from 'dayjs/esm';

import { ParticipationService } from 'app/exercise/participation/participation.service';
import { IrisAssessmentReviewHttpService } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { IrisInClassQuizDTO } from 'app/iris/shared/entities/iris-in-class-quiz-dto.model';

describe('IrisAssessmentReviewHttpService', () => {
    let service: IrisAssessmentReviewHttpService;
    let httpMock: HttpTestingController;

    const exerciseId = 42;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), IrisAssessmentReviewHttpService, { provide: ParticipationService, useValue: {} }],
        });

        service = TestBed.inject(IrisAssessmentReviewHttpService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should make the in-class quiz available and expose the active timer', () => {
        const expiresAt = dayjs().add(2, 'minutes');

        service.makeInClassQuizAvailable(exerciseId).subscribe((response) => {
            expect(response.body?.timerExpiresAt.isSame(expiresAt)).toBeTrue();
            expect(response.body?.timeLimit).toBeGreaterThan(0);
        });

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class/available`);
        expect(request.request.method).toBe('PATCH');
        request.flush({ timerExpiresAt: expiresAt.toJSON() });

        service
            .availableInClassQuizForExercise(exerciseId)
            .pipe(take(1))
            .subscribe((activeInClassQuiz) => expect(activeInClassQuiz?.timerExpiresAt.isSame(expiresAt)).toBeTrue());
    });

    it('should get the active in-class quiz window from the server', () => {
        const expiresAt = dayjs().add(90, 'seconds');
        const timeLimit = 90;

        service.getAvailableInClassQuiz(exerciseId).subscribe((response) => {
            expect(response.body?.timerExpiresAt.isSame(expiresAt)).toBeTrue();
            expect(response.body?.timeLimit).toBe(timeLimit);
        });

        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class`);
        expect(request.request.method).toBe('GET');
        request.flush({ timerExpiresAt: expiresAt.toJSON(), timeLimit });
    });

    it('should clear the active in-class quiz window', () => {
        const expiresAt = dayjs().add(2, 'minutes');
        const activeInClassQuizzes: (IrisInClassQuizDTO | undefined)[] = [];

        service.makeInClassQuizAvailable(exerciseId).subscribe();
        const request = httpMock.expectOne(`api/iris/programming-exercises/${exerciseId}/ask-user/in-class/available`);
        request.flush({ timerExpiresAt: expiresAt.toJSON() });

        const subscription = service.availableInClassQuizForExercise(exerciseId).subscribe((activeInClassQuiz) => activeInClassQuizzes.push(activeInClassQuiz));

        service.clearActiveInClassQuiz(exerciseId);

        expect(activeInClassQuizzes.at(-1)).toBeUndefined();

        subscription.unsubscribe();
    });
});
