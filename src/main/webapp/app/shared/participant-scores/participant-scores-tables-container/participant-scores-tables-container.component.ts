import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-participant-scores-tables-container',
    templateUrl: './participant-scores-tables-container.component.html',
})
export class ParticipantScoresTablesContainerComponent {
    readonly round = round;

    @Input()
    isLoading: boolean;
    @Input()
    participantScores: ParticipantScoreDTO[] = [];
    @Input()
    participantScoresAverage: ParticipantScoreAverageDTO[] = [];
    @Input()
    avgScore = 0;
    @Input()
    avgRatedScore = 0;
    @Input()
    avgGrade?: string;
    @Input()
    avgRatedGrade?: string;
    @Input()
    isBonus = false;
    @Output()
    reload = new EventEmitter<void>();

    mode: 'individual' | 'average' = 'individual';
}
