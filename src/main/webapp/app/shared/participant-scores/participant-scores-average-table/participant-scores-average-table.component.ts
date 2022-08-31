import { Component, Input } from '@angular/core';
import { ParticipantScoreAverageDTO } from 'app/shared/participant-scores/participant-scores.service';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-participant-scores-average-table',
    templateUrl: './participant-scores-average-table.component.html',
})
export class ParticipantScoresAverageTableComponent {
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    @Input()
    participantAverageScores: ParticipantScoreAverageDTO[] = [];
    @Input()
    isLoading = false;
    @Input()
    isBonus = false;
    @Input()
    course?: Course;

    extractParticipantName = (participantScoreAverageDTO: BaseEntity) => {
        const castedDTO = participantScoreAverageDTO as ParticipantScoreAverageDTO;
        return castedDTO.name!;
    };
}
