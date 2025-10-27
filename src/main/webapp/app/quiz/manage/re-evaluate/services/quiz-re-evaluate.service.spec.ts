import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { QuizReEvaluateService } from 'app/quiz/manage/re-evaluate/services/quiz-re-evaluate.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { provideHttpClient } from '@angular/common/http';

describe('QuizReEvaluateService', () => {
    let service: QuizReEvaluateService;
    let httpMock: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), QuizReEvaluateService],
        });
        service = TestBed.inject(QuizReEvaluateService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should send reevaluate request correctly', fakeAsync(() => {
        const quizExercise = { id: 1 } as QuizExercise;
        const files = new Map<string, Blob>();
        files.set('test1', new Blob());
        files.set('test2', new Blob());
        service.reevaluate(quizExercise, files).subscribe((res) => {
            expect(res.body).toBeNull();
            expect(res.ok).toBeTrue();
        });

        const req = httpMock.expectOne({ method: 'PUT', url: 'api/quiz/quiz-exercises/1/re-evaluate' });
        expect(req.request.body).toBeInstanceOf(FormData);
        expect(req.request.body.getAll('exercise')).toBeArrayOfSize(1);
        expect(req.request.body.get('exercise')).toBeInstanceOf(Blob);
        const formDataFiles = req.request.body.getAll('files');
        expect(formDataFiles).toBeArrayOfSize(2);
        expect(formDataFiles[0]).toBeInstanceOf(Blob);
        expect(formDataFiles[1]).toBeInstanceOf(Blob);
        req.flush(null, { status: 200, statusText: 'OK' });
        tick();
    }));
});
