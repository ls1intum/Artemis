import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin } from 'rxjs';
import { map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { convertDateFromClient, convertDateFromServer } from 'app/foundation/util/date.utils';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';

/** Server representation of an exercise variant group (mirrors the backend {@code ExerciseVariantGroupDTO}). */
export interface ExerciseVariantGroupDTO {
    id?: number;
    title?: string;
    /** `'variant'` or `'milestone'`, mirroring the server's `discriminator`. */
    type?: 'variant' | 'milestone';
    /** Only set when {@link type} is `'milestone'`. */
    milestoneExerciseId?: number;
    maxPoints?: number;
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    assessmentDueDate?: dayjs.Dayjs;
    exampleSolutionPublicationDate?: dayjs.Dayjs;
    exerciseIds?: number[];
}

/** Lightweight preview payload for a group member (mirrors the backend {@code ExerciseProblemStatementDTO}). */
export interface ExerciseProblemStatementDTO {
    exerciseId: number;
    problemStatement?: string;
}

/**
 * Whether the requesting student has started a milestone group's anchor milestone exercise (mirrors the backend
 * {@code MilestoneStatusDTO}). The milestone exercise itself is never shown to students, so this is the only way the
 * group view can tell whether to offer "Start exercise" for it — and the only way it can reach the milestone's problem
 * statement, which doubles as the group's description.
 */
export interface MilestoneStatusDTO {
    milestoneExerciseId: number;
    started: boolean;
    /** Set only when {@link started} is `true`. */
    participationId?: number;
    /** The (shared) repository URI of that participation. Set only when {@link started} is `true`. */
    repositoryUri?: string;
    /** The milestone's problem statement, shown as the group's description. Unset when the instructor left it empty. */
    problemStatement?: string;
}

/** The date fields a group payload carries, as the client holds them. */
interface GroupDateFields {
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    assessmentDueDate?: dayjs.Dayjs;
    exampleSolutionPublicationDate?: dayjs.Dayjs;
}

/** The same payload with its dates serialised to the ISO strings the server expects on the wire. */
type WithSerialisedDates<T> = Omit<T, keyof GroupDateFields> & { [K in keyof GroupDateFields]?: string };

/** Payload for creating a variant group (the owning course comes from the request path). */
export interface CreateExerciseVariantGroupDTO {
    title: string;
    maxPoints?: number;
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    assessmentDueDate?: dayjs.Dayjs;
    exampleSolutionPublicationDate?: dayjs.Dayjs;
}

/**
 * Talks to the {@code ExerciseVariantGroupResource} endpoints. Used by the exercise management view to load and
 * persist course-level variant groups.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseVariantGroupService {
    private http = inject(HttpClient);

    private resourceUrl(courseId: number): string {
        return `api/exercise/courses/${courseId}/exercise-variant-groups`;
    }

    private milestoneResourceUrl(courseId: number): string {
        return `api/exercise/courses/${courseId}/milestone-exercise-groups`;
    }

    /**
     * The base URL a group is addressed under. The two types are served by separate resources: a milestone group carries
     * a whole programming exercise as its anchor, so it is created, read, updated and deleted through its own routes
     * (see `MilestoneExerciseGroupResource`), and the variant-group routes do not resolve one.
     */
    private urlForGroupType(courseId: number, type?: 'variant' | 'milestone'): string {
        return type === 'milestone' ? this.milestoneResourceUrl(courseId) : this.resourceUrl(courseId);
    }

    /**
     * All of the course's groups, of both types, fetched from their respective endpoints and merged. Sorted by id so the
     * merged list keeps a stable, creation-ordered sequence rather than "all variant groups, then all milestone groups".
     */
    getGroupsForCourse(courseId: number): Observable<ExerciseVariantGroupDTO[]> {
        return forkJoin([this.getVariantGroupsForCourse(courseId), this.getMilestoneGroupsForCourse(courseId)]).pipe(
            map(([variantGroups, milestoneGroups]) => [...variantGroups, ...milestoneGroups].sort((first, second) => (first.id ?? 0) - (second.id ?? 0))),
        );
    }

    /** Only the course's variant groups; milestone groups are served by {@link getMilestoneGroupsForCourse}. */
    getVariantGroupsForCourse(courseId: number): Observable<ExerciseVariantGroupDTO[]> {
        return this.http.get<ExerciseVariantGroupDTO[]>(this.resourceUrl(courseId)).pipe(map((groups) => groups.map((group) => this.convertDatesFromServer(group))));
    }

    /**
     * Only the course's milestone groups. The milestone endpoint returns a `MilestoneExerciseGroupDTO`, which carries no
     * discriminator of its own, so the type is stamped here - the rest of the client routes updates and deletes by it.
     */
    getMilestoneGroupsForCourse(courseId: number): Observable<ExerciseVariantGroupDTO[]> {
        return this.http
            .get<ExerciseVariantGroupDTO[]>(this.milestoneResourceUrl(courseId))
            .pipe(map((groups) => groups.map((group) => cloneWith(this.convertDatesFromServer(group), { type: 'milestone' as const }))));
    }

    /**
     * Loads the problem statements of a group's visible members in a single request, so the student group-detail page
     * can render previews without fanning out one heavyweight exercise-details request per member.
     */
    getProblemStatements(courseId: number, groupId: number): Observable<ExerciseProblemStatementDTO[]> {
        return this.http.get<ExerciseProblemStatementDTO[]>(`${this.resourceUrl(courseId)}/${groupId}/problem-statements`);
    }

    /** Whether the requesting student has started the group's anchor milestone exercise. */
    getMilestoneStatus(courseId: number, groupId: number): Observable<MilestoneStatusDTO> {
        return this.http.get<MilestoneStatusDTO>(`${this.milestoneResourceUrl(courseId)}/${groupId}/milestone-status`);
    }

    createGroup(courseId: number, group: CreateExerciseVariantGroupDTO): Observable<ExerciseVariantGroupDTO> {
        return this.http.post<ExerciseVariantGroupDTO>(this.resourceUrl(courseId), this.convertDatesToClient(group)).pipe(map((created) => this.convertDatesFromServer(created)));
    }

    /**
     * Creates a milestone exercise group: provisions a real `MilestoneExercise` (repositories, build plan, the works)
     * and wires it as the new group's anchor in one request. `milestoneExercise.course` is ignored server-side and
     * always taken from `courseId`.
     */
    createMilestoneGroup(courseId: number, milestoneExercise: ProgrammingExercise): Observable<ExerciseVariantGroupDTO> {
        return this.http
            .post<ExerciseVariantGroupDTO>(this.milestoneResourceUrl(courseId), toCreateMilestoneGroupPayload(milestoneExercise))
            .pipe(map((created) => cloneWith(this.convertDatesFromServer(created), { type: 'milestone' as const })));
    }

    /**
     * Creates a user story exercise in the given milestone exercise group. Its Language/Version-Control settings,
     * repositories and timeline are ignored server-side and always taken from the group's milestone exercise; only
     * title/short name/problem statement/grading settings from `userStoryExercise` are used.
     */
    createUserStoryExercise(courseId: number, groupId: number, userStoryExercise: ProgrammingExercise): Observable<ProgrammingExercise> {
        const copy = deepClone(userStoryExercise);
        // `categories` arrives as ExerciseCategory objects from the form, but the server's Exercise#categories column
        // is a Set<String> of JSON-encoded categories - every other create/update path stringifies them first (see
        // ProgrammingExerciseService.automaticSetup); this endpoint sent the raw objects, which Jackson can't bind
        // into a String and rejects with a 400 "Failed to read request".
        ExerciseService.stringifyExerciseCategories(copy);
        return this.http.post<ProgrammingExercise>(`${this.milestoneResourceUrl(courseId)}/${groupId}/user-story-exercises`, copy);
    }

    /**
     * Saves the group to the endpoint its {@link ExerciseVariantGroupDTO#type} belongs to. The type only selects the
     * route: neither server-side update DTO has such a field, so it is stripped from the request body.
     */
    updateGroup(courseId: number, group: ExerciseVariantGroupDTO): Observable<ExerciseVariantGroupDTO> {
        const { type, ...payload } = group;
        return this.http
            .put<ExerciseVariantGroupDTO>(`${this.urlForGroupType(courseId, type)}/${group.id}`, this.convertDatesToClient(payload))
            .pipe(map((updated) => this.convertDatesFromServer(updated)));
    }

    /** Deleting a milestone group also deletes its anchor exercise, so the group's type picks the route. */
    deleteGroup(courseId: number, groupId: number, type?: 'variant' | 'milestone'): Observable<void> {
        return this.http.delete<void>(`${this.urlForGroupType(courseId, type)}/${groupId}`);
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
        return group;
    }

    /**
     * Serialises the group's dates into the ISO strings the server expects. The result is a request body, not a `T`:
     * `convertDateFromClient` returns strings, so the date fields are no longer `dayjs.Dayjs` — the return type says so.
     */
    private convertDatesToClient<T extends GroupDateFields>(group: T): WithSerialisedDates<T> {
        const body = deepClone(group) as Record<string, unknown>;
        body.releaseDate = convertDateFromClient(group.releaseDate);
        body.startDate = convertDateFromClient(group.startDate);
        body.dueDate = convertDateFromClient(group.dueDate);
        body.assessmentDueDate = convertDateFromClient(group.assessmentDueDate);
        body.exampleSolutionPublicationDate = convertDateFromClient(group.exampleSolutionPublicationDate);
        return body as WithSerialisedDates<T>;
    }
}

