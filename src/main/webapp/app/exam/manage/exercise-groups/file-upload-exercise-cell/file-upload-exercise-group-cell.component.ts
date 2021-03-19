import { Component, Input } from '@angular/core';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ExerciseType } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-file-upload-exercise-group-cell',
    templateUrl: './file-upload-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
})
export class FileUploadExerciseGroupCellComponent {
    exerciseType = ExerciseType;

    @Input()
    exercise: FileUploadExercise;
}
