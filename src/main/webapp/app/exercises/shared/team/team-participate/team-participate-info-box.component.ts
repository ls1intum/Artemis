import { Component, Input } from '@angular/core';
import { Observable } from 'rxjs';

import { Exercise } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

@Component({
    selector: 'jhi-team-participate-info-box',
    templateUrl: './team-participate-info-box.component.html',
    styleUrls: ['./team-participate-info-box.component.scss'],
})
export class TeamParticipateInfoBoxComponent {
    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;
    @Input() stickyEnabled = true;
    @Input() dockedToLeftSide = false;
    @Input() dockedToRightSide = false;
    @Input() typing$: Observable<any>;
}
