import { Component, Input, OnInit } from '@angular/core';
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
    styleUrls: ['./team-update-dialog.component.scss'],
})
export class TeamUpdateDialogComponent implements OnInit {
    @Input() team: Team;
    @Input() exercise: Exercise;

    pendingTeam: Team;
    isSaving = false;

    constructor(private participationService: ParticipationService, private teamService: TeamService, private activeModal: NgbActiveModal, private datePipe: DatePipe) {}

    ngOnInit(): void {
        this.pendingTeam = { ...this.team };
    }

    onAddStudent(student: User) {
        if (!this.pendingTeam.students) {
            this.pendingTeam.students = [];
        }
        this.pendingTeam.students.push(student);
    }

    onRemoveStudent(student: User) {
        this.pendingTeam.students = this.pendingTeam.students.filter(user => user.id !== student.id);
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.team = { ...this.pendingTeam };

        if (this.team.id !== undefined) {
            this.subscribeToSaveResponse(this.teamService.update(this.team));
        } else {
            this.subscribeToSaveResponse(this.teamService.create(this.exercise, this.team));
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
