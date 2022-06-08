import { AssessmentType } from 'app/entities/assessment-type.model';
import { Submission } from 'app/entities/submission.model';

enum AssessmentFilter {
    UNASSESSED,
    Manual,
    SEMI_AUTOMATIC,
}

export abstract class AbstractAssessmentDashboard {
    filterOption?: number;
    submissions: Submission[];
    filteredSubmissions: Submission[];
    resetFilter = false;

    applyChartFilter(submissions: Submission[]): void {
        if (this.filterOption === undefined) {
            return;
        }
        switch (this.filterOption) {
            case AssessmentFilter.UNASSESSED:
                this.filteredSubmissions = submissions.filter((submission) => {
                    return !submission.results || !submission.results.last()?.rated || submission.results.every((result) => !result.completionDate);
                });
                break;

            case AssessmentFilter.Manual:
                this.filteredSubmissions = submissions.filter((submission) => {
                    return (
                        submission.results &&
                        submission.results.last()!.rated &&
                        submission.results.last()!.assessmentType === AssessmentType.MANUAL &&
                        submission.results.last()!.completionDate
                    );
                });
                break;

            case AssessmentFilter.SEMI_AUTOMATIC:
                this.filteredSubmissions = submissions.filter((submission) => {
                    return (
                        submission.results &&
                        submission.results.last()!.rated &&
                        submission.results.last()!.assessmentType === AssessmentType.SEMI_AUTOMATIC &&
                        submission.results.last()!.completionDate
                    );
                });
                break;
            default:
                this.filteredSubmissions = submissions;
        }
    }

    resetFilterOptions() {
        this.updateFilteredSubmissions(this.submissions);
        this.filterOption = undefined;
        this.resetFilter = true;
    }

    /**
     * Update the submission filter for assessments
     * @param {Submission[]} filteredSubmissions - Submissions to be filtered for
     */
    abstract updateFilteredSubmissions(filteredSubmissions: Submission[]): void;
}
