import { Component, Input } from '@angular/core';
import { ParticipantScoreAverageDTO } from 'app/shared/participant-scores/participant-scores.service';
import { round } from 'app/shared/util/utils';
import { BaseEntity } from 'app/shared/model/base-entity';

@Component({
    selector: 'jhi-participant-scores-average-table',
    templateUrl: './participant-scores-average-table.component.html',
})
export class ParticipantScoresAverageTableComponent {
    readonly round = round;
    @Input()
    participantAverageScores: ParticipantScoreAverageDTO[] = [];
    @Input()
    isLoading = false;
    @Input()
    isBonus = false;

    extractParticipantName = (participantScoreAverageDTO: BaseEntity) => {
        const castedDTO = participantScoreAverageDTO as ParticipantScoreAverageDTO;
        return castedDTO.userName ? String(castedDTO.userName) : String(castedDTO.teamName);
    };
}
