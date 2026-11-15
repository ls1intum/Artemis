import { Service, inject } from '@angular/core';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { StringCountService } from 'app/text/overview/service/string-count.service';
import { parseJson } from 'app/foundation/util/json.util';
import { ApollonModelData, countModelElements } from 'app/modeling/shared/apollon-model.util';

/**
 * Counts the words or model elements of an example submission for the list views; the example submission endpoints are called through the generated ExampleSubmissionApi.
 */
@Service()
export class ExampleSubmissionService {
    private stringCountService = inject(StringCountService);

    /**
     * Calculates the number of elements for the example submission
     *
     * @param submission associated with the example submission
     * @param exercise   needed to decide submission type
     * @returns number of words for text submission, or number of element for the modeling submission
     */
    getSubmissionSize(submission?: Submission, exercise?: Exercise): number {
        if (submission && exercise && exercise.type === ExerciseType.TEXT) {
            return this.stringCountService.countWords((submission as TextSubmission).text);
        } else if (submission && exercise && exercise.type === ExerciseType.MODELING) {
            const model = (submission as ModelingSubmission).model;
            return model ? countModelElements(parseJson<ApollonModelData>(model)) : 0;
        }
        return 0;
    }
}
