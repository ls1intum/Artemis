import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { take } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

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

    it('should find an element', () => {
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
    });

    it('should cleanup build plan', () => {
        const returnedFromService = Object.assign({}, participationDefault, { initializationDate: currentDate.toDate() });
        let result: StudentParticipation | undefined;
        service
            .cleanupBuildPlan(participationDefault)
            .pipe(take(1))
            .subscribe((resp) => (result = resp.body ?? undefined));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);

        expect(dayjs.isDayjs(result?.initializationDate)).toBe(true);
    });

    it('should start quiz participation', () => {
        const returnedFromService = Object.assign({ initializationDate: currentDate.toDate() }, participationDefault);
        let result: StudentParticipation | undefined;

        service
            .startQuizParticipation(42)
            .pipe(take(1))
            .subscribe((resp) => (result = resp.body ?? undefined));

        const req = httpMock.expectOne('api/quiz/quiz-exercises/42/start-participation');
        expect(req.request.method).toBe('POST');
        req.flush(returnedFromService);

        expect(dayjs.isDayjs(result?.initializationDate)).toBe(true);
    });

    describe('getQuizParticipationResult', () => {
        it('should GET quiz participation result without submissionId param', () => {
            service.getQuizParticipationResult(1, 2).subscribe();

            const req = httpMock.expectOne((r) => r.url === 'api/quiz/quiz-exercises/1/participations/2/result');
            expect(req.request.method).toBe('GET');
            expect(req.request.params.has('submissionId')).toBe(false);
            req.flush(participationDefault);
        });

        it('should GET quiz participation result with submissionId param', () => {
            service.getQuizParticipationResult(1, 2, 99).subscribe();

            const req = httpMock.expectOne((r) => r.url === 'api/quiz/quiz-exercises/1/participations/2/result');
            expect(req.request.params.get('submissionId')).toBe('99');
            req.flush(participationDefault);
        });
    });

    describe('shouldPreferPractice', () => {
        it('should return true when exercise due date is in the past', () => {
            const exercise: Exercise = { dueDate: dayjs().subtract(1, 'day') } as unknown as Exercise;
            expect(service.shouldPreferPractice(exercise)).toBe(true);
        });

        it('should return false when exercise due date is in the future', () => {
            const exercise: Exercise = { dueDate: dayjs().add(1, 'day') } as unknown as Exercise;
            expect(service.shouldPreferPractice(exercise)).toBe(false);
        });

        it('should return false when exercise has no due date', () => {
            const exercise: Exercise = { dueDate: undefined } as unknown as Exercise;
            expect(service.shouldPreferPractice(exercise)).toBe(false);
        });

        it('should return false when exercise is undefined', () => {
            expect(service.shouldPreferPractice(undefined)).toBe(false);
        });
    });

    describe('getSpecificStudentParticipation', () => {
        it('should return the testRun participation when testRun is true', () => {
            const nonTestRun: StudentParticipation = { id: 1, testRun: false };
            const testRun: StudentParticipation = { id: 2, testRun: true };

            const result = service.getSpecificStudentParticipation([nonTestRun, testRun], true);

            expect(result?.id).toBe(2);
        });

        it('should return the non-testRun participation when testRun is false', () => {
            const nonTestRun: StudentParticipation = { id: 1, testRun: false };
            const testRun: StudentParticipation = { id: 2, testRun: true };

            const result = service.getSpecificStudentParticipation([nonTestRun, testRun], false);

            expect(result?.id).toBe(1);
        });

        it('should return undefined when no participation matches', () => {
            const testRun: StudentParticipation = { id: 2, testRun: true };

            const result = service.getSpecificStudentParticipation([testRun], false);

            expect(result).toBeUndefined();
        });
    });

    it('should merge student participations for programming exercises', () => {
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
    });

    it('should not merge practice participation for programming exercises', () => {
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
    });

    it('should merge student participations', () => {
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
    });

    it('should merge student participations with a testRun group for student type', () => {
        const participation1: StudentParticipation = {
            id: 1,
            type: ParticipationType.STUDENT,
            student: { id: 1, login: 'student1', internal: true },
            testRun: false,
        };
        const participation2: StudentParticipation = {
            id: 2,
            type: ParticipationType.STUDENT,
            student: { id: 2, login: 'student2', internal: true },
            testRun: true,
        };

        const mergedParticipations = service.mergeStudentParticipations([participation1, participation2]);

        expect(mergedParticipations).toHaveLength(2);
        expect(mergedParticipations[0].id).toBe(participation1.id);
        expect(mergedParticipations[1].id).toBe(participation2.id);
    });

    it('should carry over participantIdentifier and participantName when merging participations', () => {
        const participation1: StudentParticipation = {
            id: 1,
            type: ParticipationType.STUDENT,
            participantIdentifier: 'identifier1',
            participantName: 'Participant One',
        };

        const mergedParticipation = service.mergeStudentParticipations([participation1])[0];

        expect(mergedParticipation.participantIdentifier).toBe('identifier1');
        expect(mergedParticipation.participantName).toBe('Participant One');
    });

    it('should convert an array of participation dates from server', () => {
        const isoDate = '2024-06-01T10:00:00Z';
        const participations: StudentParticipation[] = [
            { id: 1, initializationDate: dayjs(isoDate) as any },
            { id: 2, initializationDate: dayjs(isoDate) as any },
        ];

        const result = ParticipationService.convertParticipationArrayDatesFromServer(participations);

        expect(result).toHaveLength(2);
        result.forEach((participation) => expect(dayjs.isDayjs(participation.initializationDate)).toBe(true));
    });

    it('should return an empty array when converting undefined participations', () => {
        const result = ParticipationService.convertParticipationArrayDatesFromServer(undefined);
        expect(result).toEqual([]);
    });

    it('should update a Participation', () => {
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
    });

    it('should delete a Participation', () => {
        service.delete(123).subscribe((resp) => expect(resp.ok).toBe(true));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
    });

    it('should get build job ids for participation results', () => {
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

        expect(resultGetBuildJobId).toEqual(expected);
    });

    describe('searchParticipations', () => {
        const baseSearch = {
            page: 0,
            pageSize: 50,
            sortingOrder: SortingOrder.ASCENDING,
            sortedColumn: 'participationId',
            searchTerm: '',
        };

        it('should GET paginated participations and convert date fields', () => {
            const isoDate = '2024-06-01T10:00:00Z';
            const serverDto = { participationId: 1, initializationDate: isoDate, individualDueDate: isoDate };

            let result: any;
            service.searchParticipations(42, baseSearch).subscribe((r) => (result = r));

            const req = httpMock.expectOne((r) => r.url === 'api/exercise/exercises/42/participations/page');
            expect(req.request.method).toBe('GET');
            req.flush([serverDto], { headers: { 'X-Total-Count': '1' } });

            expect(result.totalElements).toBe(1);
            expect(dayjs.isDayjs(result.content[0].initializationDate)).toBe(true);
            expect(dayjs.isDayjs(result.content[0].individualDueDate)).toBe(true);
        });

        it('should include filterProp in params when provided', () => {
            service.searchParticipations(1, { ...baseSearch, filterProp: 'Failed' }).subscribe();

            const req = httpMock.expectOne((r) => r.url === 'api/exercise/exercises/1/participations/page');
            expect(req.request.params.get('filterProp')).toBe('Failed');
            req.flush([], { headers: { 'X-Total-Count': '0' } });
        });

        it('should not include filterProp in params when absent', () => {
            service.searchParticipations(1, baseSearch).subscribe();

            const req = httpMock.expectOne((r) => r.url === 'api/exercise/exercises/1/participations/page');
            expect(req.request.params.has('filterProp')).toBe(false);
            req.flush([], { headers: { 'X-Total-Count': '0' } });
        });
    });

    describe('searchParticipationScores', () => {
        const baseSearch = {
            page: 0,
            pageSize: 50,
            sortingOrder: SortingOrder.ASCENDING,
            sortedColumn: 'score',
            searchTerm: '',
        };

        it('should GET paginated scores and read X-Total-Count header', () => {
            const serverDto = { participationId: 1, score: 80, participantName: 'Alice', participantIdentifier: 'alice', successful: false, testRun: false };

            let result: any;
            service.searchParticipationScores(7, baseSearch).subscribe((r) => (result = r));

            const req = httpMock.expectOne((r) => r.url === 'api/exercise/exercises/7/participations/scores');
            expect(req.request.method).toBe('GET');
            req.flush([serverDto], { headers: { 'X-Total-Count': '5' } });

            expect(result.totalElements).toBe(5);
            expect(result.content[0].score).toBe(80);
        });

        it('should include filterProp and scoreRange params when provided', () => {
            service.searchParticipationScores(1, { ...baseSearch, filterProp: 'Successful', scoreRangeLower: 60, scoreRangeUpper: 80 }).subscribe();

            const req = httpMock.expectOne((r) => r.url === 'api/exercise/exercises/1/participations/scores');
            expect(req.request.params.get('filterProp')).toBe('Successful');
            expect(req.request.params.get('scoreRangeLower')).toBe('60');
            expect(req.request.params.get('scoreRangeUpper')).toBe('80');
            req.flush([], { headers: { 'X-Total-Count': '0' } });
        });

        it('should not include scoreRange params when undefined', () => {
            service.searchParticipationScores(1, baseSearch).subscribe();

            const req = httpMock.expectOne((r) => r.url === 'api/exercise/exercises/1/participations/scores');
            expect(req.request.params.has('scoreRangeLower')).toBe(false);
            expect(req.request.params.has('scoreRangeUpper')).toBe(false);
            req.flush([], { headers: { 'X-Total-Count': '0' } });
        });
    });

    describe('getParticipationNamesForExport', () => {
        it('should GET participation names for export', () => {
            const exportDto = { participantName: 'Alice', participantIdentifier: 'alice' };

            let result: any;
            service.getParticipationNamesForExport(3).subscribe((r) => (result = r));

            const req = httpMock.expectOne('api/exercise/exercises/3/participations/names');
            expect(req.request.method).toBe('GET');
            req.flush([exportDto]);

            expect(result).toEqual([exportDto]);
        });
    });

    describe('updateIndividualDueDates', () => {
        it('should PUT individual due dates as DTOs', () => {
            const exercise: Exercise = {
                id: 5,
                type: ExerciseType.TEXT,
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
            };
            const participation: StudentParticipation = { id: 10, individualDueDate: dayjs('2024-12-31') };

            service.updateIndividualDueDates(exercise, [participation]).subscribe();

            const req = httpMock.expectOne('api/exercise/exercises/5/participations/update-individual-due-date');
            expect(req.request.method).toBe('PUT');
            expect(req.request.body[0].id).toBe(10);
            expect(req.request.body[0].exerciseId).toBe(5);
            req.flush([]);
        });

        it('should convert dates and set access rights for the returned participations', () => {
            const exercise: Exercise = {
                id: 5,
                type: ExerciseType.TEXT,
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
            };
            const participation: StudentParticipation = { id: 10, individualDueDate: dayjs('2024-12-31') };
            const returnedParticipation = {
                id: 10,
                initializationDate: currentDate.toDate(),
                individualDueDate: currentDate.toDate(),
                exercise,
            };

            let result: StudentParticipation[] | undefined;
            service
                .updateIndividualDueDates(exercise, [participation])
                .pipe(take(1))
                .subscribe((resp) => (result = resp.body ?? undefined));

            const req = httpMock.expectOne('api/exercise/exercises/5/participations/update-individual-due-date');
            req.flush([returnedParticipation]);

            expect(result).toHaveLength(1);
            expect(dayjs.isDayjs(result?.[0].initializationDate)).toBe(true);
            expect(dayjs.isDayjs(result?.[0].individualDueDate)).toBe(true);
            expect(result?.[0].exercise?.isAtLeastTutor).toBe(false);
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});
