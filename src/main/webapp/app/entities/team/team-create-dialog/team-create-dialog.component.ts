import { Component, Input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { Exercise } from 'app/entities/exercise';
import { TeamService } from 'app/entities/team/team.service';
import { Team } from 'app/entities/team/team.model';

@Component({
    selector: 'jhi-team-create-dialog',
    templateUrl: './team-create-dialog.component.html',
})
export class TeamCreateDialogComponent {
    @Input() exercise: Exercise;

    team: Team = new Team();
    isSaving = false;

    constructor(private participationService: ParticipationService, private teamService: TeamService, private activeModal: NgbActiveModal, private datePipe: DatePipe) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.team.exercise = this.exercise;
        this.subscribeToSaveResponse(this.teamService.create(this.team));
    }

    private subscribeToSaveResponse(team: Observable<HttpResponse<Team>>) {
        team.subscribe(
            res => this.onSaveSuccess(res),
            () => this.onSaveError(),
        );
    }

    onSaveSuccess(team: HttpResponse<Team>) {
        this.activeModal.close(team.body);
        this.isSaving = false;
    }

    onSaveError() {
        this.isSaving = false;
    }
}
