import { Range, round } from 'app/shared/util/utils';

export class TutorIssue {
    constructor(
        public tutorId: number,
        public tutorName: string,
        public numberOfTutorItems: number,
        public averageTutorValue: number,
        public allowedRange: Range,
        public translationKey: string,
    ) {}
}

/**
 * `TutorValueChecker` is an abstract class that wraps the verification logic whether or not the tutor value surpasses the allowed range.
 */
abstract class TutorValueChecker {
    constructor(public numberOfTutorItems: number, public averageTutorValue: number, public averageCourseValue: number, public tutorName: string, public tutorId: number) {
        this.averageTutorValue = round(this.averageTutorValue, 1);
    }

    /**
     * Checks if the tutor value is within an allowed range.
     */
    get isPerformanceIssue(): boolean {
        // If there are no 'items', then do not perform the check
        if (this.numberOfTutorItems === 0) {
            return false;
        }

        const isWithinBounds = this.allowedRange.lowerBound <= this.averageTutorValue && this.averageTutorValue <= this.allowedRange.upperBound;
        return !isWithinBounds;
    }

    /**
     * What is the allowed threshold for the tutor value.
     * By default it's average course value -/+ 20%
     */
    get allowedRange(): Range {
        const twentyPercent = this.averageCourseValue / 5;
        return new Range(round(this.averageCourseValue - twentyPercent, 1), round(this.averageCourseValue + twentyPercent, 1));
    }

    /**
     * The key to use in case of the translation.
     */
    abstract get translationKey(): string;

    /**
     * Creates tutor issue object out of the validation information.
     */
    toIssue(): TutorIssue {
        return new TutorIssue(this.tutorId, this.tutorName, this.numberOfTutorItems, this.averageTutorValue, this.allowedRange, this.translationKey);
    }
}

/**
 * `TutorValueChecker` for rating.
 */
export class TutorIssueRatingChecker extends TutorValueChecker {
    // Tutor average rating should be greater or equal to 3. Maximum rating is 5.
    static readonly MIN_RATING = 3;
    static readonly MAX_RATING = 5;

    get allowedRange(): Range {
        return new Range(TutorIssueRatingChecker.MIN_RATING, TutorIssueRatingChecker.MAX_RATING);
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
    get allowedRange(): Range {
        // Tutor complaint ratio should be less than average course complaint ratio + 20%
        const twentyPercent = this.averageCourseValue / 5;
        return new Range(0, round(this.averageCourseValue + twentyPercent, 1));
    }

    get translationKey(): string {
        return 'artemisApp.assessmentDashboard.tutorPerformanceIssues.complaints';
    }
}
