import { TranslateService } from '@ngx-translate/core';
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { map, take } from 'rxjs/operators';
import { SessionStorageService } from 'ngx-webstorage';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../test.module';

describe('QuizExercise Service', () => {
    let service: QuizExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: QuizExercise;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(QuizExerciseService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new QuizExercise(new Course(), undefined);
    });

    it('should find an element', async () => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .find(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should create a QuizExercise', async () => {
        const returnedFromService = Object.assign(
            {
                id: 0,
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        service
            .create(new QuizExercise(undefined, undefined))
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should update a QuizExercise', async () => {
        const returnedFromService = Object.assign(
            {
                description: 'BBBBBB',
                explanation: 'BBBBBB',
                randomizeQuestionOrder: true,
                allowedNumberOfAttempts: 1,
                isVisibleBeforeStart: true,
                isOpenForPractice: true,
                isPlannedToStart: true,
                duration: 1,
            },
            elemDefault,
        );

        const expected = Object.assign({}, returnedFromService);
        service
            .update(expected)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should return a list of QuizExercise', async () => {
        const returnedFromService = Object.assign(
            {
                description: 'BBBBBB',
                explanation: 'BBBBBB',
                randomizeQuestionOrder: true,
                allowedNumberOfAttempts: 1,
                isVisibleBeforeStart: true,
                isOpenForPractice: true,
                isPlannedToStart: true,
                duration: 1,
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        service
            .query()
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toContainEqual(expected));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify([returnedFromService]));
        httpMock.verify();
    });

    it('should delete a QuizExercise', async () => {
        service.delete(123).subscribe((resp) => expect(resp.ok));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
    });

    afterEach(() => {
        httpMock.verify();
    });
});
