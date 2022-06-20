import { Component, Input, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { faCheckDouble, faFileUpload, faKeyboard, faProjectDiagram, faX, faCheck, faFont } from '@fortawesome/free-solid-svg-icons';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'jhi-exam-exercise-import',
    templateUrl: './exam-exercise-import.component.html',
    styleUrls: ['./exam-exercise-import.component.scss'],
})
export class ExamExerciseImportComponent implements OnInit {
    @Input() exam: Exam;
    // Map to determine, which exercises should be imported alongside an exam
    @Input() selectedExercises?: Map<ExerciseGroup, Set<Exercise>>;
    // The shortName of the course to be displayed next to the shortName of a Programming Exercise to form the vcs key
    @Input() courseShortName?: string;
    // Expose enums to the template
    exerciseType = ExerciseType;

    // Icons
    faCheckDouble = faCheckDouble;
    faFileUpload = faFileUpload;
    faProjectDiagram = faProjectDiagram;
    faKeyboard = faKeyboard;
    faFont = faFont;
    faCheck = faCheck;
    faX = faX;

    constructor() {}

    ngOnInit(): void {
        if (!this.courseShortName) {
            this.courseShortName = this.exam.course!.shortName;
        }
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
