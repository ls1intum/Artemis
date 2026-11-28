import { ResolveFn, Route } from '@angular/router';
import { programmingExerciseUpdateRoute } from 'app/programming/manage/programming-exercise-shared.route';
import type { ProgrammingExerciseImport } from './programming-exercise-extensions';

export function externalProgrammingExerciseImportRoute(resolve: ResolveFn<ProgrammingExerciseImport>): Route {
    const route = programmingExerciseUpdateRoute('', 'artemisApp.programmingExercise.home.importLabel');
    route.resolve = { externalImport: resolve };
    route.runGuardsAndResolvers = 'paramsOrQueryParamsChange';
    return route;
}
