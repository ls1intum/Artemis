import { Component, Input, ViewChild, OnInit, OnDestroy } from '@angular/core';
import { NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';
import { HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { Team } from 'app/entities/team.model';
import { Exercise } from 'app/entities/exercise.model';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { flatMap } from 'lodash';

enum ImportStrategy {
    PURGE_EXISTING = 'PURGE_EXISTING',
    CREATE_ONLY = 'CREATE_ONLY',
}

@Component({
    selector: 'jhi-teams-import-dialog',
    templateUrl: './teams-import-dialog.component.html',
    styleUrls: ['./teams-import-dialog.component.scss'],
})
export class TeamsImportDialogComponent implements OnInit, OnDestroy {
    readonly ImportStrategy = ImportStrategy;
    readonly ActionType = ActionType;

    @ViewChild('importForm', { static: false }) importForm: NgForm;

    @Input() exercise: Exercise;
    @Input() teams: Team[]; // existing teams already in exercise

    sourceExercise: Exercise;

    searchingExercises = false;
    searchingExercisesQueryTooShort = false;
    searchingExercisesFailed = false;
    searchingExercisesNoResultsForQuery: string | null = null;

    sourceTeams: Team[];
    loadingSourceTeams = false;
    loadingSourceTeamsFailed = false;

    importStrategy: ImportStrategy | null;
    readonly defaultImportStrategy: ImportStrategy = ImportStrategy.CREATE_ONLY;

    isImporting = false;

    // computed properties
    teamShortNamesAlreadyExistingInExercise: string[] = [];
    studentLoginsAlreadyExistingInExercise: string[] = [];
    sourceTeamsFreeOfConflicts: Team[] = [];

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private teamService: TeamService, private activeModal: NgbActiveModal, private jhiAlertService: JhiAlertService) {}

    ngOnInit() {
        this.computePotentialConflictsBasedOnExistingTeams();
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    loadSourceTeams(sourceExercise: Exercise) {
        this.loadingSourceTeams = true;
        this.loadingSourceTeamsFailed = false;
        this.teamService.findAllByExerciseId(sourceExercise.id).subscribe(
            (teamsResponse) => {
                this.sourceTeams = teamsResponse.body!;
                this.computeSourceTeamsFreeOfConflicts();
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
        this.importStrategy = null;
        this.loadSourceTeams(exercise);
    }

    computePotentialConflictsBasedOnExistingTeams() {
        this.teamShortNamesAlreadyExistingInExercise = this.teams.map((team) => team.shortName);
        this.studentLoginsAlreadyExistingInExercise = flatMap(this.teams, (team) => team.students.map((student) => student.login!));
    }

    computeSourceTeamsFreeOfConflicts() {
        this.sourceTeamsFreeOfConflicts = this.sourceTeams.filter((team: Team) => this.isSourceTeamFreeOfAnyConflicts(team));
    }

    isSourceTeamFreeOfAnyConflicts(sourceTeam: Team): boolean {
        // Short name of source team already exists among teams of destination exercise
        if (this.teamShortNamesAlreadyExistingInExercise.includes(sourceTeam.shortName)) {
            return false;
        }
        // One of the students of the source team is already part of a team in the destination exercise
        if (sourceTeam.students.some((student) => this.studentLoginsAlreadyExistingInExercise.includes(student.login!))) {
            return false;
        }
        // This source team can be imported without any issues
        return true;
    }

    get numberOfConflictFreeSourceTeams(): number {
        return this.sourceTeamsFreeOfConflicts.length;
    }

    get numberOfTeamsToBeDeleted(): number | null {
        switch (this.importStrategy) {
            case ImportStrategy.PURGE_EXISTING:
                return this.teams.length;
            case ImportStrategy.CREATE_ONLY:
                return 0;
            default:
                return null;
        }
    }

    get numberOfTeamsToBeImported(): number | null {
        switch (this.importStrategy) {
            case ImportStrategy.PURGE_EXISTING:
                return this.sourceTeams.length;
            case ImportStrategy.CREATE_ONLY:
                return this.numberOfConflictFreeSourceTeams;
            default:
                return null;
        }
    }

    get numberOfTeamsAfterImport(): number | null {
        switch (this.importStrategy) {
            case ImportStrategy.PURGE_EXISTING:
                return this.sourceTeams.length;
            case ImportStrategy.CREATE_ONLY:
                return this.teams.length + this.numberOfConflictFreeSourceTeams;
            default:
                return null;
        }
    }

    /**
     * Computed flag whether to prompt the user to pick an import strategy.
     *
     * Conditions that need to be fulfilled in order for the strategy choices to show:
     * 1. A source exercise has been selected
     * 2. The source exercise has teams that could be imported
     * 3. The current exercise already has existing teams in it
     */
    get showImportStrategyChoices(): boolean {
        return this.sourceExercise && this.sourceTeams?.length > 0 && this.teams.length > 0;
    }

    updateImportStrategy(importStrategy: ImportStrategy) {
        this.importStrategy = importStrategy;
    }

    /**
     * The import button is disabled if one of the following conditions apply:
     *
     * 1. Import is already in progress
     * 2. No source exercise has been selected yet
     * 3. Source teams have not been loaded yet
     * 4. No import strategy has been chosen yet
     * 5. There are no (conflict-free depending on strategy) source teams to be imported
     */
    get isSubmitDisabled(): boolean {
        return this.isImporting || !this.sourceExercise || !this.sourceTeams || !this.importStrategy || !this.numberOfTeamsToBeImported;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    purgeAndImportTeams() {
        this.dialogErrorSource.next('');
        this.importTeams();
    }

    importTeams() {
        this.isImporting = true;
        this.teamService.importTeamsFromExercise(this.exercise, this.sourceExercise).subscribe(
            (res) => this.onSaveSuccess(res),
            () => this.onSaveError(),
        );
    }

    onSaveSuccess(teams: HttpResponse<Team[]>) {
        this.activeModal.close(teams.body);
        this.isImporting = false;
    }

    onSaveError() {
        this.jhiAlertService.error('artemisApp.team.importError');
        this.isImporting = false;
    }
}
