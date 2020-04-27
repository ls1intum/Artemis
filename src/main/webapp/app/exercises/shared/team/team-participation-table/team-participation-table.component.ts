import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { Team } from 'app/entities/team.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { Course } from 'app/entities/course.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { AlertService } from 'app/core/alert/alert.service';

const currentExerciseRowClass = 'datatable-row-current-exercise';

@Component({
    selector: 'jhi-team-participation-table',
    templateUrl: './team-participation-table.component.html',
    styleUrls: ['./team-participation-table.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TeamParticipationTableComponent implements OnInit {
    readonly ExerciseType = ExerciseType;
    readonly moment = moment;

    @Input() team: Team;
    @Input() course: Course;
    @Input() exercise: Exercise;

    participations: StudentParticipation[];
    isLoading: boolean;

    constructor(private participationService: ParticipationService, private jhiAlertService: AlertService) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.participationService.findAllParticipationsByCourseIdAndTeamShortName(this.course.id, this.team.shortName).subscribe(
            (participationsResponse) => {
                this.participations = participationsResponse.body!;
                this.isLoading = false;
            },
            (error) => {
                console.error(error);
                this.jhiAlertService.error(error.message);
                this.isLoading = false;
            },
        );
    }

    rowClass = (row: StudentParticipation): string => {
        return this.exercise.id === row.exercise.id ? currentExerciseRowClass : '';
    };
}
