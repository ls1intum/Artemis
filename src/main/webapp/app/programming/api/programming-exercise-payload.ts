import { HttpResponse } from '@angular/common/http';
import { omit } from 'lodash-es';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { convertDateFromClient, convertDateFromServer } from 'app/foundation/util/date.utils';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { convertBuildConfigDatesFromServer } from 'app/programming/shared/utils/build-time-window.utils';

/** Serializes the exercise used by setup and import endpoints, removing circular back-references. */
export function toProgrammingExerciseSetupPayload(exercise: ProgrammingExercise): ProgrammingExercise {
    const copy = cloneWith(ExerciseService.convertExerciseDatesFromClient(exercise), {
        buildAndTestStudentSubmissionsAfterDueDate: convertDateFromClient(exercise.buildAndTestStudentSubmissionsAfterDueDate),
    });
    if (copy.templateParticipation) {
        copy.templateParticipation = omit(copy.templateParticipation, ['exercise', 'results']);
    }
    if (copy.solutionParticipation) {
        copy.solutionParticipation = omit(copy.solutionParticipation, ['exercise', 'results']);
    }
    if (copy.competencyLinks) {
        copy.competencyLinks = copy.competencyLinks.map((link) => omit(link, ['exercise']));
    }
    const payload = copy as ProgrammingExercise;
    ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(payload);
    ExerciseService.stringifyExerciseCategories(payload);
    return payload;
}

export function convertProgrammingExerciseResponseDatesFromServer(entity: HttpResponse<ProgrammingExercise>) {
    const res = ExerciseService.convertExerciseResponseDatesFromServer(entity);
    if (res.body) {
        res.body.buildAndTestStudentSubmissionsAfterDueDate = convertDateFromServer(res.body.buildAndTestStudentSubmissionsAfterDueDate);
        convertBuildConfigDatesFromServer(res.body.buildConfig);
    }
    return res;
}
