import { Component, Input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { Exercise } from 'app/entities/exercise';
import { TeamService } from 'app/entities/team/team.service';
import { Team } from 'app/entities/team/team.model';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-team-update-dialog',
    templateUrl: './team-update-dialog.component.html',
})
export class TeamUpdateDialogComponent {
    @Input() team: Team;
    @Input() exercise: Exercise;

    isSaving = false;

    constructor(private participationService: ParticipationService, private teamService: TeamService, private activeModal: NgbActiveModal, private datePipe: DatePipe) {}

    onAddStudent(student: User) {
        this.team.students.push(student);
        console.log('team students:', this.team.students);
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.team.exercise = this.exercise;

        if (this.team.id !== undefined) {
            this.subscribeToSaveResponse(this.teamService.update(this.team));
        } else {
            this.subscribeToSaveResponse(this.teamService.create(this.team));
        }
    }

    private subscribeToSaveResponse(team: Observable<HttpResponse<Team>>) {
        this.isSaving = true;
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
