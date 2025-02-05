import { NgModule } from '@angular/core';
import { TeamParticipateInfoBoxComponent } from 'app/exercises/shared/team/team-participate/team-participate-info-box.component';
import { TeamStudentsOnlineListComponent } from 'app/exercises/shared/team/team-participate/team-students-online-list.component';

import { TeamStudentsListComponent } from 'app/exercises/shared/team/team-participate/team-students-list.component';
import { RouterModule } from '@angular/router';

@NgModule({
    imports: [RouterModule, TeamParticipateInfoBoxComponent, TeamStudentsOnlineListComponent, TeamStudentsListComponent],
    exports: [TeamParticipateInfoBoxComponent, TeamStudentsOnlineListComponent, TeamStudentsListComponent],
})
export class ArtemisTeamParticipeModule {}
