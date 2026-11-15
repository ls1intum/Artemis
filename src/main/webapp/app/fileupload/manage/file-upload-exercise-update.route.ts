import { createExerciseUpdateRoute } from 'app/exercise/shared/routes/exercise-update-route';
import { FileUploadExerciseManagementResolve } from 'app/fileupload/manage/services/file-upload-exercise-management-resolve.service';

export const fileUploadExerciseUpdateRoute = createExerciseUpdateRoute({
    loadComponent: () => import('app/fileupload/manage/update/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
    pageTitle: 'artemisApp.fileUploadExercise.home.title',
    resolve: { fileUploadExercise: FileUploadExerciseManagementResolve },
});
