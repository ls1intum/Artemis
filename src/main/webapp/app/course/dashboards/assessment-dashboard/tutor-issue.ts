import { TranslateService } from '@ngx-translate/core';

export type TutorIssue = string;

enum TutorValueAllowedThreshold {
    AboveAverage,
    BelowAverage,
}

/**
 * `TutorValueChecker` is an abstract class that wraps the verification logic whether or not the tutor value surpasses the allowed threshold.
 * The subclasses need to provide the `TutorValueAllowedThreshold` value as well as method to create `TutorIssue`.
 */
abstract class TutorValueChecker {
    constructor(
        public numberOfTutorItems: number,
        public averageTutorValue: number,
        public averageCourseValue: number,
        public tutorName: string,
        protected translateService: TranslateService,
    ) {}

    get thresholdValue(): number {
        const twentyPercentThreshold = this.averageCourseValue / 5;
        switch (this.allowedThreshold) {
            case TutorValueAllowedThreshold.AboveAverage:
                return this.averageCourseValue - twentyPercentThreshold;
            case TutorValueAllowedThreshold.BelowAverage:
                return this.averageCourseValue + twentyPercentThreshold;
        }
    }

    /**
     * Checks if the tutor value is within an allowed range.
     */
    get isWorseThanAverage(): boolean {
        // If there are no 'items', then do not perform the check
        if (this.numberOfTutorItems === 0) {
            return false;
        }

        switch (this.allowedThreshold) {
            case TutorValueAllowedThreshold.AboveAverage:
                return this.averageTutorValue < this.thresholdValue;
            case TutorValueAllowedThreshold.BelowAverage:
                return this.averageTutorValue > this.thresholdValue;
        }
    }

    /**
     * What is the allowed threshold for the tutor value. Is it allowed to be less than or greater than the total average.
     */
    abstract get allowedThreshold(): TutorValueAllowedThreshold;

    /**
     * Creates tutor issue object out of the validation information.
     */
    abstract toIssue(): TutorIssue;
}

/**
 * `TutorValueChecker` for rating.
 */
export class TutorIssueRatingChecker extends TutorValueChecker {
    get allowedThreshold(): TutorValueAllowedThreshold {
        return TutorValueAllowedThreshold.AboveAverage;
    }

    toIssue(): TutorIssue {
        return this.translateService.instant('artemisApp.assessmentDashboard.tutorPerformanceIssues.ratings', {
            tutorName: this.tutorName,
            ratingsCount: this.numberOfTutorItems,
            averageTutorRating: this.averageTutorValue.toFixed(2),
            averageCourseRating: this.thresholdValue.toFixed(2),
        });
    }
}

/**
 * `TutorValueChecker` for score.
 */
export class TutorIssueScoreChecker extends TutorValueChecker {
    get allowedThreshold(): TutorValueAllowedThreshold {
        return TutorValueAllowedThreshold.AboveAverage;
    }

    toIssue(): TutorIssue {
        return this.translateService.instant('artemisApp.assessmentDashboard.tutorPerformanceIssues.score', {
            tutorName: this.tutorName,
            assessmentsCount: this.numberOfTutorItems,
            averageTutorScore: this.averageTutorValue.toFixed(2),
            averageCourseScore: this.thresholdValue.toFixed(2),
        });
    }
}

/**
 * `TutorValueChecker` for complaints.
 */
export class TutorIssueComplaintsChecker extends TutorValueChecker {
    get allowedThreshold(): TutorValueAllowedThreshold {
        return TutorValueAllowedThreshold.BelowAverage;
    }

    toIssue(): TutorIssue {
        return this.translateService.instant('artemisApp.assessmentDashboard.tutorPerformanceIssues.complaints', {
            tutorName: this.tutorName,
            assessmentsCount: this.numberOfTutorItems,
            complaintsTutorRatio: this.averageTutorValue.toFixed(2),
            complaintsCourseRatio: this.thresholdValue.toFixed(2),
        });
    }
}
