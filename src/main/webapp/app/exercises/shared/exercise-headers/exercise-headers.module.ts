import { NgModule } from '@angular/core';
import { HeaderExercisePageWithDetailsComponent } from './header-exercise-page-with-details.component';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, SubmissionResultStatusModule, HeaderExercisePageWithDetailsComponent, HeaderParticipationPageComponent],
    exports: [HeaderExercisePageWithDetailsComponent, HeaderParticipationPageComponent],
})
export class ArtemisHeaderExercisePageWithDetailsModule {}
