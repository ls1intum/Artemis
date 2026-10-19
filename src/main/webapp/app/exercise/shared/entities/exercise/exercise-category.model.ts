export class ExerciseCategory {
    public color?: string;

    // TODO should be renamed to "name" -> accessing variable via "category.name" instead of "category.category" - requires database migration (stored as json in database, see the table "exercise_categories")
    public category?: string;

    constructor(category: string | undefined, color: string | undefined) {
        this.color = color;
        this.category = category;
    }

    equals(otherExerciseCategory: ExerciseCategory): boolean {
        return this.color === otherExerciseCategory.color && this.category === otherExerciseCategory.category;
    }

    /**
     * @param otherExerciseCategory
     * @returns the alphanumerical order of the two exercise categories based on their display text
     */
    compare(otherExerciseCategory: ExerciseCategory): number {
        if (this.category === otherExerciseCategory.category) {
            return 0;
        }

        const displayText = this.category?.toLowerCase() ?? '';
        const otherCategoryDisplayText = otherExerciseCategory.category?.toLowerCase() ?? '';

        return displayText < otherCategoryDisplayText ? -1 : 1;
    }
}

/**
 * The wire shape of a serialized {@link ExerciseCategory}: the JSON-string payload stored in the DB
 * (server `Set<String>`) or a DTO object. Derived from the class via `Pick`, so it carries only the data
 * fields — no methods — and stays in sync with the class: renaming a field breaks this at compile time
 * instead of silently drifting. Use it to type the result of parsing a category, then build a real
 * {@link ExerciseCategory} instance from it.
 */
export type SerializedExerciseCategory = Pick<ExerciseCategory, 'category' | 'color'>;
