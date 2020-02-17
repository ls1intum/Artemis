import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TutorLeaderboardComponent } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SortByModule } from 'app/components/pipes/sort-by.module';

@NgModule({
    imports: [ArtemisSharedModule, SortByModule, RouterModule.forChild([])],
    declarations: [TutorLeaderboardComponent],
    exports: [TutorLeaderboardComponent],
    entryComponents: [],
    providers: [],
})
export class ArtemisTutorLeaderboardModule {}
