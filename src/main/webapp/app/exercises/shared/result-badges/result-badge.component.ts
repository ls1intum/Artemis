import { Component, Input, OnInit } from '@angular/core';
import { Result, ResultStatus } from 'app/entities/result.model';
import { Participation } from 'app/entities/participation/participation.model';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { evaluateStatus } from 'app/exercises/shared/result/result.utils';

interface Badge {
    class: string;
    text: string;
    tooltip: string;
}

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
        const status = evaluateStatus(this.result, this.participation);
        this.badge = this.getBadge(status);
        this.exercise = this.participation.exercise!;
    }

    private getBadge(status: ResultStatus): Badge {
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
