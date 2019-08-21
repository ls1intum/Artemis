import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { TutorLeaderboardComponent } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.component';
import { SortByModule } from 'app/components/pipes';
import { ArtemisSharedModule } from 'app/shared';

@NgModule({
    imports: [BrowserModule, ArtemisSharedModule, SortByModule, RouterModule.forChild([])],
    declarations: [TutorLeaderboardComponent],
    exports: [TutorLeaderboardComponent],
    entryComponents: [],
    providers: [],
})
export class ArtemisTutorLeaderboardModule {}
