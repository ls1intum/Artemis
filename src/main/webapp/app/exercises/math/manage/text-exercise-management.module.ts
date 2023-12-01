import { NgModule } from '@angular/core';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisTextExerciseModule, ArtemisSharedLibsModule, ArtemisSharedComponentModule, ArtemisSharedModule],
})
export class ArtemisTextExerciseManagementModule {}
