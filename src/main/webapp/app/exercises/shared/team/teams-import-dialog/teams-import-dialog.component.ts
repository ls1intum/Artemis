import { Component, Input, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { Team } from 'app/entities/team.model';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-teams-import-dialog',
    templateUrl: './teams-import-dialog.component.html',
    styleUrls: ['./teams-import-dialog.component.scss'],
})
export class TeamsImportDialogComponent {
    @ViewChild('importForm', { static: false }) importForm: NgForm;

    @Input() exercise: Exercise;

    sourceExercise: Exercise;

    searchingExercises = false;
    searchingExercisesQueryTooShort = false;
    searchingExercisesFailed = false;
    searchingExercisesNoResultsForQuery: string | null = null;

    sourceTeams: Team[];
    loadingSourceTeams = false;
    loadingSourceTeamsFailed = false;

    isImporting = false;

    constructor(private teamService: TeamService, private activeModal: NgbActiveModal) {}

    loadSourceTeams(sourceExercise: Exercise) {
        this.loadingSourceTeams = true;
        this.loadingSourceTeamsFailed = false;
        this.teamService.findAllByExerciseId(sourceExercise.id).subscribe(
            (teamsResponse) => {
                this.sourceTeams = teamsResponse.body!;
                this.loadingSourceTeams = false;
            },
            () => {
                this.loadingSourceTeams = false;
                this.loadingSourceTeamsFailed = true;
            },
        );
    }

    onSelectSourceExercise(exercise: Exercise) {
        this.sourceExercise = exercise;
        this.loadSourceTeams(exercise);
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.subscribeToSaveResponse(this.teamService.importTeamsFromExercise(this.exercise, this.sourceExercise));
    }

    private subscribeToSaveResponse(teams: Observable<HttpResponse<Team[]>>) {
        this.isImporting = true;
        teams.subscribe(
            (res) => this.onSaveSuccess(res),
            (error) => this.onSaveError(error),
        );
    }

    onSaveSuccess(teams: HttpResponse<Team[]>) {
        this.activeModal.close(teams.body);
        this.isImporting = false;
    }

    onSaveError(httpErrorResponse: HttpErrorResponse) {
        this.isImporting = false;
    }
}
