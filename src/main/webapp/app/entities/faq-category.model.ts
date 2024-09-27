export class FAQCategory {
    public color?: string;

    public category?: string;

    constructor(category: string | undefined, color: string | undefined) {
        this.color = color;
        this.category = category;
    }

    equals(otherFaqCategory: FAQCategory): boolean {
        return this.color === otherFaqCategory.color && this.category === otherFaqCategory.category;
    }

    /**
     * @param otherFaqCategory
     * @returns the alphanumerical order of the two exercise categories based on their display text
     */
    compare(otherFaqCategory: FAQCategory): number {
        if (this.category === otherFaqCategory.category) {
            return 0;
        }

        const displayText = this.category?.toLowerCase() ?? '';
        const otherCategoryDisplayText = otherFaqCategory.category?.toLowerCase() ?? '';

        return displayText < otherCategoryDisplayText ? -1 : 1;
    }
}
