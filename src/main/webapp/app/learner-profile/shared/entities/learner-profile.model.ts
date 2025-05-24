export class CourseLearnerProfileDTO {
    /**
     * Minimum value allowed for profile fields representing values on a Likert scale.
     * Must be the same as in the server in CourseLearnerProfile.java
     */
    public static readonly MIN_VALUE = 1;
    /**
     * Maximum value allowed for profile fields representing values on a Likert scale.
     * Must be the same as in the server in CourseLearnerProfile.java
     */
    public static readonly MAX_VALUE = 5;

    public id: number;
    public courseId: number;
    public courseTitle: string;
    public aimForGradeOrBonus: number;
    public timeInvestment: number;
    public repetitionIntensity: number;

    public isValid(): boolean {
        return this.isValueInRange(this.aimForGradeOrBonus) && this.isValueInRange(this.timeInvestment) && this.isValueInRange(this.repetitionIntensity);
    }

    private isValueInRange(value: number): boolean {
        return value >= CourseLearnerProfileDTO.MIN_VALUE && value <= CourseLearnerProfileDTO.MAX_VALUE;
    }
}
