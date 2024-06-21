import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';

import { SelfLearningFeedbackRequest } from 'app/entities/self-learning-feedback-request.model';

/**
 * Get the icon type for the self-learning-feedback icon
 *
 */
export const getSelfLearningIconClass = (selfLearningFeedbackRequest: SelfLearningFeedbackRequest | undefined): IconProp => {
    if (selfLearningFeedbackRequest && SelfLearningFeedbackRequest.isPending(selfLearningFeedbackRequest)) {
        return faQuestionCircle;
    }
    if (selfLearningFeedbackRequest && SelfLearningFeedbackRequest.isFailed(selfLearningFeedbackRequest)) {
        return faTimesCircle;
    }
    return faQuestionCircle;
};

/**
 * Get the css class for the entire text as a string
 *
 * @return {string} the css class
 */
export const getSelfLearningFeedbackTextColorClass = (selfLearningFeedbackRequest: SelfLearningFeedbackRequest | undefined) => {
    if (selfLearningFeedbackRequest && SelfLearningFeedbackRequest.isPending(selfLearningFeedbackRequest)) {
        return 'text-secondary';
    }
    if (selfLearningFeedbackRequest && SelfLearningFeedbackRequest.isFailed(selfLearningFeedbackRequest)) {
        return 'text-danger';
    }
    return 'text-secondary';
};
