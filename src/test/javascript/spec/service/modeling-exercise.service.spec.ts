import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { UMLDiagramType, ModelingExercise } from 'app/entities/modeling-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { routes } from 'app/exercises/modeling/manage/modeling-exercise.route';
import { RouterTestingModule } from '@angular/router/testing';
import { ArtemisModelingExerciseModule } from 'app/exercises/modeling/manage/modeling-exercise.module';
import { expect } from '../entry';

describe('ModelingExercise Service', () => {
    let injector: TestBed;
    let service: ModelingExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: ModelingExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisModelingExerciseModule, HttpClientTestingModule, RouterTestingModule.withRoutes(routes)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        });
        injector = getTestBed();
        service = injector.get(ModelingExerciseService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined);
    });

    describe('Service methods', async () => {
        it('should find an element', async () => {
            const returnedFromService = Object.assign({}, elemDefault);
            service
                .find(123)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should create a ModelingExercise', async () => {
            const returnedFromService = Object.assign(
                {
                    id: 0,
                },
                elemDefault,
            );
            const expected = Object.assign({}, returnedFromService);
            service
                .create(new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined))
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should update a ModelingExercise', async () => {
            const returnedFromService = Object.assign(
                {
                    diagramType: 'BBBBBB',
                    sampleSolutionModel: 'BBBBBB',
                    sampleSolutionExplanation: 'BBBBBB',
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

        it('should delete a ModelingExercise', async () => {
            service.delete(123).subscribe((resp) => expect(resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});
