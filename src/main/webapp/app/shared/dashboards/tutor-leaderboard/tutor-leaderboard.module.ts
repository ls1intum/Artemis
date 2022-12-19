import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild([])],
    declarations: [TutorLeaderboardComponent],
    exports: [TutorLeaderboardComponent],
})
export class ArtemisTutorLeaderboardModule {}
