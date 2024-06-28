export class ExerciseCategory {
    public color?: string;

    // TODO should be renamed to "name" -> accessing variable via "category.name" instead of "category.category" - requires database migration (stored as json in database, see the table "exercise_categories")
    public category?: string;

    constructor(color: string | undefined, category: string | undefined) {
        this.color = color;
        this.category = category;
    }

    // TODO add compare function here to be used for sort

    equals(other: ExerciseCategory): boolean {
        return this.color === other.color && this.category === other.category;
    }
}
