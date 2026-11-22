import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import type { ExerciseGroup as GeneratedExerciseGroup } from 'app/openapi/model/exercise-group';
import type { ExamForExerciseGroup } from 'app/openapi/model/exam-for-exercise-group';

function toExam(dto: ExamForExerciseGroup): Exam {
    // Plain objects, not new Exam() / new Course(): the constructors add defaults the server never sent.
    const course: Course | undefined = dto.course && {
        id: dto.course.id,
        defaultProgrammingLanguage: Object.values(ProgrammingLanguage).find((language) => language === dto.course?.defaultProgrammingLanguage),
    };
    const exam: Exam = { id: dto.id, examMode: dto.examMode === undefined ? undefined : ExamMode[dto.examMode], course };
    // The old client left the date string in place; hydrate copies it without the type of the Exam field getting in the way.
    return hydrate(exam, { exampleSolutionPublicationDate: dto.exampleSolutionPublicationDate });
}

/**
 * Converts an exercise group of the single-group endpoints (get, create, update) into an {@link ExerciseGroup}.
 *
 * These endpoints send the exam as a slim projection and leave out the exercises, so the group carries only what they send.
 * The example solution publication date stays the string the server sent, as the old client left it; every reader wraps it
 * in dayjs() or only tests it for presence.
 *
 * @param dto the generated exercise group
 * @returns the exercise group carrying only the fields the server sent
 */
export function toExerciseGroup(dto: GeneratedExerciseGroup): ExerciseGroup {
    return {
        id: dto.id,
        title: dto.title,
        isMandatory: dto.isMandatory,
        exam: dto.exam && toExam(dto.exam),
    };
}
