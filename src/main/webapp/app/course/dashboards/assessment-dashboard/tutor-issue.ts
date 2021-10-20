export class TutorIssue {
    constructor(
        public tutorId: number,
        public tutorName: string,
        public numberOfTutorItems: number,
        public averageTutorValue: number,
        public threshold: number,
        public translationKey: string,
    ) {}
}

enum TutorValueAllowedThreshold {
    AboveAverage,
    BelowAverage,
}

/**
 * `TutorValueChecker` is an abstract class that wraps the verification logic whether or not the tutor value surpasses the allowed threshold.
 * The subclasses need to provide the `TutorValueAllowedThreshold` value as well as method to create `TutorIssue`.
 */
abstract class TutorValueChecker {
    constructor(public numberOfTutorItems: number, public averageTutorValue: number, public averageCourseValue: number, public tutorName: string, public tutorId: number) {}

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
     * The key to use in case of the translation.
     */
    abstract get translationKey(): string;

    /**
     * Creates tutor issue object out of the validation information.
     */
    toIssue(): TutorIssue {
        return new TutorIssue(this.tutorId, this.tutorName, this.numberOfTutorItems, this.averageTutorValue, this.thresholdValue, this.translationKey);
    }
}

/**
 * `TutorValueChecker` for rating.
 */
export class TutorIssueRatingChecker extends TutorValueChecker {
    get allowedThreshold(): TutorValueAllowedThreshold {
        return TutorValueAllowedThreshold.AboveAverage;
    }

    get translationKey(): string {
        return 'artemisApp.assessmentDashboard.tutorPerformanceIssues.ratings';
    }
}

/**
 * `TutorValueChecker` for score.
 */
export class TutorIssueScoreChecker extends TutorValueChecker {
    get allowedThreshold(): TutorValueAllowedThreshold {
        return TutorValueAllowedThreshold.AboveAverage;
    }

    get translationKey(): string {
        return 'artemisApp.assessmentDashboard.tutorPerformanceIssues.score';
    }
}

/**
 * `TutorValueChecker` for complaints.
 */
export class TutorIssueComplaintsChecker extends TutorValueChecker {
    get allowedThreshold(): TutorValueAllowedThreshold {
        return TutorValueAllowedThreshold.BelowAverage;
    }

    get translationKey(): string {
        return 'artemisApp.assessmentDashboard.tutorPerformanceIssues.complaints';
    }
}
