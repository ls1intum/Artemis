import { Component, Input, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { faCheckDouble, faFileUpload, faFont, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { shortNamePattern } from 'app/shared/constants/input.constants';

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
    // Map / Blocklist with the title and shortName of the programming exercises, that have been either rejected by the server or must be changed because the exam is imported into the same course
    titleAndShortNameOfProgrammingExercises = new Map<number, String[]>();
    // Expose enums to the template
    exerciseType = ExerciseType;
    // Map to determine, if an exercise group contains at least one programming exercise.
    // I.E. the short name must be displayed in the corresponding table
    containsProgrammingExercises = new Map<ExerciseGroup, boolean>();

    // Patterns
    // length of < 3 is also accepted in order to provide more accurate validation error messages
    readonly shortNamePattern = RegExp('(^(?![\\s\\S]))|^[a-zA-Z][a-zA-Z0-9]*$|' + shortNamePattern); // must start with a letter and cannot contain special characters
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
    updateMapsAfterRejectedImport() {
        this.titleAndShortNameOfProgrammingExercises.clear();
        this.initializeTitleAndShortNameMap();
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
        // Initialize containsProgrammingExercises
        this.exam.exerciseGroups!.forEach((exerciseGroup) => {
            const hasProgrammingExercises = !!exerciseGroup.exercises?.some((value) => value.type === ExerciseType.PROGRAMMING);
            this.containsProgrammingExercises.set(exerciseGroup, hasProgrammingExercises);
            // In case of a rejected import, we can delete programming exercises with a title from the Map / blocklist, as those were not rejected by the server.
            exerciseGroup.exercises?.forEach((exercise) => {
                if (exercise.type === ExerciseType.PROGRAMMING && exercise.title) {
                    this.titleAndShortNameOfProgrammingExercises.delete(exercise.id!);
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
        } else {
            this.selectedExercises!.get(exerciseGroup)!.add(exercise);
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
    getBlocklistTitleOfProgrammingExercise(exerciseId: number): String {
        const title = this.titleAndShortNameOfProgrammingExercises.get(exerciseId)?.first();
        return title ? title! : ``;
    }

    /**
     * Returns the placeholder shortName (i.e. the one rejected by the server) for the programming exercise
     * @param exerciseId the corresponding exercise
     */
    getBlocklistShortNameOfProgrammingExercise(exerciseId: number): String {
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
        return exercise.title?.length! > 0 && this.titleNamePattern.test(exercise.title!) && exercise.title !== this.getBlocklistTitleOfProgrammingExercise(exercise.id!);
    }

    /**
     * Validates the Title for Programming Exercises based on the user's input
     * @param exercise the exercise to be checked
     */
    validateShortNameOfProgrammingExercise(exercise: Exercise): boolean {
        return (
            exercise.shortName?.length! > 2 &&
            this.shortNamePattern.test(exercise.shortName!) &&
            exercise.shortName !== this.getBlocklistShortNameOfProgrammingExercise(exercise.id!)
        );
    }

    /**
     * Method to iterate over all exercise groups and exercises to validate, if the exerciseGroup.title & exercise.title & exercise.shortName are correctly set
     */
    public validateUserInput(): boolean {
        let validConfiguration = true;
        this.selectedExercises?.forEach((value, key) => {
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
