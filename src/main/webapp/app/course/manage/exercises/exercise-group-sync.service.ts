import { Injectable, inject } from '@angular/core';
import dayjs from 'dayjs/esm';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { Exercise, ExerciseType, ExerciseVariantGroupReference } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { QuizExercise, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ExerciseVariantGroupDTO, toCourseExerciseGroup } from 'app/course/manage/exercises/exercise-variant-group.service';

/**
 * Keeps the exercise management page's local exercise/group state in sync with variant-group and quiz data loaded from
 * the server, mirroring the server-side group timeline propagation so member dates and quiz badges update without a
 * full page reload. Pure state mapping only — the HTTP calls stay with the callers.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseGroupSyncService {
    private readonly quizExerciseService = inject(QuizExerciseService);

    /** Recomputes the client-derived quiz `status` / `quizStarted` flags (the server does not serialize them). */
    applyQuizClientState(quiz: QuizExercise): void {
        quiz.status = this.quizExerciseService.getStatus(quiz);
        quiz.quizStarted = quiz.status === QuizStatus.ACTIVE;
    }

    /**
     * Applies a group's shared timeline to one member exercise (returning a fresh copy so signal inputs react) and
     * recomputes quiz client state, mirroring the server-side re-sync in `applyGroupTimelineToExercises`. Used after a
     * group edit so member dates and quiz badges / lifecycle buttons update without a full page reload.
     */
    applyGroupTimelineToMember(exercise: Exercise, groupDto: ExerciseVariantGroupDTO, now: dayjs.Dayjs): Exercise {
        const updated: Exercise = cloneWith(exercise, {
            releaseDate: groupDto.releaseDate,
            startDate: groupDto.startDate,
            dueDate: groupDto.dueDate,
            assessmentDueDate: groupDto.assessmentDueDate,
            exampleSolutionPublicationDate: groupDto.exampleSolutionPublicationDate,
        });
        this.refreshQuizTimelineFlags(updated, now);
        return updated;
    }

    /**
     * Merges freshly loaded variant groups into the exercise list: exercises whose group membership changed get a new
     * object reference (so dependent signal computeds react) carrying the new group reference and the group's shared
     * timeline; removed members keep their own dates (the server keeps them on unassignment too) and only drop the
     * group reference. Returns the updated exercise list plus the mapped view-model groups.
     */
    mergeGroupsIntoExercises(exercises: Exercise[], dtos: ExerciseVariantGroupDTO[]): { exercises: Exercise[]; groups: CourseExerciseGroup[] } {
        const refByExerciseId = new Map<number, ExerciseVariantGroupReference>();
        const groupDtoByExerciseId = new Map<number, ExerciseVariantGroupDTO>();
        for (const dto of dtos) {
            const ref: ExerciseVariantGroupReference = {
                id: dto.id,
                title: dto.title,
                maxPoints: dto.maxPoints,
                releaseDate: dto.releaseDate,
                startDate: dto.startDate,
                dueDate: dto.dueDate,
                assessmentDueDate: dto.assessmentDueDate,
                exampleSolutionPublicationDate: dto.exampleSolutionPublicationDate,
            };
            for (const id of dto.exerciseIds ?? []) {
                refByExerciseId.set(id, ref);
                groupDtoByExerciseId.set(id, dto);
            }
        }
        const now = dayjs();
        const updatedExercises = exercises.map((exercise) => {
            if (exercise.id === undefined) return exercise;
            const newRef = refByExerciseId.get(exercise.id);
            // Both maps are filled from the same DTO ids above, so they are populated together — look the group DTO up
            // alongside the reference rather than asserting it is present.
            const groupDto = groupDtoByExerciseId.get(exercise.id);
            if (newRef?.id === exercise.exerciseVariantGroup?.id) return exercise;
            if (newRef === undefined || groupDto === undefined) {
                // The exercise was removed from its group. The server keeps the exercise's own dates on
                // unassignment, so only drop the group reference here — do not blank the timeline.
                return cloneWith(exercise, { exerciseVariantGroup: undefined });
            }
            const updated = this.applyGroupTimelineToMember(exercise, groupDto, now);
            updated.exerciseVariantGroup = newRef;
            return updated;
        });
        const exercisesById = new Map<number, Exercise>();
        for (const exercise of updatedExercises) {
            if (exercise.id !== undefined) {
                exercisesById.set(exercise.id, exercise);
            }
        }
        return { exercises: updatedExercises, groups: dtos.map((dto) => toCourseExerciseGroup(dto, exercisesById)) };
    }

    /**
     * Merges quiz batches and editability loaded from the dedicated quiz endpoint into the exercise list (the
     * /with-exercises response does not load the quizBatches association) and recomputes the batch-dependent quiz
     * status. Replaced quizzes get a fresh object reference so the row's signal input reacts; the groups are pointed at
     * the same new objects. Returns undefined when nothing needed to change.
     */
    mergeQuizInfo(exercises: Exercise[], groups: CourseExerciseGroup[], quizzes: QuizExercise[]): { exercises: Exercise[]; groups: CourseExerciseGroup[] } | undefined {
        // quizInfoById carries both quizBatches (for status computation) and isEditable (from the server,
        // which uses the authoritative DB check — client-side status alone can miss cases like INDIVIDUAL
        // mode quizzes where student batches are started but the quiz is not visibleToStudents yet).
        const quizInfoById = new Map(
            quizzes.map((quiz) => [quiz.id, { quizBatches: quiz.quizBatches, isEditable: quiz.isEditable, hasDragAndDropQuestions: quiz.hasDragAndDropQuestions }]),
        );
        const replacements = new Map<number, Exercise>();
        const merged = exercises.map((exercise) => {
            if (exercise.type !== ExerciseType.QUIZ || exercise.id === undefined) {
                return exercise;
            }
            // A single lookup: an absent entry means this quiz was not among the ones loaded, so leave it untouched.
            const info = quizInfoById.get(exercise.id);
            if (info === undefined) {
                return exercise;
            }
            // hasDragAndDropQuestions: the /with-exercises response has no question graph, so this flag is the
            // list view's only way to tell whether the quiz supports AI variant generation.
            const quiz = cloneWith(exercise as QuizExercise, {
                quizBatches: info.quizBatches,
                isEditable: info.isEditable,
                hasDragAndDropQuestions: info.hasDragAndDropQuestions,
            });
            this.applyQuizClientState(quiz);
            replacements.set(exercise.id, quiz);
            return quiz;
        });
        if (replacements.size === 0) {
            return undefined;
        }
        return {
            exercises: merged,
            groups: groups.map((group) =>
                cloneWith(group, {
                    exercises: (group.exercises ?? []).map((exercise) => (exercise.id !== undefined && replacements.has(exercise.id) ? replacements.get(exercise.id)! : exercise)),
                }),
            ),
        };
    }

    /** Recomputes the timeline-derived quiz flags after the exercise's dates changed; no-op for non-quiz exercises. */
    private refreshQuizTimelineFlags(exercise: Exercise, now: dayjs.Dayjs): void {
        if (exercise.type !== ExerciseType.QUIZ) {
            return;
        }
        const quiz = exercise as QuizExercise;
        const startDate = quiz.startDate ?? quiz.releaseDate;
        // Recompute unconditionally: a grouped quiz's timeline is fully governed by its group, so when the group clears a
        // date the derived flag must be reset too. A missing start/release date means not-yet-visible, a missing due date
        // means not-yet-ended (guarding on `!== undefined` here would leave a stale `true` after the group unset a date).
        quiz.visibleToStudents = startDate !== undefined && startDate.isBefore(now);
        quiz.quizEnded = quiz.dueDate !== undefined && quiz.dueDate.isBefore(now);
        this.applyQuizClientState(quiz);
    }
}
