import { TestBed } from '@angular/core/testing';
import { CoursePracticeQuizService } from './course-practice-quiz.service';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('CoursePracticeQuizService', () => {
    let service: CoursePracticeQuizService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), CoursePracticeQuizService],
        });
        service = TestBed.inject(CoursePracticeQuizService);
        httpMock = TestBed.inject(HttpTestingController);
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
});
