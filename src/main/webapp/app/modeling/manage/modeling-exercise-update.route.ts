import { createExerciseUpdateRoute } from 'app/exercise/shared/routes/exercise-update-route';
import { ModelingExerciseResolver } from 'app/modeling/manage/services/modeling-exercise-resolver.service';

export const modelingExerciseUpdateRoute = createExerciseUpdateRoute({
    loadComponent: () => import('app/modeling/manage/update/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
    pageTitle: 'artemisApp.modelingExercise.home.title',
    resolve: { modelingExercise: ModelingExerciseResolver },
});
