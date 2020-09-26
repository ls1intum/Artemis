import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { map, take } from 'rxjs/operators';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Router } from '@angular/router';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockRouter } from '../helpers/mocks/mock-router';
import { expect } from '../helpers/jasmine.jest.fix';

describe('TextExercise Service', () => {
    let injector: TestBed;
    let service: TextExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: TextExercise;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        });
        injector = getTestBed();
        service = injector.get(TextExerciseService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new TextExercise(new Course(), undefined);
    });

    describe('Service methods', () => {
        it('should find an element', async () => {
            const returnedFromService = Object.assign({}, elemDefault);
            service
                .find(123)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should create a TextExercise', async () => {
            const returnedFromService = Object.assign(
                {
                    id: 0,
                },
                elemDefault,
            );
            const expected = Object.assign({}, returnedFromService);
            service
                .create(new TextExercise(undefined, undefined))
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should update a TextExercise', async () => {
            const returnedFromService = Object.assign(
                {
                    sampleSolution: 'BBBBBB',
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

        it('should return a list of TextExercise', async () => {
            const returnedFromService = Object.assign(
                {
                    sampleSolution: 'BBBBBB',
                },
                elemDefault,
            );
            const expected = Object.assign({}, returnedFromService);
            service
                .query(expected)
                .pipe(
                    take(1),
                    map((resp) => resp.body),
                )
                .subscribe((body) => expect(body).toContainEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify([returnedFromService]));
            httpMock.verify();
        });

        it('should delete a TextExercise', async () => {
            service.delete(123).subscribe((resp) => expect(resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});
