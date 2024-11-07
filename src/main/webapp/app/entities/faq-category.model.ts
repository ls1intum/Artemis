export class FaqCategory {
    public color?: string;

    public category?: string;

    constructor(category: string | undefined, color: string | undefined) {
        this.color = color;
        this.category = category;
    }

    equals(otherFaqCategory: FaqCategory): boolean {
        return this.color === otherFaqCategory.color && this.category === otherFaqCategory.category;
    }

    /**
     * @param otherFaqCategory
     * @returns the alphanumerical order of the two categories based on their display text
     */
    compare(otherFaqCategory: FaqCategory): number {
        if (this.category === otherFaqCategory.category) {
            return 0;
        }

        const displayText = this.category?.toLowerCase() ?? '';
        const otherCategoryDisplayText = otherFaqCategory.category?.toLowerCase() ?? '';

        return displayText < otherCategoryDisplayText ? -1 : 1;
    }
}
