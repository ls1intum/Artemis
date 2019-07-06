import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { TutorLeaderboardComponent } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.component';
import { SortByModule } from 'app/components/pipes';
import { ArTEMiSSharedModule } from 'app/shared';

@NgModule({
    imports: [BrowserModule, ArTEMiSSharedModule, SortByModule, RouterModule.forChild([])],
    declarations: [TutorLeaderboardComponent],
    exports: [TutorLeaderboardComponent],
    entryComponents: [],
    providers: [],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSTutorLeaderboardModule {}
