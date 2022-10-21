import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { map, take } from 'rxjs/operators';
import { MockExerciseService } from '../helpers/mocks/service/mock-exercise.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../test.module';

describe('FileUploadExercise Service', () => {
    let service: FileUploadExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: FileUploadExercise;

    const course: Course = { id: 123 } as Course;
    const fileUploadExercise = new FileUploadExercise(course, undefined);
    fileUploadExercise.id = 456;
    fileUploadExercise.filePattern = 'pdf';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ExerciseService, useClass: MockExerciseService },
            ],
        });
        service = TestBed.inject(FileUploadExerciseService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new FileUploadExercise(undefined, undefined);
    });

    it('should find an element', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .find(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should create a FileUploadExercise', fakeAsync(() => {
        const returnedFromService = Object.assign(
            {
                id: 0,
            },
            elemDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        service
            .create(fileUploadExercise)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should update a FileUploadExercise', fakeAsync(() => {
        const returnedFromService = Object.assign(
            {
                filePattern: 'bbbbbb',
            },
            elemDefault,
        );

        const expected = Object.assign({}, returnedFromService);
        service
            .update(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should return a list of FileUploadExercise', fakeAsync(() => {
        const returnedFromService = Object.assign(
            {
                filePattern: 'BBBBBB',
            },
            elemDefault,
        );
        returnedFromService.assessmentDueDate = undefined;
        returnedFromService.assessmentDueDateError = false;
        returnedFromService.dueDate = undefined;
        returnedFromService.dueDateError = false;
        returnedFromService.releaseDate = undefined;
        returnedFromService.studentParticipations = [];

        const expected = Object.assign({}, returnedFromService);
        service
            .query(expected)
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toContainEqual(expected));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([returnedFromService]);
        httpMock.verify();
        tick();
    }));

    it('should delete a FileUploadExercise', fakeAsync(() => {
        service.delete(123).subscribe((resp) => expect(resp.ok).toBeTrue());

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();
    }));

    it('should update and re-evaluate a FileUploadExercise', fakeAsync(() => {
        const returnedFromService = Object.assign(
            {
                filePattern: 'BBBBBB',
            },
            elemDefault,
        );

        const expected = Object.assign({}, returnedFromService);
        service
            .reevaluateAndUpdate(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
