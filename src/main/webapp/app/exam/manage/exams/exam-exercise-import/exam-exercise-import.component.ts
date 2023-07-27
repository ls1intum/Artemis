import { Component, Input, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { faCheckDouble, faFileUpload, faFont, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { SHORT_NAME_PATTERN } from 'app/shared/constants/input.constants';

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

    exercisesWithDuplicatedTitles = new Set<Exercise>();
    exercisesWithDuplicatedShortNames = new Set<Exercise>();

    // Expose enums to the template
    exerciseType = ExerciseType;
    // Map to determine, if an exercise group contains at least one programming exercise.
    // I.E. the short name must be displayed in the corresponding table
    containsProgrammingExercises = new Map<ExerciseGroup, boolean>();

    // Patterns
    // length of < 3 is also accepted in order to provide more accurate validation error messages
    readonly shortNamePattern = RegExp('(^(?![\\s\\S]))|^[a-zA-Z][a-zA-Z0-9]*$|' + SHORT_NAME_PATTERN); // must start with a letter and cannot contain special characters
    readonly titleNamePattern = RegExp('^[a-zA-Z0-9-_ ]+'); // must only contain alphanumeric characters, or whitespaces, or '_' or '-'

    // Icons
    faCheckDouble = faCheckDouble;
    faFileUpload = faFileUpload;
    faProjectDiagram = faProjectDiagram;
    faKeyboard = faKeyboard;
    faFont = faFont;

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
                    if (!duplicated.has(exercise.title)) {
                        duplicated.add(exercise.title);
                        this.titleAndShortNameOfProgrammingExercises.delete(exercise.id!);
                    } else {
                        this.titleAndShortNameOfProgrammingExercises.set(exercise.id!, [exercise.title!, exercise.shortName!]);
                        exercise.title = '';
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
            //the title, short name of the exercise is no longer considered to be duplicated. In case it was duplicated
            //before, the set with duplicates has to be updated (checked again if any objects can be removed from the set)
            if (this.exercisesWithDuplicatedTitles.delete(exercise)) {
                this.exercisesWithDuplicatedTitles.forEach((ex) => this.checkForDuplicatedTitlesOrShortNames(ex, true));
            }

            if (this.exercisesWithDuplicatedShortNames.delete(exercise)) {
                this.exercisesWithDuplicatedShortNames.forEach((ex) => this.checkForDuplicatedTitlesOrShortNames(ex, false));
            }
        } else {
            this.selectedExercises!.get(exerciseGroup)!.add(exercise);
            this.checkForDuplicatedTitlesOrShortNames(exercise, true);
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
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            exercise.title?.length! > 0 && this.titleNamePattern.test(exercise.title!) && !this.exercisesWithDuplicatedTitles.has(exercise)
        );
    }

    /**
     * checks if the exercise is selected and checks for duplicated titles or short names
     * @param exercise      the exercise we want to check if it has duplicates
     * @param checkForTitle true if duplicated titles should be checked otherwise the short names are checked
     * @param exerciseGroup exercise group of the exercise
     */
    checkIfExerciseSelectedAndDuplicates(exercise: Exercise, checkForTitle: boolean, exerciseGroup: ExerciseGroup) {
        if (!this.exerciseIsSelected(exercise, exerciseGroup)) {
            return;
        }
        this.checkForDuplicatedTitlesOrShortNames(exercise, checkForTitle);
    }

    /**
     * checks if there are any selected exercises with the same title or short name as the passed exercise
     * @param exercise     exercise we want to use to check for duplications
     * @param checkForTitle true if the title should be checked, otherwise the short name is checked
     */
    checkForDuplicatedTitlesOrShortNames(exercise: Exercise, checkForTitle: boolean) {
        let hasDuplicate = false;
        this.selectedExercises.forEach((exerciseGroup) => {
            exerciseGroup.forEach((ex) => {
                if (ex.type == ExerciseType.PROGRAMMING && ex !== exercise) {
                    if (checkForTitle && ex.title === exercise.title) {
                        hasDuplicate = true;
                        this.exercisesWithDuplicatedTitles.add(ex);
                    } else if (ex.shortName === exercise.shortName) {
                        hasDuplicate = true;
                        this.exercisesWithDuplicatedShortNames.add(ex);
                    }
                }
            });
        });

        if (hasDuplicate) {
            if (checkForTitle) {
                this.exercisesWithDuplicatedTitles.add(exercise);
            } else {
                this.exercisesWithDuplicatedShortNames.add(exercise);
            }
        } else {
            if (checkForTitle && this.exercisesWithDuplicatedTitles.delete(exercise)) {
                //if the exercise had a duplicated title before we should check other exercises for duplicated titles and update them
                this.exercisesWithDuplicatedTitles.forEach((ex) => this.checkForDuplicatedTitlesOrShortNames(ex, true));
            } else if (this.exercisesWithDuplicatedShortNames.delete(exercise)) {
                this.exercisesWithDuplicatedShortNames.forEach((ex) => this.checkForDuplicatedTitlesOrShortNames(ex, false));
            }
        }
    }

    /**
     * Validates the Title for Programming Exercises based on the user's input
     * @param exercise the exercise to be checked
     */
    validateShortNameOfProgrammingExercise(exercise: Exercise): boolean {
        return (
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            exercise.shortName?.length! > 2 && this.shortNamePattern.test(exercise.shortName!) && !this.exercisesWithDuplicatedShortNames.has(exercise)
        );
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

    /**
     * Get an icon for the type of the given exercise.
     * @param exercise {Exercise}
     */
    getExerciseIcon(exercise: Exercise): IconProp {
        switch (exercise.type) {
            case ExerciseType.QUIZ:
                return this.faCheckDouble;
            case ExerciseType.FILE_UPLOAD:
                return this.faFileUpload;
            case ExerciseType.MODELING:
                return this.faProjectDiagram;
            case ExerciseType.PROGRAMMING:
                return this.faKeyboard;
            default:
                return this.faFont;
        }
    }
}
