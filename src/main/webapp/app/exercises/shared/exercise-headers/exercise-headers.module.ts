import { NgModule } from '@angular/core';
import { HeaderExercisePageWithDetailsComponent } from './header-exercise-page-with-details.component';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { DifficultyBadgeComponent } from './difficulty-badge.component';
import { MomentModule } from 'ngx-moment';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';

@NgModule({
    imports: [MomentModule, ArtemisSharedModule, ArtemisResultModule, ArtemisSharedComponentModule],
    declarations: [HeaderExercisePageWithDetailsComponent, HeaderParticipationPageComponent, DifficultyBadgeComponent, IncludedInScoreBadgeComponent],
    exports: [HeaderExercisePageWithDetailsComponent, HeaderParticipationPageComponent, DifficultyBadgeComponent, IncludedInScoreBadgeComponent],
})
export class ArtemisHeaderExercisePageWithDetailsModule {}
