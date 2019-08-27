import { of } from 'rxjs';
import { FileUploadExercise } from 'app/entities/file-upload-exercise/file-upload-exercise.model';

export class MockFileUploadExerciseService {
    create = (fileUploadExercise: FileUploadExercise) => of();
    update = (fileUploadExercise: FileUploadExercise, exerciseId: number, req?: any) => of();
    find = (id: number) => of();
    query = (req?: any) => of();
    delete = (id: number) => of();
}
