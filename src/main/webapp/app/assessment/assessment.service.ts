import { Exercise } from 'app/entities/exercise.model';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Result } from 'app/entities/result.model';

/**
 * For team exercises, the team tutor is the assessor and handles both complaints and feedback requests himself
 * For individual exercises, complaints are handled by a secondary reviewer and feedback requests by the assessor himself
 * For exam test runs, the original assessor is allowed to respond to complaints.
 */
export const isAllowedToRespondToComplaintAction = (isTestRun: boolean, isAssessor: boolean, complaint: Complaint, exercise?: Exercise): boolean => {
    if (exercise?.isAtLeastInstructor) {
        return true;
    }
    if (exercise?.teamMode || isTestRun) {
        return isAssessor;
    }
    if (exercise?.assessmentType === AssessmentType.AUTOMATIC && complaint.result && complaint.result.assessor === undefined) {
        return true;
    }
    return complaint!.complaintType === ComplaintType.COMPLAINT ? !isAssessor : isAssessor;
};

/**
 * During assessment, modifying the feedback should be allowed.
 * After the assessment, modification should be allowed if the deadline hasn't passed yet. After that, it should be prevented.
 * If a feedback request was filed, the feedback should not be modifiable.
 * If a complaint was filed, the feedback should be only modifiable if the user is allowed to handle the complaint.
 */
export const isAllowedToModifyFeedback = (
    isTestRun: boolean,
    isAssessor: boolean,
    hasAssessmentDueDatePassed: boolean,
    result?: Result,
    complaint?: Complaint,
    exercise?: Exercise,
): boolean => {
    if (exercise?.isAtLeastInstructor) {
        return true;
    }
    if (!result) {
        return false;
    }
    if (!result.completionDate) {
        return true;
    }
    if (complaint) {
        if (exercise?.assessmentType === AssessmentType.AUTOMATIC) {
            return isAllowedToRespondToComplaintAction(isTestRun, isAssessor, complaint, exercise);
        } else {
            return complaint.complaintType === ComplaintType.COMPLAINT && isAllowedToRespondToComplaintAction(isTestRun, isAssessor, complaint, exercise);
        }
    }
    return !hasAssessmentDueDatePassed;
};