/**
 * Payload for creating a milestone exercise group (mirrors the backend {@code CreateMilestoneExerciseGroupDTO}). The
 * server provisions the anchor {@code MilestoneExercise} from these settings; the group's own title is taken from it,
 * and the owning course from the request path.
 */
export interface CreateMilestoneExerciseGroupDTO {
    title?: string;
    shortName?: string;
    problemStatement?: string;
    channelName?: string;
    programmingLanguage?: ProgrammingLanguage;
    projectType?: ProjectType;
    packageName?: string;
    allowOnlineEditor?: boolean;
    allowOfflineIde?: boolean;
    allowOnlineIde?: boolean;
    staticCodeAnalysisEnabled?: boolean;
    maxStaticCodeAnalysisPenalty?: number;
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    assessmentDueDate?: dayjs.Dayjs;
    exampleSolutionPublicationDate?: dayjs.Dayjs;
    buildConfig?: ProgrammingExerciseBuildConfig;
}

/**
 * Narrows the full programming-exercise form model down to the milestone create payload. Points, assessment and
 * competency settings are deliberately dropped: a milestone is never graded itself, so the server rejects nothing here -
 * it simply never reads them.
 */
export function toCreateMilestoneGroupPayload(milestoneExercise: ProgrammingExercise): CreateMilestoneExerciseGroupDTO {
    return {
        title: milestoneExercise.title,
        shortName: milestoneExercise.shortName,
        problemStatement: milestoneExercise.problemStatement,
        channelName: milestoneExercise.channelName,
        programmingLanguage: milestoneExercise.programmingLanguage,
        projectType: milestoneExercise.projectType,
        packageName: milestoneExercise.packageName,
        allowOnlineEditor: milestoneExercise.allowOnlineEditor,
        allowOfflineIde: milestoneExercise.allowOfflineIde,
        allowOnlineIde: milestoneExercise.allowOnlineIde,
        staticCodeAnalysisEnabled: milestoneExercise.staticCodeAnalysisEnabled,
        maxStaticCodeAnalysisPenalty: milestoneExercise.maxStaticCodeAnalysisPenalty,
        releaseDate: milestoneExercise.releaseDate,
        startDate: milestoneExercise.startDate,
        dueDate: milestoneExercise.dueDate,
        assessmentDueDate: milestoneExercise.assessmentDueDate,
        exampleSolutionPublicationDate: milestoneExercise.exampleSolutionPublicationDate,
        buildConfig: milestoneExercise.buildConfig,
    };
}

