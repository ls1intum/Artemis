import { NgModule } from '@angular/core';
import { HeaderExercisePageWithDetailsComponent } from './header-exercise-page-with-details.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { DifficultyBadgeComponent } from './difficulty-badge.component';
import { MomentModule } from 'ngx-moment';

@NgModule({
    imports: [MomentModule, ArtemisSharedModule],
    declarations: [HeaderExercisePageWithDetailsComponent, DifficultyBadgeComponent],
    exports: [HeaderExercisePageWithDetailsComponent, DifficultyBadgeComponent],
})
export class ArtemisHeaderExercisePageWithDetailsModule {}
