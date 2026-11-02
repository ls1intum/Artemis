import { createExerciseUpdateRoute } from 'app/exercise/shared/routes/exercise-update-route';
import { TextExerciseResolver } from 'app/text/manage/text-exercise/service/text-exercise-resolver.service';

export const textExerciseUpdateRoute = createExerciseUpdateRoute({
    loadComponent: () => import('app/text/manage/text-exercise/update/text-exercise-update.component').then((m) => m.TextExerciseUpdateComponent),
    pageTitle: 'artemisApp.textExercise.home.title',
    resolve: { textExercise: TextExerciseResolver },
});
