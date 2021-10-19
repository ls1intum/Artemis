import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';
import { roundScoreSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-participant-scores-tables-container',
    templateUrl: './participant-scores-tables-container.component.html',
})
export class ParticipantScoresTablesContainerComponent {
    readonly roundScoreSpecifiedByCourseSettings = roundScoreSpecifiedByCourseSettings;

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
    @Input()
    course?: Course;
    @Output()
    reload = new EventEmitter<void>();

    mode: 'individual' | 'average' = 'individual';
}
