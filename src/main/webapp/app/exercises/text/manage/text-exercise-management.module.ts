import { NgModule } from '@angular/core';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';

import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisTextExerciseModule, ArtemisSharedComponentModule],
})
export class ArtemisTextExerciseManagementModule {}
