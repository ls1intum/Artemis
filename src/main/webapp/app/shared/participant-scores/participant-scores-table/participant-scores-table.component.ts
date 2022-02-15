import { Component, Input } from '@angular/core';
import { ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-participant-scores-table',
    templateUrl: './participant-scores-table.component.html',
})
export class ParticipantScoresTableComponent {
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;

    @Input()
    participantScores: ParticipantScoreDTO[] = [];
    @Input()
    isLoading = false;
    @Input()
    course?: Course;

    extractParticipantName = (participantScoreDTO: ParticipantScoreDTO) => {
        return participantScoreDTO.userName ? String(participantScoreDTO.userName) : String(participantScoreDTO.teamName);
    };
}
