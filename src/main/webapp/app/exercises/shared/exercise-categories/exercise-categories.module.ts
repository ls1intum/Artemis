import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExerciseCategoriesComponent } from 'app/exercises/shared/exercise-categories/exercise-categories.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { MockRouterLinkDirective } from '../../../../../../test/javascript/spec/helpers/mocks/directive/mock-router-link.directive';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisHeaderExercisePageWithDetailsModule, ArtemisSharedComponentModule],
    declarations: [ExerciseCategoriesComponent, MockRouterLinkDirective],
    exports: [ExerciseCategoriesComponent],
})
export class ExerciseCategoriesModule {}
