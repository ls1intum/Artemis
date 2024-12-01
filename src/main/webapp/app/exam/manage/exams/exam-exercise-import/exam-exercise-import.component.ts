import { Component, Input, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam/exam.model';
import { faCheckDouble, faFont } from '@fortawesome/free-solid-svg-icons';
import { Exercise, ExerciseType, getIcon } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { EXERCISE_TITLE_NAME_REGEX, SHORT_NAME_PATTERN } from 'app/shared/constants/input.constants';

@Component({
    selector: 'jhi-exam-exercise-import',
    templateUrl: './exam-exercise-import.component.html',
    styleUrls: ['./exam-exercise-import.component.scss'],
})
export class ExamExerciseImportComponent implements OnInit {
    @Input() exam: Exam;
    @Input() importInSameCourse = false;
    // Map to determine, which exercises the user has selected and therefore should be imported alongside an exam
    selectedExercises = new Map<ExerciseGroup, Set<Exercise>>();
    // Map / Blocklist with the title and shortName of the programming exercises, that have been either rejected by the server
    // or must be changed because the exam is imported into the same course
    titleAndShortNameOfProgrammingExercises = new Map<number, string[]>();

    // Map of programming exercise ids with duplicated titles and their corresponding title
    exercisesWithDuplicatedTitles = new Map<number, string>();

    // Map of programming exercise ids with duplicated short names and their corresponding short name
    exercisesWithDuplicatedShortNames = new Map<number, string>();

    // Expose enums to the template
    exerciseType = ExerciseType;
    // Map to determine, if an exercise group contains at least one programming exercise.
    // I.E. the short name must be displayed in the corresponding table
    containsProgrammingExercises = new Map<ExerciseGroup, boolean>();

    // Patterns
    // length of < 3 is also accepted in order to provide more accurate validation error messages
    readonly SHORT_NAME_REGEX = RegExp('(^(?![\\s\\S]))|^[a-zA-Z][a-zA-Z0-9]*$|' + SHORT_NAME_PATTERN); // must start with a letter and cannot contain special characters

    // Icons
    faCheckDouble = faCheckDouble;
    faFont = faFont;

    getExerciseIcon = getIcon;

    constructor() {}

    ngOnInit(): void {
        this.initializeSelectedExercisesAndContainsProgrammingExercisesMaps();
        // If the exam is imported into the same course, the title + shortName of Programming Exercises must be changed
        if (this.importInSameCourse) {
            this.initializeTitleAndShortNameMap();
        }
    }

    /**
     * Method to update the Maps after a rejected import due to invalid project key(s) of programming exercise(s)
     * Called by the parent component
     */
    updateMapsAfterRejectedImportDueToInvalidProjectKey() {
        this.titleAndShortNameOfProgrammingExercises.clear();
        this.initializeTitleAndShortNameMap();
        this.selectedExercises.clear();
        this.containsProgrammingExercises.clear();
        this.initializeSelectedExercisesAndContainsProgrammingExercisesMaps();
    }

    /**
     * Method to update the Maps after a rejected import due to duplicated short name or title
     * Called by the parent component
     */
    updateMapsAfterRejectedImportDueToDuplicatedShortNameOrTitle() {
        this.titleAndShortNameOfProgrammingExercises.clear();
        this.selectedExercises.clear();
        this.containsProgrammingExercises.clear();
        this.initializeSelectedExercisesAndContainsProgrammingExercisesMaps();
    }

    /**
     * Method to initialize the Maps selectedExercises and containsProgrammingExercises
     */
    initializeSelectedExercisesAndContainsProgrammingExercisesMaps() {
        // Initialize selectedExercises
        this.exam.exerciseGroups?.forEach((exerciseGroup) => {
            this.selectedExercises.set(exerciseGroup, new Set<Exercise>(exerciseGroup.exercises));
        });
        const duplicated = new Set<string>();
        // Initialize containsProgrammingExercises
        this.exam.exerciseGroups!.forEach((exerciseGroup) => {
            const hasProgrammingExercises = !!exerciseGroup.exercises?.some((value) => value.type === ExerciseType.PROGRAMMING);
            this.containsProgrammingExercises.set(exerciseGroup, hasProgrammingExercises);
            // In case of a rejected import, we can delete programming exercises with a title from the Map / blocklist, as those were not rejected by the server.
            exerciseGroup.exercises?.forEach((exercise) => {
                if (exercise.type === ExerciseType.PROGRAMMING && exercise.title) {
                    if (duplicated.has(exercise.title)) {
                        this.titleAndShortNameOfProgrammingExercises.set(exercise.id!, [exercise.title!, '']);
                        exercise.title = '';
                    } else {
                        duplicated.add(exercise.title);
                        this.titleAndShortNameOfProgrammingExercises.delete(exercise.id!);
                    }
                }
            });
        });
    }

    /**
     * Method to initialize the Map titleAndShortNameOfProgrammingExercises for the import
     * Case rejected Import: In the selected exercises, all the (previously) selected exercises are stored. All programming exercises are added to the Map,
     * which functions as a blocklist.
     * Case import in same course: The selected exercises are already initialized and all programming exercises are added to the
     * Map / Blocklist, as the title and shortName must be changed when importing into the same course.
     */
    initializeTitleAndShortNameMap() {
        // The title and short name of the programming exercises are added to the map to display the rejected title and short name to the user
        this.selectedExercises.forEach((exerciseSet) => {
            exerciseSet.forEach((exercise) => {
                if (exercise.type === ExerciseType.PROGRAMMING) {
                    this.titleAndShortNameOfProgrammingExercises.set(exercise.id!, [exercise.title!, exercise.shortName!]);
                }
            });
        });
    }

    /**
     * Sets the selected exercise for an exercise group in the selectedExercises Map-
     * The ExerciseGroup is the Key in the Map, the Exercises are stored as a Set as the value.
     * @param exercise The selected exercise
     * @param exerciseGroup The exercise group for which the user selected an exercise to import
     */
    onSelectExercise(exercise: Exercise, exerciseGroup: ExerciseGroup) {
        if (this.selectedExercises!.get(exerciseGroup)!.has(exercise)) {
            // Case Exercise is already selected -> delete
            this.selectedExercises!.get(exerciseGroup)!.delete(exercise);
            // The title or short name of the exercise is not considered to be duplicated. In case it was duplicated
            // before, the set with duplicates has to be updated (checked again if any objects can be removed from the map).
            if (this.exercisesWithDuplicatedTitles.delete(exercise.id!)) {
                this.removeProgrammingExerciseFromDuplicates(exercise.title!, true);
            }

            if (this.exercisesWithDuplicatedShortNames.delete(exercise.id!)) {
                this.removeProgrammingExerciseFromDuplicates(exercise.shortName!, false);
            }
        } else {
            this.selectedExercises!.get(exerciseGroup)!.add(exercise);
            if (exercise.type === ExerciseType.PROGRAMMING) {
                this.checkForDuplicatedTitlesOrShortNamesOfProgrammingExercise(exercise, exerciseGroup, true);
                this.checkForDuplicatedTitlesOrShortNamesOfProgrammingExercise(exercise, exerciseGroup, false);
            }
        }
    }

    /**
     * Returns if an exercise is (currently) selected for import.
     * I.E. if the exercise is contained within the Map selectedExercises
     * @param exercise the exercise for which should be determined, if it is selected by the user
     * @param exerciseGroup the corresponding exercise group i.e. the key of the map
     */
    exerciseIsSelected(exercise: Exercise, exerciseGroup: ExerciseGroup): boolean {
        return this.selectedExercises!.get(exerciseGroup)!.has(exercise);
    }

    /**
     * Returns if an exercise Group contains exercises
     * I.E. At least one exercise within the exercise Group is selected for import
     * @param exerciseGroup the corresponding exercise group
     */
    exerciseGroupContainsExercises(exerciseGroup: ExerciseGroup): boolean {
        return this.selectedExercises!.get(exerciseGroup)!.size > 0;
    }

    /**
     * Returns if an exercise Group contains at least one programming exercise
     * I.E. the short name must be displayed in the selection menu
     * @param exerciseGroup the corresponding exercise group
     */
    exerciseGroupContainsProgrammingExercises(exerciseGroup: ExerciseGroup): boolean {
        return !!this.containsProgrammingExercises!.get(exerciseGroup);
    }

    /**
     * Returns the placeholder title (i.e. the one rejected by the server) for the programming exercise
     * @param exerciseId the corresponding exercise
     */
    getBlocklistTitleOfProgrammingExercise(exerciseId: number): string {
        const title = this.titleAndShortNameOfProgrammingExercises.get(exerciseId)?.first();
        return title ? title! : ``;
    }

    /**
     * Returns the placeholder shortName (i.e. the one rejected by the server) for the programming exercise
     * @param exerciseId the corresponding exercise
     */
    getBlocklistShortNameOfProgrammingExercise(exerciseId: number): string {
        const shortName = this.titleAndShortNameOfProgrammingExercises.get(exerciseId)?.last();
        return shortName ? shortName! : ``;
    }

    /**
     * Helper method to map the Map<ExerciseGroup, Set<Exercises>> selectedExercises to an ExerciseGroup[] with Exercises[] each.
     * Called once by the parent component when the user desires to import the exam / exercise groups
     */
    public mapSelectedExercisesToExerciseGroups(): ExerciseGroup[] {
        const exerciseGroups: ExerciseGroup[] = [];
        this.selectedExercises?.forEach((value, key) => {
            if (value.size > 0) {
                key.exercises = Array.from(value.values());
                exerciseGroups.push(key);
            }
        });
        return exerciseGroups;
    }

    /**
     * Validates the Title for Programming Exercises based on the user's input
     * @param exercise the exercise to be checked
     */
    validateTitleOfProgrammingExercise(exercise: Exercise): boolean {
        return (
            !!exercise.title?.length &&
            EXERCISE_TITLE_NAME_REGEX.test(exercise.title!) &&
            !this.exercisesWithDuplicatedTitles.has(exercise.id!) &&
            (exercise.title !== this.getBlocklistTitleOfProgrammingExercise(exercise.id!) || this.getBlocklistShortNameOfProgrammingExercise(exercise.id!) === '')
        );
    }

    /**
     * Validates the Title for Programming Exercises based on the user's input
     * @param exercise the exercise to be checked
     */
    validateShortNameOfProgrammingExercise(exercise: Exercise): boolean {
        return (
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            exercise.shortName?.length! > 2 &&
            this.SHORT_NAME_REGEX.test(exercise.shortName!) &&
            !this.exercisesWithDuplicatedShortNames.has(exercise.id!) &&
            exercise.shortName !== this.getBlocklistShortNameOfProgrammingExercise(exercise.id!)
        );
    }

    /**
     * checks if there are any selected programming exercises with the same title or short name as the passed exercise
     * @param exercise      exercise we want to use to check for duplications
     * @param exerciseGroup exercise group of the exercise
     * @param checkForTitle true if the title should be checked, otherwise the short name is checked
     */
    checkForDuplicatedTitlesOrShortNamesOfProgrammingExercise(exercise: Exercise, exerciseGroup: ExerciseGroup, checkForTitle: boolean) {
        if (!this.exerciseIsSelected(exercise, exerciseGroup)) {
            return;
        }

        const duplicatesToCheck = checkForTitle ? this.exercisesWithDuplicatedTitles : this.exercisesWithDuplicatedShortNames;
        let hasDuplicate = false;
        this.selectedExercises.forEach((exerciseGroup) => {
            exerciseGroup.forEach((ex) => {
                if (ex.type === ExerciseType.PROGRAMMING && ex !== exercise) {
                    if ((checkForTitle && ex.title === exercise.title) || (!checkForTitle && ex.shortName === exercise.shortName)) {
                        hasDuplicate = true;
                        duplicatesToCheck.set(ex.id!, checkForTitle ? exercise.title! : exercise.shortName!);
                    }
                }
            });
        });

        // check if the exercise was a duplicate before
        const titleOrShortName = duplicatesToCheck.get(exercise.id!);
        if (duplicatesToCheck.delete(exercise.id!)) {
            this.removeProgrammingExerciseFromDuplicates(titleOrShortName!, checkForTitle);
        }

        if (hasDuplicate) {
            duplicatesToCheck.set(exercise.id!, checkForTitle ? exercise.title! : exercise.shortName!);
        }
    }

    /**
     * After a duplicate programming exercise's title / short name of programming exercise has been edited or a programming exercise has been unselected,
     * other duplicates need to be checked if they can be removed from the map. In case there is only one exercise with the same title / short name
     * it is no longer considered a duplicate and is removed
     * @param titleOrShortName title / short name of the exercise that was a duplicate before
     * @param checkForTitle   true if the exercise title duplication should be checked, otherwise short name duplication is checked
     */
    removeProgrammingExerciseFromDuplicates(titleOrShortName: string, checkForTitle: boolean) {
        const setToCheck = checkForTitle ? this.exercisesWithDuplicatedTitles : this.exercisesWithDuplicatedShortNames;
        const filteredKeys = Array.from(setToCheck.keys()).filter((key) => setToCheck.get(key) === titleOrShortName);

        if (filteredKeys.length <= 1) {
            filteredKeys.forEach((key) => setToCheck.delete(key));
        }
    }

    /**
     * Method to iterate over all exercise groups and exercises to validate, if the exerciseGroup.title & exercise.title & exercise.shortName are correctly set
     */
    public validateUserInput(): boolean {
        let validConfiguration = true;
        this.selectedExercises?.forEach((value, key) => {
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            if (!(key.title?.length! > 0)) {
                validConfiguration = false;
            }
            if (value.size > 0) {
                value.forEach((exercise) => {
                    if (!validConfiguration) {
                        return false;
                    }
                    if (exercise.type === ExerciseType.PROGRAMMING) {
                        validConfiguration = this.validateTitleOfProgrammingExercise(exercise) && this.validateShortNameOfProgrammingExercise(exercise);
                    } else {
                        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
                        validConfiguration = exercise.title?.length! > 0;
                    }
                });
            }
        });
        return validConfiguration;
    }
}
