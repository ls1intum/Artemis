export class TutorIssue {
    constructor(
        public tutorId: number,
        public tutorName: string,
        public numberOfTutorItems: number,
        public averageTutorValue: number,
        public threshold: Threshold,
        public translationKey: string,
    ) {}
}

/**
 * Represents the lower and upper bound of allowed values.
 */
type Threshold = [number, number];

/**
 * `TutorValueChecker` is an abstract class that wraps the verification logic whether or not the tutor value surpasses the allowed threshold.
 * The subclasses need to provide the `TutorValueAllowedThreshold` value as well as method to create `TutorIssue`.
 */
abstract class TutorValueChecker {
    constructor(public numberOfTutorItems: number, public averageTutorValue: number, public averageCourseValue: number, public tutorName: string, public tutorId: number) {
        this.averageTutorValue = this.round(this.averageTutorValue);
    }

    /**
     * Checks if the tutor value is within an allowed range.
     */
    get isPerformanceIssue(): boolean {
        // If there are no 'items', then do not perform the check
        if (this.numberOfTutorItems === 0) {
            return false;
        }

        const [lowerBound, upperBound] = this.allowedThreshold;
        const isWithinBounds = lowerBound <= this.averageTutorValue && this.averageTutorValue <= upperBound;
        return !isWithinBounds;
    }

    /**
     * What is the allowed threshold for the tutor value.
     * By default it's average course value -/+ 20%
     */
    get allowedThreshold(): Threshold {
        const twentyPercent = this.averageCourseValue / 5;
        return [this.round(this.averageCourseValue - twentyPercent), this.round(this.averageCourseValue + twentyPercent)];
    }

    /**
     * The key to use in case of the translation.
     */
    abstract get translationKey(): string;

    /**
     * Creates tutor issue object out of the validation information.
     */
    toIssue(): TutorIssue {
        return new TutorIssue(this.tutorId, this.tutorName, this.numberOfTutorItems, this.averageTutorValue, this.allowedThreshold, this.translationKey);
    }

    /**
     * Rounds the value up to one digit after comma
     */
    protected round(value: number) {
        return Math.round(value * 10) / 10;
    }
}

/**
 * `TutorValueChecker` for rating.
 */
export class TutorIssueRatingChecker extends TutorValueChecker {
    get allowedThreshold(): Threshold {
        // Tutor average rating should be greater or equal to 3. Maximum rating is 5.
        return [3, 5];
    }

    get translationKey(): string {
        return 'artemisApp.assessmentDashboard.tutorPerformanceIssues.ratings';
    }
}

/**
 * `TutorValueChecker` for score.
 */
export class TutorIssueScoreChecker extends TutorValueChecker {
    get translationKey(): string {
        return 'artemisApp.assessmentDashboard.tutorPerformanceIssues.score';
    }
}

/**
 * `TutorValueChecker` for complaints.
 */
export class TutorIssueComplaintsChecker extends TutorValueChecker {
    get allowedThreshold(): Threshold {
        // Tutor complaint ratio should be less than average course complaint ratio + 20%
        const twentyPercent = this.averageCourseValue / 5;
        return [0, this.round(this.averageCourseValue + twentyPercent)];
    }

    get translationKey(): string {
        return 'artemisApp.assessmentDashboard.tutorPerformanceIssues.complaints';
    }
}
