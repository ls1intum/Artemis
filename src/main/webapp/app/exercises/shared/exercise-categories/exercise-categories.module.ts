import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExerciseCategoriesComponent } from 'app/exercises/shared/exercise-categories/exercise-categories.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ExerciseCategoriesComponent],
    exports: [ExerciseCategoriesComponent],
})
export class ExerciseCategoriesModule {}
