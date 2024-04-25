import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { map, take } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
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
import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

describe('Participation Service', () => {
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
        service = TestBed.inject(ParticipationService);
        httpMock = TestBed.inject(HttpTestingController);
        currentDate = dayjs();

        participationDefault = { type: 'student' } as unknown as StudentParticipation;
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
        service.deleteForGuidedTour(123).subscribe((resp) => expect(resp.ok).toBeTrue());
        let request = httpMock.expectOne({ method: 'DELETE' });
        expect(request.request.params.keys()).toHaveLength(0);

        service.deleteForGuidedTour(123, { a: 'param' }).subscribe((resp) => expect(resp.ok).toBeTrue());
        request = httpMock.expectOne({ method: 'DELETE' });
        expect(request.request.params.keys()).toHaveLength(1);
        expect(request.request.params.get('a')).toBe('param');
    }));

    it('should cleanup build plan', fakeAsync(() => {
        service.cleanupBuildPlan(participationDefault).subscribe((resp) => expect(resp).toMatchObject(participationDefault));
        httpMock.expectOne({ method: 'PUT' });
    }));

    it('should merge student participations for programming exercises', fakeAsync(() => {
        const participation1: ProgrammingExerciseStudentParticipation = {
            id: 1,
            type: ParticipationType.PROGRAMMING,
            repositoryUri: 'repo-url',
            buildPlanId: 'build-plan-id',
            student: { id: 1, login: 'student1', guidedTourSettings: [], internal: true },
            team: { id: 1, name: 'team1' },
            results: [{ id: 3 }],
            submissions: [{ id: 1 }],
        };

        const participation2: ProgrammingExerciseStudentParticipation = {
            id: 2,
            type: ParticipationType.PROGRAMMING,
            repositoryUri: 'repo-url-1',
            buildPlanId: 'build-plan-id-1',
            student: { id: 2, login: 'student2', guidedTourSettings: [], internal: true },
            results: [{ id: 1 }, { id: 2 }],
            submissions: [{ id: 2 }, { id: 3 }],
        };

        const mergedParticipation = service.mergeStudentParticipations([participation1, participation2])[0];
        expect(mergedParticipation?.team!.id).toEqual(participation1.team!.id);
        expect(mergedParticipation?.team!.name).toEqual(participation1.team!.name);
        expect(mergedParticipation?.id).toEqual(participation1.id);
        expect(mergedParticipation?.results).toEqual([...participation1.results!, ...participation2.results!]);
        expect(mergedParticipation?.submissions).toEqual([...participation1.submissions!, ...participation2.submissions!]);
        mergedParticipation?.results?.forEach((result) => expect(result.participation).toMatchObject(mergedParticipation));
        mergedParticipation?.submissions?.forEach((submission) => expect(submission.participation).toMatchObject(mergedParticipation));
    }));

    it('should not merge practice participation for programming exercises', fakeAsync(() => {
        const participation1: ProgrammingExerciseStudentParticipation = {
            id: 1,
            type: ParticipationType.PROGRAMMING,
            repositoryUri: 'repo-url',
            buildPlanId: 'build-plan-id',
            student: { id: 1, login: 'student1', guidedTourSettings: [], internal: true },
            results: [{ id: 3 }],
            submissions: [{ id: 1 }],
            testRun: true,
        };

        const participation2: ProgrammingExerciseStudentParticipation = {
            id: 2,
            type: ParticipationType.PROGRAMMING,
            repositoryUri: 'repo-url-1',
            buildPlanId: 'build-plan-id-1',
            student: { id: 2, login: 'student2', guidedTourSettings: [], internal: true },
            results: [{ id: 1 }, { id: 2 }],
            submissions: [{ id: 2 }, { id: 3 }],
        };

        const mergedParticipations = service.mergeStudentParticipations([participation1, participation2]);
        expect(mergedParticipations).toHaveLength(2);
        expect(mergedParticipations[0]).toEqual(participation2);
        expect(mergedParticipations[1]).toEqual(participation1);
    }));

    it('should merge student participations', fakeAsync(() => {
        const participation1: StudentParticipation = {
            id: 1,
            type: ParticipationType.STUDENT,
            student: { id: 1, login: 'student1', guidedTourSettings: [], internal: true },
            results: [{ id: 3 }],
            submissions: [{ id: 1 }],
        };

        const participation2: StudentParticipation = {
            id: 2,
            type: ParticipationType.STUDENT,
            student: { id: 2, login: 'student2', guidedTourSettings: [], internal: true },
            results: [{ id: 1 }, { id: 2 }],
            submissions: [{ id: 2 }, { id: 3 }],
        };

        const mergedParticipation = service.mergeStudentParticipations([participation1, participation2])[0];
        expect(mergedParticipation?.id).toEqual(participation1.id);
        expect(mergedParticipation?.results).toEqual([...participation1.results!, ...participation2.results!]);
        expect(mergedParticipation?.submissions).toEqual([...participation1.submissions!, ...participation2.submissions!]);
        mergedParticipation?.results?.forEach((result) => expect(result.participation).toMatchObject(mergedParticipation));
        mergedParticipation?.submissions?.forEach((submission) => expect(submission.participation).toMatchObject(mergedParticipation));
    }));

    it('should update a Participation', fakeAsync(() => {
        const exercise = new ProgrammingExercise(new Course(), undefined);
        exercise.id = 1;
        exercise.categories = undefined;
        exercise.exampleSolutionPublicationDate = undefined;

        const returnedFromService = {
            ...participationDefault,
            repositoryUri: 'BBBBBB',
            buildPlanId: 'BBBBBB',
            initializationState: 'BBBBBB',
            initializationDate: currentDate,
            presentationScore: 1,
            exercise,
            // the update service will make the participation results and submissions
            // empty arrays instead of undefined, so we need to adapt our expected
            // values accordingly
            results: [],
            submissions: [],
        };

        const expected = Object.assign({}, returnedFromService) as StudentParticipation;

        service
            .update(exercise, expected)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toMatchObject({ ...expected }));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should return a list of Participation', fakeAsync(() => {
        const returnedFromService = Object.assign(
            {
                repositoryUri: 'BBBBBB',
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
        service.delete(123).subscribe((resp) => expect(resp.ok).toBeTrue());

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();
    }));

    it('should get logs availability for participation results', fakeAsync(() => {
        let resultGetLogsAvailability: any;
        const logsAvailability: { [key: string]: boolean } = { '1': true, '2': false };
        const returnedFromService = logsAvailability;
        const expected = { ...returnedFromService };

        service
            .getLogsAvailabilityForResultsOfParticipation(1)
            .pipe(take(1))
            .subscribe((resp) => (resultGetLogsAvailability = resp));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(resultGetLogsAvailability).toEqual(expected);
    }));

    it.each<any>([
        ['attachment; filename="FixArtifactDownload-Tests-1.0.jar"', 'FixArtifactDownload-Tests-1.0.jar'],
        ['', 'artifact'],
        ['filename="FixArtifactDownload-Tests-1.0.jar"', 'FixArtifactDownload-Tests-1.0.jar'],
        ['f="abc"', 'artifact'],
    ])('%# should download artifact and extract file name: %p', async (headerVal: string, expectedFileName: string) => {
        const expectedBlob = new Blob(['abc', 'cfe'], { type: 'application/java-archive' });
        const headers = new HttpHeaders({ 'content-disposition': headerVal, 'content-type': 'application/java-archive' });
        const response = { body: expectedBlob, headers, status: 200 };

        service.downloadArtifact(123).subscribe((resp) => {
            expect(resp.fileName).toBe(expectedFileName);
            expect(resp.fileContent).toBe(expectedBlob);
        });

        const req = httpMock.expectOne({ method: 'GET' });
        req.event(new HttpResponse<Blob>(response));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
