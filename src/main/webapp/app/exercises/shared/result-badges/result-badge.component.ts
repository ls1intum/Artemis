import { Component, Input, OnInit } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { resultIsPreliminary } from 'app/exercises/shared/result/result.utils';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

interface Badge {
    class: string;
    text: string;
    tooltip: string;
}
type Status = 'graded' | 'ungraded' | 'testRun' | 'preliminary';

@Component({
    selector: 'jhi-result-badge',
    templateUrl: './result-badge.component.html',
})
export class ResultBadgeComponent implements OnInit {
    @Input() result: Result;
    @Input() participation: Participation;
    exercise: Exercise;

    badge: Badge;

    ngOnInit(): void {
        const status = this.evaluateStatus(this.result, this.participation);
        this.badge = this.getBadge(status);
        this.exercise = this.participation.exercise!;
    }

    private evaluateStatus(result: Result, participation: Participation) {
        if (resultIsPreliminary(result)) {
            return 'preliminary';
        }

        if (participation.type === ParticipationType.STUDENT || participation.type === ParticipationType.PROGRAMMING) {
            const studentParticipation = participation as StudentParticipation;
            if (studentParticipation.testRun) {
                return 'testRun';
            }
        }

        return result.rated ? 'graded' : 'ungraded';
    }

    private getBadge(status: Status): Badge {
        if (status === 'preliminary') {
            const programmingExercise = this.exercise as ProgrammingExercise;
            return {
                class: 'bg-secondary',
                text: 'artemisApp.result.preliminary',
                tooltip:
                    programmingExercise?.assessmentType !== AssessmentType.AUTOMATIC ? 'artemisApp.result.preliminaryTooltipSemiAutomatic' : 'artemisApp.result.preliminaryTooltip',
            };
        }

        return {
            graded: { class: 'bg-success', text: 'artemisApp.result.graded', tooltip: 'artemisApp.result.gradedTooltip' },
            ungraded: { class: 'bg-info', text: 'artemisApp.result.notGraded', tooltip: 'artemisApp.result.notGradedTooltip' },
            testRun: { class: 'bg-secondary', text: 'artemisApp.result.practice', tooltip: 'artemisApp.result.practiceTooltip' },
        }[status];
    }
}
