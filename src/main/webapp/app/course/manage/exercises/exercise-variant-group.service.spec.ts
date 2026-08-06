import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import {
    CreateExerciseVariantGroupDTO,
    ExerciseVariantGroupDTO,
    ExerciseVariantGroupService,
    PersistableGroup,
    isPersistableGroup,
    toCourseExerciseGroup,
    toCreateGroupPayload,
    toUpdateGroupPayload,
} from 'app/course/manage/exercises/exercise-variant-group.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('ExerciseVariantGroupService', () => {
    let service: ExerciseVariantGroupService;
    let httpMock: HttpTestingController;

    const courseId = 42;
    const baseUrl = `api/exercise/courses/${courseId}/exercise-variant-groups`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(ExerciseVariantGroupService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should GET the groups of a course and convert dates', () => {
        const serverGroups = [{ id: 1, title: 'Loop variants', maxPoints: 10, dueDate: dayjs().toJSON(), exerciseIds: [5] }];
        let received: ExerciseVariantGroupDTO[] | undefined;

        service.getGroupsForCourse(courseId).subscribe((groups) => (received = groups));

        const req = httpMock.expectOne(baseUrl);
        expect(req.request.method).toBe('GET');
        req.flush(serverGroups);

        expect(received).toHaveLength(1);
        expect(dayjs.isDayjs(received![0].dueDate)).toBe(true);
        expect(received![0].exerciseIds).toEqual([5]);
    });

    it('should POST a new group with client-converted dates', () => {
        const payload: CreateExerciseVariantGroupDTO = { title: 'Loop variants', maxPoints: 10, dueDate: dayjs('2026-06-15T10:00:00Z') };
        let created: ExerciseVariantGroupDTO | undefined;

        service.createGroup(courseId, payload).subscribe((group) => (created = group));

        const req = httpMock.expectOne(baseUrl);
        expect(req.request.method).toBe('POST');
        expect(req.request.body.title).toBe('Loop variants');
        // dayjs dates are serialized to ISO strings before sending.
        expect(typeof req.request.body.dueDate).toBe('string');
        req.flush({ id: 7, title: 'Loop variants', maxPoints: 10 });

        expect(created!.id).toBe(7);
    });

    it('should PUT an updated group to the id-scoped URL', () => {
        const group: ExerciseVariantGroupDTO = { id: 7, title: 'Renamed', maxPoints: 5 };

        service.updateGroup(courseId, group).subscribe();

        const req = httpMock.expectOne(`${baseUrl}/7`);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body.title).toBe('Renamed');
        req.flush(group);
    });

    it('should DELETE a group by id', () => {
        service.deleteGroup(courseId, 7).subscribe();

        const req = httpMock.expectOne(`${baseUrl}/7`);
        expect(req.request.method).toBe('DELETE');
        req.flush(null);
    });

    it('should PUT an exercise assignment with the target group id', () => {
        service.setExerciseVariantGroup(courseId, 5, 7).subscribe();

        const req = httpMock.expectOne(`api/exercise/courses/${courseId}/exercises/5/variant-group`);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ groupId: 7 });
        req.flush(null);
    });

    it('should PUT an unassignment with an undefined group id', () => {
        service.setExerciseVariantGroup(courseId, 5).subscribe();

        const req = httpMock.expectOne(`api/exercise/courses/${courseId}/exercises/5/variant-group`);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body.groupId).toBeUndefined();
        req.flush(null);
    });

    it('should convert all timeline fields from their server representation', () => {
        const iso = '2026-06-15T10:00:00Z';
        const serverGroup = {
            id: 1,
            title: 'G',
            releaseDate: iso,
            startDate: iso,
            dueDate: iso,
            assessmentDueDate: iso,
            exampleSolutionPublicationDate: iso,
        };
        let received: ExerciseVariantGroupDTO | undefined;

        service.getGroupsForCourse(courseId).subscribe((groups) => (received = groups[0]));
        httpMock.expectOne(baseUrl).flush([serverGroup]);

        for (const field of ['releaseDate', 'startDate', 'dueDate', 'assessmentDueDate', 'exampleSolutionPublicationDate'] as const) {
            expect(dayjs.isDayjs(received![field])).toBe(true);
        }
    });

    describe('payload mappers', () => {
        const group: PersistableGroup = {
            id: 7,
            title: 'Loop variants',
            maxPoints: 10,
            releaseDate: dayjs('2026-01-01T00:00:00Z'),
            dueDate: dayjs('2026-02-02T00:00:00Z'),
            exercises: [{ id: 1, type: ExerciseType.TEXT } as Exercise],
        };

        it('recognises only a group with a non-empty title as persistable', () => {
            expect(isPersistableGroup(group)).toBe(true);
            expect(isPersistableGroup({ id: 7 })).toBe(false);
            expect(isPersistableGroup({ id: 7, title: '   ' })).toBe(false);
        });

        it('maps the edit dialog view model to the create payload (without id and members)', () => {
            const payload = toCreateGroupPayload(group);
            expect(payload).toEqual({
                title: 'Loop variants',
                maxPoints: 10,
                releaseDate: group.releaseDate,
                startDate: undefined,
                dueDate: group.dueDate,
                assessmentDueDate: undefined,
                exampleSolutionPublicationDate: undefined,
            });
        });

        it('maps the edit dialog view model to the update payload including the id', () => {
            const payload = toUpdateGroupPayload(group, 7);
            expect(payload.id).toBe(7);
            expect(payload.title).toBe('Loop variants');
        });

        it('maps a server DTO to the client group and resolves members by id', () => {
            const one = { id: 1, type: ExerciseType.TEXT } as Exercise;
            const dto: ExerciseVariantGroupDTO = { id: 7, title: 'G', maxPoints: 5, dueDate: dayjs('2026-02-02T00:00:00Z'), exerciseIds: [1, 999] };

            const mapped = toCourseExerciseGroup(dto, new Map([[1, one]]));

            expect(mapped.id).toBe(7);
            expect(mapped.maxPoints).toBe(5);
            expect(mapped.dueDate).toBe(dto.dueDate);
            // Unknown ids (999) are dropped instead of producing holes.
            expect(mapped.exercises).toEqual([one]);
        });
    });
});
