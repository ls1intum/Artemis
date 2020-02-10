import { Component, Input, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { Exercise } from 'app/entities/exercise';
import { TeamService } from 'app/entities/team/team.service';
import { Team } from 'app/entities/team/team.model';
import { User } from 'app/core/user/user.model';
import { cloneDeep } from 'lodash';

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

    searchingStudents = false;
    searchingStudentsFailed = false;

    studentTeamConflicts = [];

    ignoreTeamSizeRecommendation = false;

    constructor(private participationService: ParticipationService, private teamService: TeamService, private activeModal: NgbActiveModal, private datePipe: DatePipe) {}

    ngOnInit(): void {
        this.pendingTeam = cloneDeep(this.team);
    }

    get config() {
        return this.exercise.teamAssignmentConfig!;
    }

    get showIgnoreTeamSizeRecommendationOption() {
        return !this.recommendedTeamSize;
    }

    get teamSizeViolationUnconfirmed() {
        return this.showIgnoreTeamSizeRecommendationOption && !this.ignoreTeamSizeRecommendation;
    }

    private get recommendedTeamSize() {
        const pendingTeamSize = this.pendingTeam.students.length;
        return pendingTeamSize >= this.config.minTeamSize && pendingTeamSize <= this.config.maxTeamSize;
    }

    hasConflictingTeam(student: User) {
        return this.findStudentTeamConflict(student) !== undefined;
    }

    getConflictingTeam(student: User) {
        const conflict = this.findStudentTeamConflict(student);
        return conflict ? conflict['teamId'] : null;
    }

    private findStudentTeamConflict(student: User) {
        return this.studentTeamConflicts.find(c => c['studentLogin'] === student.login);
    }

    private isStudentAlreadyInPendingTeam(student: User) {
        return this.pendingTeam.students.find(s => s.id === student.id) !== undefined;
    }

    onAddStudent(student: User) {
        if (!this.pendingTeam.students) {
            this.pendingTeam.students = [];
        }
        if (!this.isStudentAlreadyInPendingTeam(student)) {
            this.pendingTeam.students.push(student);
        }
    }

    onRemoveStudent(student: User) {
        this.pendingTeam.students = this.pendingTeam.students.filter(user => user.id !== student.id);
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        if (this.teamSizeViolationUnconfirmed) {
            return;
        }

        this.team = cloneDeep(this.pendingTeam);

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
            error => this.onSaveError(error),
        );
    }

    onSaveSuccess(team: HttpResponse<Team>) {
        this.activeModal.close(team.body);
        this.isSaving = false;
    }

    onSaveError(httpErrorResponse: HttpErrorResponse) {
        this.isSaving = false;

        const { errorKey, params } = httpErrorResponse.error;

        switch (errorKey) {
            case 'studentsAlreadyAssignedToTeams':
                const { conflicts } = params;
                this.studentTeamConflicts = conflicts;
                break;
            default:
                break;
        }
    }
}
