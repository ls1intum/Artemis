import { Component, input } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Observable } from 'rxjs';
import { JhiConnectionStatusComponent } from 'app/shared-ui/connection-status/connection-status.component';
import { TeamStudentsOnlineListComponent } from './team-students-online-list.component';

@Component({
    selector: 'jhi-team-participate-info-box',
    templateUrl: './team-participate-info-box.component.html',
    styleUrls: ['./team-participate-info-box.component.scss'],
    imports: [JhiConnectionStatusComponent, TeamStudentsOnlineListComponent],
})
export class TeamParticipateInfoBoxComponent {
    readonly exercise = input.required<Exercise>();
    readonly participation = input.required<StudentParticipation>();
    readonly stickyEnabled = input(true);
    readonly dockedToLeftSide = input(false);
    readonly dockedToRightSide = input(false);
    readonly typing$ = input.required<Observable<any>>();
}
