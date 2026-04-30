import { Failure } from './failure';
import { Success } from './success';


/**
 * Discriminated response for a single quiz question refinement, either a success with the refined question or a failure with an error message
 */
/**
 * @type QuizQuestionRefinementResponse
 * Discriminated response for a single quiz question refinement, either a success with the refined question or a failure with an error message
 * @export
 */
export type QuizQuestionRefinementResponse = Failure | Success;

