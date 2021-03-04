import { Component, Input } from '@angular/core';
import { ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';

@Component({
    selector: 'jhi-participant-scores-table',
    templateUrl: './participant-scores-table.component.html',
})
export class ParticipantScoresTableComponent {
    @Input()
    participantScores: ParticipantScoreDTO[] = [];

    @Input()
    isLoading = false;
    extractParticipantName = (participantScoreDTO: ParticipantScoreDTO) => {
        return participantScoreDTO.userName ? String(participantScoreDTO.userName) : String(participantScoreDTO.teamName);
    };
}
