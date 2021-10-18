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
    constructor(public numberOfTutorItems: number, public averageTutorValue: number, public averageCourseValue: number, public tutorName: string) {}

    /**
     * Checks if the tutor value is within an allowed range.
     */
    get isWorseThanAverage(): boolean {
        const twentyPercentThreshold = this.averageCourseValue / 5;
        switch (this.allowedThreshold) {
            case TutorValueAllowedThreshold.AboveAverage:
                return this.averageTutorValue < this.averageCourseValue - twentyPercentThreshold;
            case TutorValueAllowedThreshold.BelowAverage:
                return this.averageTutorValue > this.averageCourseValue + twentyPercentThreshold;
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
        return `${this.tutorName} has received ${this.numberOfTutorItems} rating(s) with an average of ${this.averageTutorValue} ⭐️️ which is below ${this.averageCourseValue} ⭐.`;
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
        return `${this.tutorName} has assessed ${this.numberOfTutorItems} submission(s) with an average score of ${this.averageTutorValue.toFixed(2)}%
which is below ${this.averageCourseValue.toFixed(2)}%.`;
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
        return `${this.tutorName} has assessed ${this.numberOfTutorItems} submission(s) with an average number of complaints ${this.averageTutorValue} which is below ${this.averageCourseValue}.`;
    }
}
