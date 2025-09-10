import { Component, Input } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Observable } from 'rxjs';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { TeamStudentsOnlineListComponent } from './team-students-online-list.component';

@Component({
    selector: 'jhi-team-participate-info-box',
    templateUrl: './team-participate-info-box.component.html',
    styleUrls: ['./team-participate-info-box.component.scss'],
    imports: [JhiConnectionStatusComponent, TeamStudentsOnlineListComponent],
})
export class TeamParticipateInfoBoxComponent {
    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;
    @Input() stickyEnabled = true;
    @Input() dockedToLeftSide = false;
    @Input() dockedToRightSide = false;
    @Input() typing$: Observable<any>;
}
