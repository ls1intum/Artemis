import { createExerciseUpdateRoute } from 'app/exercise/shared/routes/exercise-update-route';

export const quizExerciseUpdateRoute = createExerciseUpdateRoute({
    loadComponent: () => import('app/quiz/manage/update/quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
    pageTitle: 'artemisApp.quizExercise.home.title',
});
