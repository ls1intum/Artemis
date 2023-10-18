import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { QuizPoolService } from 'app/exercises/quiz/manage/quiz-pool.service';
import { ArtemisTestModule } from '../test.module';
import { QuizPool } from 'app/entities/quiz/quiz-pool.model';
import { firstValueFrom } from 'rxjs';

describe('QuizPoolService', () => {
    let quizPoolService: QuizPoolService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [QuizPoolService],
        });
        quizPoolService = TestBed.inject(QuizPoolService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should return updated quiz pool', async () => {
        const updatedQuizPool = new QuizPool();
        updatedQuizPool.id = 1;
        const courseId = 2;
        const examId = 3;
        const quizPool = new QuizPool();
        const response = firstValueFrom(quizPoolService.update(courseId, examId, quizPool));
        const req = httpMock.expectOne({
            method: 'PUT',
            url: `api/courses/${courseId}/exams/${examId}/quiz-pools`,
        });
        req.flush(updatedQuizPool);
        expect((await response)?.body).toEqual(updatedQuizPool);
    });

    it('should return quiz pool', async () => {
        const quizPool = new QuizPool();
        quizPool.id = 1;
        const courseId = 2;
        const examId = 3;
        const response = firstValueFrom(quizPoolService.find(courseId, examId));
        const req = httpMock.expectOne({
            method: 'GET',
            url: `api/courses/${courseId}/exams/${examId}/quiz-pools`,
        });
        req.flush(quizPool);
        expect((await response)?.body).toEqual(quizPool);
    });
});
