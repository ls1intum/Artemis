export class ExerciseCategory {
    public color?: string;

    // TODO should be renamed to "name" -> accessing variable via "category.name" instead of "category.category" - requires database migration (stored as json in database, see the table "exercise_categories")
    public category?: string;

    constructor() {}
}
