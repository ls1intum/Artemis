import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import dayjs from 'dayjs/esm';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { PlagiarismOptions } from 'app/exercises/shared/plagiarism/types/PlagiarismOptions';
import * as helper from 'app/shared/util/download.util';
import { Router } from '@angular/router';
import { MockRouter } from '../helpers/mocks/mock-router';
import { lastValueFrom } from 'rxjs';
import { UMLDiagramType } from '@ls1intum/apollon';

describe('ModelingExercise Service', () => {
    let service: ModelingExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: ModelingExercise;
    let plagiarismResult: ModelingPlagiarismResult;
    const category = { color: 'red', category: 'testCategory' } as ExerciseCategory;
    const categories = [JSON.stringify(category) as ExerciseCategory];
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
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
        plagiarismResult = new ModelingPlagiarismResult();
        plagiarismResult.exercise = elemDefault;
        plagiarismResult.comparisons = [];
        plagiarismResult.duration = 43;
        plagiarismResult.similarityDistribution = [3, 10];
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
        const returnedFromService = {
            id: 0,
            ...elemDefault,
            categories,
        };
        const expected = { ...returnedFromService, categories: [category] };
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
        const returnedFromService = {
            diagramType: UMLDiagramType.ClassDiagram,
            exampleSolutionModel: 'BBBBBB',
            exampleSolutionExplanation: 'BBBBBB',
            ...elemDefault,
            categories,
        };

        const expected = { ...returnedFromService, categories: [category] };
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
        const modelingExerciseReturned = { ...elemDefault };
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
        const modelingExercise = { ...elemDefault };
        modelingExercise.id = 445;
        modelingExercise.categories = [category];
        const returnedFromService = {
            ...modelingExercise,
            categories,
        };

        const expected = { ...returnedFromService, categories: [category] };
        service
            .import({ ...modelingExercise, categories })
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toEqual(expected);
            });
        const req = httpMock.expectOne({ method: 'POST', url: `${service.resourceUrl}/import/${modelingExercise.id}` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should check plagiarism result', fakeAsync(() => {
        elemDefault.id = 756;

        const returnedFromService = {
            ...plagiarismResult,
        };

        const options = new PlagiarismOptions(9, 4, 6);

        const expected = { ...returnedFromService };
        service
            .checkPlagiarism(elemDefault.id, options)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(expected));
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${elemDefault.id}/check-plagiarism?similarityThreshold=9&minimumScore=4&minimumSize=6` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get plagiarism result', fakeAsync(() => {
        elemDefault.id = 756;

        const returnedFromService = {
            ...plagiarismResult,
        };

        const expected = { ...returnedFromService };
        service
            .getLatestPlagiarismResult(elemDefault.id)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(expected));
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${elemDefault.id}/plagiarism-result` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get number of clusters', fakeAsync(() => {
        elemDefault.id = 756;
        const expected = 1;
        service
            .getNumberOfClusters(elemDefault.id)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(expected));
        const req = httpMock.expectOne({ method: 'GET', url: `${service.adminResourceUrl}/${elemDefault.id}/check-clusters` });
        req.flush(expected);
        tick();
    }));

    it('should build clusters', fakeAsync(() => {
        elemDefault.id = 756;
        const expected = {};
        service
            .buildClusters(elemDefault.id)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(expected));
        const req = httpMock.expectOne({ method: 'POST', url: `${service.adminResourceUrl}/${elemDefault.id}/trigger-automatic-assessment` });
        req.flush(expected);
        tick();
    }));

    it('should delete clusters', fakeAsync(() => {
        elemDefault.id = 756;

        // We use a fake async and don't need to await the promise
        // eslint-disable-next-line jest/valid-expect
        expect(lastValueFrom(service.deleteClusters(elemDefault.id))).toResolve();
        const req = httpMock.expectOne({ method: 'DELETE', url: `${service.adminResourceUrl}/${elemDefault.id}/clusters` });
        req.flush(null);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
