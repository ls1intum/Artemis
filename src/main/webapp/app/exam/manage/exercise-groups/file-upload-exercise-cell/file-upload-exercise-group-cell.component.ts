import { Component, input } from '@angular/core';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';

@Component({
    selector: 'jhi-file-upload-exercise-group-cell',
    templateUrl: './file-upload-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
})
export class FileUploadExerciseGroupCellComponent {
    exerciseType = ExerciseType;
    exercise = input.required<FileUploadExercise>();
}
