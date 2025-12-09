import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { take } from 'rxjs/operators';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import dayjs from 'dayjs/esm';
import * as helper from 'app/shared/util/download.util';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { lastValueFrom } from 'rxjs';
import { UMLDiagramType } from '@ls1intum/apollon';
import { provideHttpClient } from '@angular/common/http';

describe('ModelingExercise Service', () => {
    let service: ModelingExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: ModelingExercise;
    const category = new ExerciseCategory('testCategory', 'red');
    const categories = [JSON.stringify(category) as unknown as ExerciseCategory] as ExerciseCategory[];
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                LocalStorageService,
                { provide: Router, useClass: MockRouter },
            ],
        });
        service = TestBed.inject(ModelingExerciseService);
        service.resourceUrl = 'resourceUrl';
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined);
        elemDefault.dueDate = dayjs();
        elemDefault.releaseDate = dayjs();
        elemDefault.assessmentDueDate = dayjs();
        elemDefault.studentParticipations = [];
    });

    it('should find an element', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .find(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(elemDefault));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should create a ModelingExercise', fakeAsync(() => {
        const returnedFromService = Object.assign({ id: 0 }, elemDefault, { categories });
        const expected = Object.assign({}, returnedFromService, { categories: [category] });
        service
            .create(new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined))
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toEqual(expected);
            });
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should update a ModelingExercise', fakeAsync(() => {
        const returnedFromService = Object.assign({ diagramType: UMLDiagramType.ClassDiagram, exampleSolutionModel: 'BBBBBB', exampleSolutionExplanation: 'BBBBBB' }, elemDefault, {
            categories,
        });

        const expected = Object.assign({}, returnedFromService, { categories: [category] });
        service
            .update(expected)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(expected));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should delete a ModelingExercise', fakeAsync(() => {
        service.delete(123).subscribe((resp) => expect(resp.ok).toBeTrue());

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
    }));

    it('should convert model to pdf', fakeAsync(() => {
        jest.spyOn(helper, 'downloadStream').mockReturnValue();
        const blob = new Blob(['test'], { type: 'text/html' }) as File;

        // We use a fake async and don't need to await the promise
        // eslint-disable-next-line jest/valid-expect
        expect(lastValueFrom(service.convertToPdf('model1', 'filename'))).resolves.toContainEntry(['body', blob]);
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(blob);
        tick();
    }));

    it('should re-evaluate and update a modelling exercise', () => {
        const modelingExerciseReturned = Object.assign({}, elemDefault);
        modelingExerciseReturned.id = 111;
        service
            .reevaluateAndUpdate(modelingExerciseReturned)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toBe(modelingExerciseReturned);
            });
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(modelingExerciseReturned);
    });

    it('should import a modeling exercise', fakeAsync(() => {
        const modelingExercise = Object.assign({}, elemDefault);
        modelingExercise.id = 445;
        modelingExercise.categories = [category];
        const returnedFromService = Object.assign({}, modelingExercise, { categories });

        const expected = Object.assign({}, returnedFromService, { categories: [category] });
        service
            .import(Object.assign({}, modelingExercise, { categories }))
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toEqual(expected);
            });
        const req = httpMock.expectOne({ method: 'POST', url: `${service.resourceUrl}/import/${modelingExercise.id}` });
        req.flush(returnedFromService);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
