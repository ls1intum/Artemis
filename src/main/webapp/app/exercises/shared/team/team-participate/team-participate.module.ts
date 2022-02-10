import { NgModule } from '@angular/core';
import { TeamParticipateInfoBoxComponent } from 'app/exercises/shared/team/team-participate/team-participate-info-box.component';
import { TeamStudentsOnlineListComponent } from 'app/exercises/shared/team/team-participate/team-students-online-list.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TeamStudentsListComponent } from 'app/exercises/shared/team/team-participate/team-students-list.component';
import { RouterModule } from '@angular/router';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule],
    declarations: [TeamParticipateInfoBoxComponent, TeamStudentsOnlineListComponent, TeamStudentsListComponent],
    exports: [TeamParticipateInfoBoxComponent, TeamStudentsOnlineListComponent, TeamStudentsListComponent],
})
export class ArtemisTeamParticipeModule {}
