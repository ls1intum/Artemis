import { NgModule } from '@angular/core';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMathExerciseModule } from 'app/exercises/math/manage/math-exercise.module';

@NgModule({
    imports: [ArtemisMathExerciseModule, ArtemisSharedLibsModule, ArtemisSharedComponentModule, ArtemisSharedModule],
})
export class ArtemisMathExerciseManagementModule {}
