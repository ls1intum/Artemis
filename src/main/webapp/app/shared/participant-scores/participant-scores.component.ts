import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';

@Component({
    selector: 'jhi-participant-scores',
    templateUrl: './participant-scores.component.html',
    styles: [],
})
export class ParticipantScoresComponent {
    @Output()
    reload = new EventEmitter<void>();

    @Input()
    participantScores: ParticipantScoreDTO[] = [];
    @Input()
    avgScore = 0;
    @Input()
    avgRatedScore = 0;

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
