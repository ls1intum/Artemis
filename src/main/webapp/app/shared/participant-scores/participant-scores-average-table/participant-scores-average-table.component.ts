import { Component, Input } from '@angular/core';
import { ParticipantScoreAverageDTO } from 'app/shared/participant-scores/participant-scores.service';

@Component({
    selector: 'jhi-participant-scores-average-table',
    templateUrl: './participant-scores-average-table.component.html',
})
export class ParticipantScoresAverageTableComponent {
    @Input()
    participantAverageScores: ParticipantScoreAverageDTO[] = [];
    @Input()
    isLoading = false;

    extractParticipantName = (participantScoreAverageDTO: ParticipantScoreAverageDTO) => {
        if (participantScoreAverageDTO.userName) {
            return `${participantScoreAverageDTO.userName}`;
        }
        return `${participantScoreAverageDTO.teamName}`;
    };
}