/**
 * A group carrying everything the server needs to persist it. `title` is optional on the view model but required by
 * the payload, so callers narrow through {@link isPersistableGroup} instead of asserting with `!`.
 */
export type PersistableGroup = CourseExerciseGroup & { title: string };

/** Whether the group has the non-empty title the server requires (the edit dialog's Save button enforces this). */
export function isPersistableGroup(group: CourseExerciseGroup): group is PersistableGroup {
    return !!group.title?.trim();
}

/** Maps the edit dialog's view model to the create payload. */
export function toCreateGroupPayload(group: PersistableGroup): CreateExerciseVariantGroupDTO {
    return {
        title: group.title,
        maxPoints: group.maxPoints,
        releaseDate: group.releaseDate,
        startDate: group.startDate,
        dueDate: group.dueDate,
        assessmentDueDate: group.assessmentDueDate,
        exampleSolutionPublicationDate: group.exampleSolutionPublicationDate,
    };
}

/** Maps the edit dialog's view model to the update payload. The id is passed separately, so it cannot be missing. */
export function toUpdateGroupPayload(group: PersistableGroup, id: number): ExerciseVariantGroupDTO {
    const payload: ExerciseVariantGroupDTO = toCreateGroupPayload(group);
    payload.id = id;
    // Only picks the endpoint - ExerciseVariantGroupService.updateGroup strips it from the request body.
    payload.type = group.type;
    return payload;
}

/**
 * Maps a server DTO to the client {@link CourseExerciseGroup}. Members are resolved from {@code exercisesById} since
 * the DTO carries only ids; the client-only {@code order} stays undefined.
 */
export function toCourseExerciseGroup(dto: ExerciseVariantGroupDTO, exercisesById: Map<number, Exercise>): CourseExerciseGroup {
    return {
        id: dto.id,
        title: dto.title,
        type: dto.type,
        milestoneExerciseId: dto.milestoneExerciseId,
        maxPoints: dto.maxPoints,
        releaseDate: dto.releaseDate,
        startDate: dto.startDate,
        dueDate: dto.dueDate,
        assessmentDueDate: dto.assessmentDueDate,
        exampleSolutionPublicationDate: dto.exampleSolutionPublicationDate,
        exercises: (dto.exerciseIds ?? []).map((id) => exercisesById.get(id)).filter((exercise): exercise is Exercise => exercise !== undefined),
    };
}
