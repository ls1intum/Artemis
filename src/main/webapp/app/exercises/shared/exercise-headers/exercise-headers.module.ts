import { NgModule } from '@angular/core';
import { HeaderExercisePageWithDetailsComponent } from './header-exercise-page-with-details.component';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { DifficultyBadgeComponent } from './difficulty-badge.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, SubmissionResultStatusModule],
    declarations: [HeaderExercisePageWithDetailsComponent, HeaderParticipationPageComponent, DifficultyBadgeComponent, IncludedInScoreBadgeComponent],
    exports: [HeaderExercisePageWithDetailsComponent, HeaderParticipationPageComponent, DifficultyBadgeComponent, IncludedInScoreBadgeComponent],
})
export class ArtemisHeaderExercisePageWithDetailsModule {}
