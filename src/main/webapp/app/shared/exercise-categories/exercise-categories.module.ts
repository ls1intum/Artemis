import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ExerciseCategoriesComponent } from 'app/shared/exercise-categories/exercise-categories.component';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule, ArtemisSharedComponentModule],
    declarations: [ExerciseCategoriesComponent],
    exports: [ExerciseCategoriesComponent],
})
export class ExerciseCategoriesModule {}
