import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExerciseCategoriesComponent } from 'app/exercises/shared/exercise-categories/exercise-categories.component';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule],
    declarations: [ExerciseCategoriesComponent],
    exports: [ExerciseCategoriesComponent],
})
export class ExerciseCategoriesModule {}
