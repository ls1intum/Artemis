import { AssessmentType } from 'app/entities/assessment-type.model';
import { Submission } from 'app/entities/submission.model';

enum AssessmentFilter {
    UNASSESSED = 0,
    MANUAL = 1,
    SEMI_AUTOMATIC = 2,
}

enum AssessmentTranslation {
    UNASSESSED = 'unassessedSubmissions',
    MANUAL = 'manuallyAssessedSubmissions',
    SEMI_AUTOMATIC = 'semiAutomaticallyAssessedSubmissions',
}

export abstract class AbstractAssessmentDashboard {
    filterOption?: number;
    translationString: string | undefined;
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
                this.translationString = AssessmentTranslation.UNASSESSED;
                this.filteredSubmissions = submissions.filter((submission) => {
                    return !submission.results || !submission.results.length || !submission.results.last()?.rated || submission.results.every((result) => !result.completionDate);
                });
                break;

            case AssessmentFilter.MANUAL:
                this.translationString = AssessmentTranslation.MANUAL;
                this.filteredSubmissions = submissions.filter((submission) => {
                    return (
                        submission.results &&
                        submission.results.length &&
                        submission.results.last()!.rated &&
                        submission.results.last()!.assessmentType === AssessmentType.MANUAL &&
                        submission.results.last()!.completionDate
                    );
                });
                break;

            case AssessmentFilter.SEMI_AUTOMATIC:
                this.translationString = AssessmentTranslation.SEMI_AUTOMATIC;
                this.filteredSubmissions = submissions.filter((submission) => {
                    return (
                        submission.results &&
                        submission.results.length &&
                        submission.results.last()!.rated &&
                        submission.results.last()!.assessmentType === AssessmentType.SEMI_AUTOMATIC &&
                        submission.results.last()!.completionDate
                    );
                });
                break;
        }
        // If no filter applies, we can omit the filter option and therefore hide the reset button
        if (this.submissions.length === this.filteredSubmissions.length) {
            this.filterOption = undefined;
        }
    }

    /**
     * Triggers the reset of the applied chart filter
     * Also resets the "Show locked" option to "Show all" if applied together with the chart filter
     */
    resetFilterOptions(): void {
        this.filterOption = undefined;
        this.translationString = undefined;
        this.resetFilter = true;
        this.applyChartFilter(this.submissions);
    }
}
