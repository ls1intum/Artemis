import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { QuizReEvaluateService } from 'app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate.service';
import { ArtemisTestModule } from '../test.module';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

describe('QuizReEvaluateService', () => {
    let service: QuizReEvaluateService;
    let httpMock: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [QuizReEvaluateService],
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
            expect(res.body).toEqual(quizExercise);
        });

        const req = httpMock.expectOne({ method: 'PUT', url: 'api/quiz-exercises/1/re-evaluate' });
        expect(req.request.body).toBeInstanceOf(FormData);
        expect(req.request.body.getAll('exercise')).toBeArrayOfSize(1);
        expect(req.request.body.get('exercise')).toBeInstanceOf(Blob);
        const formDataFiles = req.request.body.getAll('files');
        expect(formDataFiles).toBeArrayOfSize(2);
        expect(formDataFiles[0]).toBeInstanceOf(Blob);
        expect(formDataFiles[1]).toBeInstanceOf(Blob);
        req.flush(quizExercise);
        tick();
    }));
});
