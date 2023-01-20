import { Component, Input, OnInit } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

interface Badge {
    class: string;
    text: string;
    tooltip: string;
}
type Status = 'graded' | 'ungraded' | 'testRun' | undefined;

@Component({
    selector: 'jhi-result-badges',
    templateUrl: './result-badges.component.html',
})
export class ResultBadgesComponent implements OnInit {
    @Input() result: Result;

    badges: Badge[];

    ngOnInit(): void {
        const statuses = [this.evaluateIsGraded(this.result), this.evaluateIsTestRun(this.result.participation!)];
        this.badges = statuses.map(this.getBadge).filter((b) => b !== undefined) as Badge[];
    }

    private evaluateIsGraded(result: Result): Status {
        return result.rated ? 'graded' : 'ungraded';
    }

    private evaluateIsTestRun(participation: Participation): Status {
        if (participation.type === ParticipationType.STUDENT || participation.type === ParticipationType.PROGRAMMING) {
            const studentParticipation = participation as StudentParticipation;
            if (studentParticipation.testRun) {
                return 'testRun';
            }
        }
    }

    private getBadge(status: Status): Badge | undefined {
        if (status) {
            return {
                graded: { class: 'bg-success', text: 'artemisApp.result.graded', tooltip: 'artemisApp.result.gradedTooltip' },
                ungraded: { class: 'bg-info', text: 'artemisApp.result.notGraded', tooltip: 'artemisApp.result.notGradedTooltip' },
                testRun: { class: 'bg-secondary', text: 'artemisApp.result.practice', tooltip: 'artemisApp.result.practiceTooltip' },
            }[status];
        }
    }
}
