import { of } from 'rxjs';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';

export const fileUploadExercise = new FileUploadExercise(undefined, undefined);
fileUploadExercise.id = 2;
fileUploadExercise.title = 'some title';
fileUploadExercise.maxPoints = 20;
fileUploadExercise.filePattern = 'pdf,png';
fileUploadExercise.problemStatement = 'Example problem statement';
fileUploadExercise.course = new Course();
fileUploadExercise.categories = [];

export class MockFileUploadExerciseService {
    create = (fileUploadExerciseParam: FileUploadExercise) => of();
    update = (fileUploadExerciseParam: FileUploadExercise, exerciseId: number, req?: any) => of();
    find = (fileUploadExerciseId: number) => of(new HttpResponse({ body: fileUploadExercise }));
    query = (req?: any) => of();
    delete = (fileUploadExerciseId: number) => of();
}
