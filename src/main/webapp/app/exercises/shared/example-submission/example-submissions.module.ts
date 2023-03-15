import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ExampleSubmissionImportComponent } from './example-submission-import/example-submission-import.component';
import { routes as modelingExerciseRoutes } from 'app/exercises/modeling/manage/modeling-exercise.route';
import { ExampleSubmissionsComponent } from 'app/exercises/shared/example-submission/example-submissions.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { textExerciseRoute as textExerciseRoutes } from 'app/exercises/text/manage/text-exercise/text-exercise.route';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...textExerciseRoutes, ...modelingExerciseRoutes];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), ArtemisSharedComponentModule, ArtemisResultModule, SubmissionResultStatusModule],
    declarations: [ExampleSubmissionsComponent, ExampleSubmissionImportComponent],
})
export class ExampleSubmissionsModule {}
