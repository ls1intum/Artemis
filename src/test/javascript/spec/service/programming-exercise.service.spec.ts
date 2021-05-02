import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { map, take } from 'rxjs/operators';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { routes } from 'app/exercises/programming/manage/programming-exercise-management-routing.module';
import { RouterTestingModule } from '@angular/router/testing';
import { ArtemisTestModule } from '../test.module';
import { FormsModule } from '@angular/forms';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingExerciseManagementModule } from 'app/exercises/programming/manage/programming-exercise-management.module';
import * as moment from 'moment';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Result } from 'app/entities/result.model';

describe('ProgrammingExercise Service', () => {
    let injector: TestBed;
    let service: ProgrammingExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: ProgrammingExercise;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                HttpClientTestingModule,
                RouterTestingModule.withRoutes(routes),
                ArtemisTestModule,
                FormsModule,
                ArtemisSharedModule,
                ArtemisSharedComponentModule,
                ArtemisProgrammingExerciseManagementModule,
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        });
        injector = getTestBed();
        service = injector.get(ProgrammingExerciseService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new ProgrammingExercise(undefined, undefined);
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

        it('should create a ProgrammingExercise', async () => {
            const returnedFromService = Object.assign(
                {
                    id: 0,
                },
                elemDefault,
            );
            const expected = Object.assign({}, returnedFromService);
            service
                .automaticSetup(new ProgrammingExercise(undefined, undefined))
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should reconnect template submission with result', async () => {
            const templateParticipation = new TemplateProgrammingExerciseParticipation();
            const tempSubmission = new ProgrammingSubmission();
            const tempResult = new Result();
            tempResult.id = 2;
            tempSubmission.results = [tempResult];
            templateParticipation.submissions = [tempSubmission];
            const returnedFromService = Object.assign({ id: 0 }, { ...elemDefault, templateParticipation });
            const expected = Object.assign({}, returnedFromService);
            service
                .findWithTemplateAndSolutionParticipation(expected.id, true)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
        });

        it('should update a ProgrammingExercise', async () => {
            const returnedFromService = Object.assign(
                {
                    templateRepositoryUrl: 'BBBBBB',
                    solutionRepositoryUrl: 'BBBBBB',
                    templateBuildPlanId: 'BBBBBB',
                    publishBuildPlanUrl: true,
                    allowOnlineEditor: true,
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

        it('should update the Timeline of a ProgrammingExercise', async () => {
            const returnedFromService = Object.assign(
                {
                    releaseDate: moment('2020-12-10 10:00:00'),
                    dueDate: moment('2021-01-01 10:00:00'),
                    assessmentDueDate: moment('2021-01-02 10:00:00'),
                },
                elemDefault,
            );
            const expected = Object.assign({}, returnedFromService);
            service
                .updateTimeline(expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should return a list of ProgrammingExercise', async () => {
            const returnedFromService = Object.assign(
                {
                    templateRepositoryUrl: 'BBBBBB',
                    solutionRepositoryUrl: 'BBBBBB',
                    templateBuildPlanId: 'BBBBBB',
                    publishBuildPlanUrl: true,
                    allowOnlineEditor: true,
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
                .subscribe((body) => expect(body).toContain(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify([returnedFromService]));
            httpMock.verify();
        });

        it('should delete a ProgrammingExercise', async () => {
            service.delete(123, false, false).subscribe((resp) => expect(resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});
