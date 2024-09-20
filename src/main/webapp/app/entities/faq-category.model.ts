export class FAQCategory {
    public color?: string;

    public category?: string;

    constructor(category: string | undefined, color: string | undefined) {
        this.color = color;
        this.category = category;
    }

    equals(otherExerciseCategory: FAQCategory): boolean {
        return this.color === otherExerciseCategory.color && this.category === otherExerciseCategory.category;
    }

    /**
     * @param otherExerciseCategory
     * @returns the alphanumerical order of the two exercise categories based on their display text
     */
    compare(otherExerciseCategory: FAQCategory): number {
        if (this.category === otherExerciseCategory.category) {
            return 0;
        }

        const displayText = this.category?.toLowerCase() ?? '';
        const otherCategoryDisplayText = otherExerciseCategory.category?.toLowerCase() ?? '';

        return displayText < otherCategoryDisplayText ? -1 : 1;
    }
}
