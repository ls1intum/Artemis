import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExampleSubmissionsComponent } from 'app/exercises/shared/example-submission/example-submissions.component';
import { textExerciseRoute as textExerciseRoutes } from 'app/exercises/text/manage/text-exercise/text-exercise.route';
import { routes as modelingExerciseRoutes } from 'app/exercises/modeling/manage/modeling-exercise.route';
import { ExampleSubmissionImportComponent } from './example-submission-import/example-submission-import.component';

const ENTITY_STATES = [...textExerciseRoutes, ...modelingExerciseRoutes];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [ExampleSubmissionsComponent, ExampleSubmissionImportComponent],
})
export class ExampleSubmissionsModule {}
