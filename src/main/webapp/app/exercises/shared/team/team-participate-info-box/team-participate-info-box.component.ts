import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { StickyStatus } from '@w11k/angular-sticky-things';

@Component({
    selector: 'jhi-team-participate-info-box',
    templateUrl: './team-participate-info-box.component.html',
    styleUrls: ['./team-participate-info-box.component.scss'],
})
export class TeamParticipateInfoBoxComponent {
    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;
    @Input() stickyEnabled = true;
    @Input() bounded = true;

    stickyStatus: StickyStatus;

    onStickyStatusChange(stickyStatus: StickyStatus) {
        this.stickyStatus = stickyStatus;
    }

    get isSticky(): boolean {
        return this.stickyStatus?.isSticky;
    }
}
