import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { map, take } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

describe('Participation Service', () => {
    let service: ParticipationService;
    let httpMock: HttpTestingController;
    let participationDefault: Participation;
    let currentDate: dayjs.Dayjs;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: Router, useClass: MockRouter },
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
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
            student: { id: 1, login: 'student1', internal: true },
            team: { id: 1, name: 'team1' },

            submissions: [{ id: 1, results: [{ id: 3 }] }],
        };

        const participation2: ProgrammingExerciseStudentParticipation = {
            id: 2,
            type: ParticipationType.PROGRAMMING,
            repositoryUri: 'repo-url-1',
            buildPlanId: 'build-plan-id-1',
            student: { id: 2, login: 'student2', internal: true },

            submissions: [
                { id: 2, results: [{ id: 1 }] },
                { id: 3, results: [{ id: 2 }] },
            ],
        };

        const mergedParticipation = service.mergeStudentParticipations([participation1, participation2])[0];
        expect(mergedParticipation?.team!.id).toEqual(participation1.team!.id);
        expect(mergedParticipation?.team!.name).toEqual(participation1.team!.name);
        expect(mergedParticipation?.id).toEqual(participation1.id);
        expect(mergedParticipation?.submissions).toEqual([...participation1.submissions!, ...participation2.submissions!]);
        mergedParticipation?.submissions?.forEach((submission) => expect(submission.participation).toMatchObject(mergedParticipation));
    }));

    it('should not merge practice participation for programming exercises', fakeAsync(() => {
        const participation1: ProgrammingExerciseStudentParticipation = {
            id: 1,
            type: ParticipationType.PROGRAMMING,
            repositoryUri: 'repo-url',
            buildPlanId: 'build-plan-id',
            student: { id: 1, login: 'student1', internal: true },

            submissions: [{ id: 1, results: [{ id: 3 }] }],
            testRun: true,
        };

        const participation2: ProgrammingExerciseStudentParticipation = {
            id: 2,
            type: ParticipationType.PROGRAMMING,
            repositoryUri: 'repo-url-1',
            buildPlanId: 'build-plan-id-1',
            student: { id: 2, login: 'student2', internal: true },
            submissions: [
                { id: 2, results: [{ id: 1 }] },
                { id: 3, results: [{ id: 2 }] },
            ],
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
            student: { id: 1, login: 'student1', internal: true },
            submissions: [{ id: 1, results: [{ id: 3 }] }],
        };

        const participation2: StudentParticipation = {
            id: 2,
            type: ParticipationType.STUDENT,
            student: { id: 2, login: 'student2', internal: true },
            submissions: [
                { id: 2, results: [{ id: 1 }] },
                { id: 3, results: [{ id: 2 }] },
            ],
        };

        const mergedParticipation = service.mergeStudentParticipations([participation1, participation2])[0];
        expect(mergedParticipation?.id).toEqual(participation1.id);
        expect(mergedParticipation?.submissions).toEqual([...participation1.submissions!, ...participation2.submissions!]);
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

    it('should get build job ids for participation results', fakeAsync(() => {
        let resultGetBuildJobId: any;
        const resultIdToBuildJobIdMap: { [key: string]: boolean } = { '1': true, '2': false };
        const returnedFromService = resultIdToBuildJobIdMap;
        const expected = { ...returnedFromService };

        service
            .getBuildJobIdsForResultsOfParticipation(1)
            .pipe(take(1))
            .subscribe((resp) => (resultGetBuildJobId = resp));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(resultGetBuildJobId).toEqual(expected);
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
