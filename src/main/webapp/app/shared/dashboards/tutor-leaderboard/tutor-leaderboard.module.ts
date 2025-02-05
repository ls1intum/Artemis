import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';

@NgModule({
    imports: [RouterModule.forChild([]), TutorLeaderboardComponent],
    exports: [TutorLeaderboardComponent],
})
export class ArtemisTutorLeaderboardModule {}
