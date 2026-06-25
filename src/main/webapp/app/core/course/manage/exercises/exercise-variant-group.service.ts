import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { convertDateFromClient, convertDateFromServer } from 'app/foundation/util/date.utils';
import { CourseExerciseGroup } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

/** Server representation of an exercise variant group (mirrors the backend {@code ExerciseVariantGroupDTO}). */
export interface ExerciseVariantGroupDTO {
    id?: number;
    title?: string;
    maxPoints?: number;
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    assessmentDueDate?: dayjs.Dayjs;
    exampleSolutionPublicationDate?: dayjs.Dayjs;
    buildAndTestStudentSubmissionsAfterDueDate?: dayjs.Dayjs;
    exerciseIds?: number[];
}

/** Payload for creating a variant group (the owning course comes from the request path). */
export interface CreateExerciseVariantGroupDTO {
    title: string;
    maxPoints?: number;
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    assessmentDueDate?: dayjs.Dayjs;
    exampleSolutionPublicationDate?: dayjs.Dayjs;
    buildAndTestStudentSubmissionsAfterDueDate?: dayjs.Dayjs;
}

/**
 * Talks to the real {@code ExerciseVariantGroupResource} endpoints. Used by the exercise management view when the
 * nav-bar mock-data toggle is off; the mock interceptor only rewrites requests while mock mode is enabled.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseVariantGroupService {
    private http = inject(HttpClient);

    private resourceUrl(courseId: number): string {
        return `api/exercise/courses/${courseId}/exercise-variant-groups`;
    }

    getGroupsForCourse(courseId: number): Observable<ExerciseVariantGroupDTO[]> {
        return this.http.get<ExerciseVariantGroupDTO[]>(this.resourceUrl(courseId)).pipe(map((groups) => groups.map((group) => this.convertDatesFromServer(group))));
    }

    createGroup(courseId: number, group: CreateExerciseVariantGroupDTO): Observable<ExerciseVariantGroupDTO> {
        return this.http.post<ExerciseVariantGroupDTO>(this.resourceUrl(courseId), this.convertDatesToClient(group)).pipe(map((created) => this.convertDatesFromServer(created)));
    }

    updateGroup(courseId: number, group: ExerciseVariantGroupDTO): Observable<ExerciseVariantGroupDTO> {
        return this.http
            .put<ExerciseVariantGroupDTO>(`${this.resourceUrl(courseId)}/${group.id}`, this.convertDatesToClient(group))
            .pipe(map((updated) => this.convertDatesFromServer(updated)));
    }

    deleteGroup(courseId: number, groupId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl(courseId)}/${groupId}`);
    }

    /** Assigns the exercise to the given group, or removes it from its current group when {@code groupId} is undefined. */
    setExerciseVariantGroup(courseId: number, exerciseId: number, groupId?: number): Observable<void> {
        return this.http.put<void>(`api/exercise/courses/${courseId}/exercises/${exerciseId}/variant-group`, { groupId: groupId ?? undefined });
    }

    private convertDatesFromServer<T extends ExerciseVariantGroupDTO>(group: T): T {
        group.releaseDate = convertDateFromServer(group.releaseDate);
        group.startDate = convertDateFromServer(group.startDate);
        group.dueDate = convertDateFromServer(group.dueDate);
        group.assessmentDueDate = convertDateFromServer(group.assessmentDueDate);
        group.exampleSolutionPublicationDate = convertDateFromServer(group.exampleSolutionPublicationDate);
        group.buildAndTestStudentSubmissionsAfterDueDate = convertDateFromServer(group.buildAndTestStudentSubmissionsAfterDueDate);
        return group;
    }

    private convertDatesToClient<
        T extends {
            releaseDate?: dayjs.Dayjs;
            startDate?: dayjs.Dayjs;
            dueDate?: dayjs.Dayjs;
            assessmentDueDate?: dayjs.Dayjs;
            exampleSolutionPublicationDate?: dayjs.Dayjs;
            buildAndTestStudentSubmissionsAfterDueDate?: dayjs.Dayjs;
        },
    >(group: T): T {
        return {
            ...group,
            releaseDate: convertDateFromClient(group.releaseDate),
            startDate: convertDateFromClient(group.startDate),
            dueDate: convertDateFromClient(group.dueDate),
            assessmentDueDate: convertDateFromClient(group.assessmentDueDate),
            exampleSolutionPublicationDate: convertDateFromClient(group.exampleSolutionPublicationDate),
            buildAndTestStudentSubmissionsAfterDueDate: convertDateFromClient(group.buildAndTestStudentSubmissionsAfterDueDate),
        } as unknown as T;
    }
}

/**
 * Maps a server DTO to the client {@link CourseExerciseGroup} model used by the management view. Members are resolved
 * from {@code exercisesById} since the DTO only carries exercise ids. The mock-only {@code order} field stays
 * undefined.
 */
export function toCourseExerciseGroup(dto: ExerciseVariantGroupDTO, exercisesById: Map<number, Exercise>): CourseExerciseGroup {
    return {
        id: dto.id,
        title: dto.title,
        maxPoints: dto.maxPoints,
        releaseDate: dto.releaseDate,
        startDate: dto.startDate,
        dueDate: dto.dueDate,
        assessmentDueDate: dto.assessmentDueDate,
        exampleSolutionPublicationDate: dto.exampleSolutionPublicationDate,
        buildAndTestStudentSubmissionsAfterDueDate: dto.buildAndTestStudentSubmissionsAfterDueDate,
        exercises: (dto.exerciseIds ?? []).map((id) => exercisesById.get(id)).filter((exercise): exercise is Exercise => exercise !== undefined),
    };
}
