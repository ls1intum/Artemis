import { AssessmentType } from 'app/entities/assessment-type.model';
import { Submission } from 'app/entities/submission.model';
import { AssessmentAndComplaintFilter } from 'app/exercises/text/assess/text-assessment-dashboard/text-assessment-dashboard.component';

export abstract class AbstractAssessmentDashboard {
    filterOption?: number;
    submissions: Submission[] = [];
    filteredSubmissions: Submission[] = [];

    applyChartFilter(submissions: Submission[]) {
        if (this.filterOption === undefined) {
            return;
        }
        console.log(typeof this.filterOption);
        switch (this.filterOption) {
            case AssessmentAndComplaintFilter.UNASSESSED:
                this.filteredSubmissions = submissions.filter((submission) => {
                    return !submission.results || submission.results.every((result) => !result.rated) || submission.results.every((result) => !result.completionDate);
                });
                break;

            case AssessmentAndComplaintFilter.Manual:
                this.filteredSubmissions = submissions.filter((submission) => {
                    return (
                        submission.results && submission.results[0].rated && submission.results[0].assessmentType === AssessmentType.MANUAL && submission.results[0].completionDate
                    );
                });
                break;

            case AssessmentAndComplaintFilter.SEMI_AUTOMATIC:
                this.filteredSubmissions = submissions.filter((submission) => {
                    return (
                        submission.results &&
                        submission.results[0].rated &&
                        submission.results[0].assessmentType === AssessmentType.SEMI_AUTOMATIC &&
                        submission.results[0].completionDate
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
    }

    /**
     * Update the submission filter for assessments
     * @param {Submission[]} filteredSubmissions - Submissions to be filtered for
     */
    abstract updateFilteredSubmissions(filteredSubmissions: Submission[]): void;
}
