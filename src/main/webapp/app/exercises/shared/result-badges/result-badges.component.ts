import { Component, Input, OnInit } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

interface Badge {
    class: string;
    text: string;
    tooltip: string;
}
type Status = 'graded' | 'ungraded' | 'testRun';

@Component({
    selector: 'jhi-result-badges',
    templateUrl: './result-badges.component.html',
})
export class ResultBadgesComponent implements OnInit {
    @Input() result: Result;
    @Input() participation: Participation;

    badge: Badge;

    ngOnInit(): void {
        const status = this.evaluateStatus(this.result, this.participation);
        this.badge = this.getBadge(status);
    }

    private evaluateStatus(result: Result, participation: Participation) {
        if (participation.type === ParticipationType.STUDENT || participation.type === ParticipationType.PROGRAMMING) {
            const studentParticipation = participation as StudentParticipation;
            if (studentParticipation.testRun) {
                return 'testRun';
            }
        }

        return result.rated ? 'graded' : 'ungraded';
    }

    private getBadge(status: Status): Badge {
        return {
            graded: { class: 'bg-success', text: 'artemisApp.result.graded', tooltip: 'artemisApp.result.gradedTooltip' },
            ungraded: { class: 'bg-info', text: 'artemisApp.result.notGraded', tooltip: 'artemisApp.result.notGradedTooltip' },
            testRun: { class: 'bg-secondary', text: 'artemisApp.result.practice', tooltip: 'artemisApp.result.practiceTooltip' },
        }[status];
    }
}
