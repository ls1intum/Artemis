import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { map, take } from 'rxjs/operators';
import dayjs from 'dayjs';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockRouter } from '../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';

describe('Participation Service', () => {
    let injector: TestBed;
    let service: ParticipationService;
    let httpMock: HttpTestingController;
    let participationDefault: Participation;
    let currentDate: dayjs.Dayjs;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        });
        injector = getTestBed();
        service = injector.get(ParticipationService);
        httpMock = injector.get(HttpTestingController);
        currentDate = dayjs();

        participationDefault = new StudentParticipation();
    });

    it('should find an element', fakeAsync(() => {
        const returnedFromService = Object.assign(
            {
                initializationDate: currentDate.toDate(),
            },
            participationDefault,
        );
        service
            .find(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: participationDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find an element with latest result', fakeAsync(() => {
        const returnedFromService = { ...participationDefault, initializationDate: currentDate.toDate() };
        returnedFromService.results = [{ id: 1 }];
        service
            .findWithLatestResult(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: returnedFromService }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find participation for the exercise', fakeAsync(() => {
        const returnedFromService = { ...participationDefault, initializationDate: currentDate.toDate() };
        returnedFromService.id = 123;
        service
            .findParticipationForCurrentUser(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: returnedFromService }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find no participation for the exercise', fakeAsync(() => {
        service
            .findParticipationForCurrentUser(123)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toBeUndefined());
        httpMock.expectOne({ method: 'GET' });
        tick();
    }));

    it('should delete for guided tour', fakeAsync(() => {
        service.deleteForGuidedTour(123).subscribe((resp) => expect(resp.ok));
        let request = httpMock.expectOne({ method: 'DELETE' });
        expect(request.request.params.keys().length).toEqual(0);

        service.deleteForGuidedTour(123, { a: 'param' }).subscribe((resp) => expect(resp.ok));
        request = httpMock.expectOne({ method: 'DELETE' });
        expect(request.request.params.keys().length).toEqual(1);
        expect(request.request.params.get('a')).toEqual('param');
    }));

    it('should cleanup build plan', fakeAsync(() => {
        service.cleanupBuildPlan(participationDefault).subscribe((resp) => expect(resp).toMatchObject(participationDefault));
        httpMock.expectOne({ method: 'PUT' });
    }));

    it('should merge student participations', fakeAsync(() => {
        const participation1: ProgrammingExerciseStudentParticipation = {
            id: 1,
            type: ParticipationType.PROGRAMMING,
            repositoryUrl: 'repo-url',
            buildPlanId: 'build-plan-id',
            student: { id: 1, login: 'student1', guidedTourSettings: [] },
            team: { id: 1, name: 'team1' },
            results: [{ id: 3 }],
            submissions: [{ id: 1 }],
        };

        const participation2: ProgrammingExerciseStudentParticipation = {
            id: 2,
            type: ParticipationType.PROGRAMMING,
            repositoryUrl: 'repo-url-1',
            buildPlanId: 'build-plan-id-1',
            student: { id: 2, login: 'student2', guidedTourSettings: [] },
            results: [{ id: 1 }, { id: 2 }],
            submissions: [{ id: 2 }, { id: 3 }],
        };

        const mergedParticipation = service.mergeStudentParticipations([participation1, participation2]);
        expect(mergedParticipation?.team!.id!).toEqual(participation1.team!.id);
        expect(mergedParticipation?.team!.name!).toEqual(participation1.team!.name);
        expect(mergedParticipation?.id).toEqual(participation1.id);
        expect(mergedParticipation?.results).toEqual([...participation1.results!, ...participation2.results!]);
        expect(mergedParticipation?.submissions).toEqual([...participation1.submissions!, ...participation2.submissions!]);
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        mergedParticipation?.results?.forEach((result) => expect(result.participation).toMatchObject(mergedParticipation));
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        mergedParticipation?.submissions?.forEach((submission) => expect(submission.participation).toMatchObject(mergedParticipation));
    }));

    it('should merge student participations', fakeAsync(() => {
        const participation1: StudentParticipation = {
            id: 1,
            type: ParticipationType.STUDENT,
            student: { id: 1, login: 'student1', guidedTourSettings: [] },
            results: [{ id: 3 }],
            submissions: [{ id: 1 }],
        };

        const participation2: StudentParticipation = {
            id: 2,
            type: ParticipationType.STUDENT,
            student: { id: 2, login: 'student2', guidedTourSettings: [] },
            results: [{ id: 1 }, { id: 2 }],
            submissions: [{ id: 2 }, { id: 3 }],
        };

        const mergedParticipation = service.mergeStudentParticipations([participation1, participation2]);
        expect(mergedParticipation?.id).toEqual(participation1.id);
        expect(mergedParticipation?.results).toEqual([...participation1.results!, ...participation2.results!]);
        expect(mergedParticipation?.submissions).toEqual([...participation1.submissions!, ...participation2.submissions!]);
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        mergedParticipation?.results?.forEach((result) => expect(result.participation).toMatchObject(mergedParticipation));
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        mergedParticipation?.submissions?.forEach((submission) => expect(submission.participation).toMatchObject(mergedParticipation));
    }));

    it('should merge no participations', fakeAsync(() => {
        const participation: StudentParticipation = {
            id: 1,
            type: ParticipationType.SOLUTION,
            student: { id: 1, login: 'student1', guidedTourSettings: [] },
            results: [{ id: 3 }],
            submissions: [{ id: 1 }],
        };

        let mergedParticipation = service.mergeStudentParticipations([participation]);
        expect(mergedParticipation).toBeUndefined();

        participation.type = ParticipationType.STUDENT;
        mergedParticipation = service.mergeStudentParticipations([participation]);
        expect(mergedParticipation?.id).toEqual(participation.id);
    }));

    it('should update a Participation', fakeAsync(() => {
        const exercise = new TextExercise(new Course(), undefined);
        exercise.id = 1;
        const returnedFromService = Object.assign(
            {
                repositoryUrl: 'BBBBBB',
                buildPlanId: 'BBBBBB',
                initializationState: 'BBBBBB',
                initializationDate: currentDate,
                presentationScore: 1,
                exercise,
            },
            participationDefault,
        );

        const expected = Object.assign({}, returnedFromService);

        service
            .update(exercise, expected)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should return a list of Participation', fakeAsync(() => {
        const returnedFromService = Object.assign(
            {
                repositoryUrl: 'BBBBBB',
                buildPlanId: 'BBBBBB',
                initializationState: 'BBBBBB',
                initializationDate: currentDate,
                presentationScore: 1,
                results: [],
                submissions: [],
            },
            participationDefault,
        );
        const expected = Object.assign({}, returnedFromService);
        service
            .findAllParticipationsByExercise(1)
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toContainEqual(expected));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([returnedFromService]);
        tick();
    }));

    it('should delete a Participation', fakeAsync(() => {
        service.delete(123).subscribe((resp) => expect(resp.ok));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();
    }));

    it.each<any>([
        ['attachment; filename="FixArtifactDownload-Tests-1.0.jar"', 'FixArtifactDownload-Tests-1.0.jar'],
        ['', 'artifact'],
        ['filename="FixArtifactDownload-Tests-1.0.jar"', 'FixArtifactDownload-Tests-1.0.jar'],
        ['f="abc"', 'artifact'],
    ])('%# should download artifact and extract file name: %p', async (headerVal: string, expectedFileName: string, done: jest.DoneCallback) => {
        const expectedBlob = new Blob(['abc', 'cfe'], { type: 'application/java-archive' });
        const headers = new HttpHeaders({ 'content-disposition': headerVal, 'content-type': 'application/java-archive' });
        const response = { body: expectedBlob, headers, status: 200 };

        service.downloadArtifact(123).subscribe((resp) => {
            expect(resp.fileName).toBe(expectedFileName);
            expect(resp.fileContent).toBe(expectedBlob);
            done();
        });

        const req = httpMock.expectOne({ method: 'GET' });
        req.event(new HttpResponse<Blob>(response));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
