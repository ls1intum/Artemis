import { Component, input } from '@angular/core';
import { ExerciseType } from 'app/entities/exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

@Component({
    selector: 'jhi-file-upload-exercise-group-cell',
    templateUrl: './file-upload-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
})
export class FileUploadExerciseGroupCellComponent {
    exerciseType = ExerciseType;
    exercise = input.required<FileUploadExercise>();
}
