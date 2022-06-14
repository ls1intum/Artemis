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

    /**
     * Applies the filter based on the selection in the chart to the displayed submissions
     * @param submissions all submissions visible in the default view
     */
    applyChartFilter(submissions: Submission[]): void {
        if (this.filterOption === undefined) {
            this.filteredSubmissions = submissions;
            return;
        }
        switch (this.filterOption) {
            case AssessmentFilter.UNASSESSED:
                this.filteredSubmissions = submissions.filter((submission) => {
                    return (
                        !submission.results || submission.results.length === 0 || !submission.results.last()?.rated || submission.results.every((result) => !result.completionDate)
                    );
                });
                break;

            case AssessmentFilter.Manual:
                this.filteredSubmissions = submissions.filter((submission) => {
                    return (
                        submission.results &&
                        submission.results.length > 0 &&
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
                        submission.results.length > 0 &&
                        submission.results.last()!.rated &&
                        submission.results.last()!.assessmentType === AssessmentType.SEMI_AUTOMATIC &&
                        submission.results.last()!.completionDate
                    );
                });
                break;
        }
    }

    /**
     * Triggers the reset of the applied chart filter
     * Also resets the "Show locked" option to "Show all" if applied together with the chart filter
     */
    resetFilterOptions(): void {
        this.filterOption = undefined;
        this.resetFilter = true;
        this.applyChartFilter(this.submissions);
    }
}
