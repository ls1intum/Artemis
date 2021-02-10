import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';

@Component({
    selector: 'jhi-participant-scores-tables-container',
    templateUrl: './participant-scores-tables-container.component.html',
})
export class ParticipantScoresTablesContainerComponent {
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
    @Output()
    reload = new EventEmitter<void>();

    mode: 'individual' | 'average' = 'individual';
}
