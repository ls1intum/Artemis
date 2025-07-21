import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CourseTrainingQuizService } from './course-training-quiz.service';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('CourseTrainingQuizService', () => {
    let service: CourseTrainingQuizService;
    let httpMock: HttpTestingController;
    let exerciseId: number;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), CourseTrainingQuizService, { provide: AccountService, useClass: MockAccountService }],
        });
        service = TestBed.inject(CourseTrainingQuizService);
        httpMock = TestBed.inject(HttpTestingController);
        exerciseId = 123;
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should fetch an Array of quiz questions', () => {
        const courseId = 1;
        const mockQuestions: QuizQuestion[] = [{ id: 1 } as QuizQuestion];
        service.getQuizQuestions(courseId).subscribe((questions) => {
            expect(questions).toEqual(mockQuestions);
        });
        const req = httpMock.expectOne(`api/quiz/courses/${courseId}/practice/quiz`);
        expect(req.request.method).toBe('GET');
        req.flush(mockQuestions);
    });

    it('should submit submission for training', fakeAsync(() => {
        const mockSubmission = new QuizSubmission();
        const mockResult = new Result();
        mockResult.id = 1;
        mockResult.score = 10;
        service.submitForTraining(mockSubmission, exerciseId).subscribe((res) => {
            expect(res.body!.id).toBe(1);
            expect(res.body!.score).toBe(10);
        });

        const req = httpMock.expectOne({ method: 'POST', url: `api/quiz/exercises/${exerciseId}/submissions/training` });
        req.flush(mockResult);
        tick();
    }));
});
