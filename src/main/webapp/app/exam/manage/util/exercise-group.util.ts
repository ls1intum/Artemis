import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import type { ExerciseGroup as GeneratedExerciseGroup } from 'app/openapi/model/exercise-group';

/**
 * Converts an exercise group of the single-group endpoints (get, create, update) into an {@link ExerciseGroup}.
 *
 * These endpoints send the exam as a slim projection and leave out the exercises, so the group carries only what they send.
 *
 * @param dto the generated exercise group
 * @returns the exercise group with a dayjs example solution publication date
 */
export function toExerciseGroup(dto: GeneratedExerciseGroup): ExerciseGroup {
    const exerciseGroup = new ExerciseGroup();
    exerciseGroup.id = dto.id;
    exerciseGroup.title = dto.title;
    exerciseGroup.isMandatory = dto.isMandatory;
    if (dto.exam) {
        const exam = new Exam();
        exam.id = dto.exam.id;
        exam.examMode = dto.exam.examMode === undefined ? undefined : ExamMode[dto.exam.examMode];
        exam.exampleSolutionPublicationDate = convertDateStringFromServer(dto.exam.exampleSolutionPublicationDate);
        if (dto.exam.course) {
            const course = new Course();
            course.id = dto.exam.course.id;
            course.defaultProgrammingLanguage = Object.values(ProgrammingLanguage).find((language) => language === dto.exam?.course?.defaultProgrammingLanguage);
            exam.course = course;
        }
        exerciseGroup.exam = exam;
    }
    return exerciseGroup;
}
