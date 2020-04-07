import { Component, Input, OnInit, ViewChild } from '@angular/core';
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
export class TeamsImportDialogComponent implements OnInit {
    @ViewChild('importForm', { static: false }) importForm: NgForm;

    @Input() exercise: Exercise;

    sourceExercise: Exercise;
    isImporting = false;

    searchingExercises = false;
    searchingExercisesQueryTooShort = false;
    searchingExercisesFailed = false;
    searchingExercisesNoResultsForQuery: string | null = null;

    constructor(private teamService: TeamService, private activeModal: NgbActiveModal) {}

    ngOnInit(): void {}

    onSelectSourceExercise(exercise: Exercise) {
        this.sourceExercise = exercise;
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
