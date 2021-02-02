import { Component, Input } from '@angular/core';
import { ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';

@Component({
    selector: 'jhi-participant-scores-table',
    templateUrl: './participant-scores-table.component.html',
    styles: [],
})
export class ParticipantScoresTableComponent {
    @Input()
    participantScores: ParticipantScoreDTO[] = [];

    @Input()
    isLoading = false;
    extractParticipantName = (participantScoreDTO: ParticipantScoreDTO) => {
        if (participantScoreDTO.userName) {
            return `${participantScoreDTO.userName}`;
        } else {
            return `${participantScoreDTO.teamName}`;
        }
    };
}
