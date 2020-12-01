import { Component, Input, ViewChild, OnInit, OnDestroy, ViewEncapsulation } from '@angular/core';
import { NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { Team, TeamImportStrategyType as ImportStrategy } from 'app/entities/team.model';
import { Exercise } from 'app/entities/exercise.model';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { flatMap } from 'lodash';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-teams-import-dialog',
    templateUrl: './teams-import-dialog.component.html',
    styleUrls: ['./teams-import-dialog.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TeamsImportDialogComponent implements OnInit, OnDestroy {
    readonly ImportStrategy = ImportStrategy;
    readonly ActionType = ActionType;

    @ViewChild('importForm', { static: false }) importForm: NgForm;

    @Input() exercise: Exercise;
    @Input() teams: Team[]; // existing teams already in exercise

    sourceExercise?: Exercise;

    searchingExercises = false;
    searchingExercisesFailed = false;
    searchingExercisesNoResultsForQuery?: string;

    sourceTeams?: Team[];
    loadingSourceTeams = false;
    loadingSourceTeamsFailed = false;

    importStrategy?: ImportStrategy;
    readonly defaultImportStrategy: ImportStrategy = ImportStrategy.CREATE_ONLY;

    isImporting = false;
    showImportFromExercise = true;

    // computed properties
    teamShortNamesAlreadyExistingInExercise: string[] = [];
    studentLoginsAlreadyExistingInExercise: string[] = [];
    sourceTeamsFreeOfConflicts: Team[] = [];
    studentRegistrationNumbersAlreadyExistingInOtherTeams: string[] = [];
    studentRegistrationNumbersAlreadyExistingInExercise: string[] = [];
    notFoundRegistrationNumbers: string[] = [];
    notFoundLogins: string[] = [];
    studentLoginsAlreadyExistingInOtherTeams: string[] = [];

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private teamService: TeamService, private activeModal: NgbActiveModal, private jhiAlertService: JhiAlertService) {}

    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit() {
        this.computePotentialConflictsBasedOnExistingTeams();
    }

    /**
     * Life cycle hook to indicate component destruction is done
     */
    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Load teams from source exercise
     * @param {Exercise} sourceExercise - Source exercise to load teams from
     */
    loadSourceTeams(sourceExercise: Exercise) {
        this.sourceTeams = undefined;
        this.loadingSourceTeams = true;
        this.loadingSourceTeamsFailed = false;
        this.teamService.findAllByExerciseId(sourceExercise.id!).subscribe(
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

    /**
     * Method is called when the user has selected an exercise using the autocomplete select field
     *
     * The import strategy is reset and the source teams are loaded for the selected exercise
     *
     * @param exercise Exercise that was selected as a source exercise
     */
    onSelectSourceExercise(exercise: Exercise) {
        this.sourceExercise = exercise;
        this.initImportStrategy();
        this.loadSourceTeams(exercise);
    }

    /**
     * If the exercise has no teams yet, the user doesn't have to chose an import strategy
     * since there is no need for conflict handling decisions when no teams exist yet.
     */
    initImportStrategy() {
        this.importStrategy = this.teams.length === 0 ? this.defaultImportStrategy : undefined;
    }

    /**
     * Computes lists of potential conflict sources:
     * 1. The existing team short names of team already in this exercise
     * 2. The logins of students who already belong to teams of this exercise
     * 3. Registration numbers of students who already belong to teams of this exercise
     */
    computePotentialConflictsBasedOnExistingTeams() {
        this.teamShortNamesAlreadyExistingInExercise = this.teams.map((team) => team.shortName!);
        this.studentLoginsAlreadyExistingInExercise = flatMap(this.teams, (team) => team.students!.map((student) => student.login!));
        this.studentRegistrationNumbersAlreadyExistingInExercise = flatMap(this.teams, (team) => team.students!.map((student) => student.visibleRegistrationNumber || ''));
    }

    /**
     * Computes a list of all source teams that are conflict-free (i.e. could be imported without any problems)
     */
    computeSourceTeamsFreeOfConflicts() {
        this.sourceTeamsFreeOfConflicts = this.sourceTeams!.filter((team: Team) => this.isSourceTeamFreeOfAnyConflicts(team));
    }

    /**
     * Predicate that returns true iff the given source team is conflict-free
     *
     * This is the case if the following conditions are fulfilled:
     * 1. No team short name unique constraint violation
     * 2. No student is in the team that is already assigned to a team of the exercise
     * 3. No student in the team is also in another team
     *
     * @param sourceTeam Team which is checked for conflicts
     */
    isSourceTeamFreeOfAnyConflicts(sourceTeam: Team): boolean {
        // Short name of source team already exists among teams of destination exercise
        if (this.teamShortNamesAlreadyExistingInExercise.includes(sourceTeam.shortName!)) {
            return false;
        }
        // One of the students of the source team is already part of a team in the destination exercise
        if (sourceTeam.students!.some((student) => this.studentLoginsAlreadyExistingInExercise.includes(student.login!))) {
            return false;
        }

        // One of the students of the source team is already part of a team in the destination exercise or of another imported team
        if (!this.showImportFromExercise) {
            if (
                sourceTeam.students!.some((student) => {
                    return (
                        (student.visibleRegistrationNumber &&
                            (this.studentRegistrationNumbersAlreadyExistingInExercise.includes(student.visibleRegistrationNumber) ||
                                this.studentRegistrationNumbersAlreadyExistingInOtherTeams.includes(student.visibleRegistrationNumber))) ||
                        (student.login && this.studentLoginsAlreadyExistingInOtherTeams.includes(student.login))
                    );
                })
            ) {
                return false;
            }
        }

        // This source team can be imported without any issues
        return true;
    }

    get numberOfConflictFreeSourceTeams(): number {
        return this.sourceTeamsFreeOfConflicts.length;
    }

    get numberOfTeamsToBeDeleted() {
        switch (this.importStrategy) {
            case ImportStrategy.PURGE_EXISTING:
                return this.teams.length;
            case ImportStrategy.CREATE_ONLY:
                return 0;
        }
    }

    get numberOfTeamsToBeImported() {
        switch (this.importStrategy) {
            case ImportStrategy.PURGE_EXISTING:
                return this.sourceTeams!.length;
            case ImportStrategy.CREATE_ONLY:
                return this.numberOfConflictFreeSourceTeams;
        }
    }

    get numberOfTeamsAfterImport() {
        switch (this.importStrategy) {
            case ImportStrategy.PURGE_EXISTING:
                return this.sourceTeams!.length;
            case ImportStrategy.CREATE_ONLY:
                return this.teams.length + this.numberOfConflictFreeSourceTeams;
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
        if (this.showImportFromExercise) {
            return this.sourceExercise !== undefined && this.sourceTeams !== undefined && this.sourceTeams.length > 0 && this.teams.length > 0;
        }
        return this.sourceTeams !== undefined && this.sourceTeams.length > 0 && this.teams.length > 0;
    }

    /**
     * Update the strategy to use for importing
     * @param {ImportStrategy} importStrategy - Strategy to use
     */
    updateImportStrategy(importStrategy: ImportStrategy) {
        this.importStrategy = importStrategy;
    }

    /**
     * Computed flag whether to show the import preview numbers in the footer of the modal
     */
    get showImportPreviewNumbers(): boolean {
        if (this.showImportFromExercise) {
            return this.sourceExercise !== undefined && this.sourceTeams !== undefined && this.sourceTeams.length > 0 && Boolean(this.importStrategy);
        }
        return (
            this.studentRegistrationNumbersAlreadyExistingInOtherTeams.length > 0 ||
            this.studentLoginsAlreadyExistingInOtherTeams.length > 0 ||
            (this.sourceTeams !== undefined && this.sourceTeams.length > 0 && Boolean(this.importStrategy))
        );
    }

    /**
     * The import button is disabled if one of the following conditions apply:
     *
     * 1. Import is already in progress
     * 2. No source exercise has been selected yet
     * 3. Source teams have not been loaded yet
     * 4. No import strategy has been chosen yet
     * 5. There are no (conflict-free depending on strategy) source teams to be imported
     * 6. Student's registration number appears just once
     */
    get isSubmitDisabled(): boolean {
        if (this.showImportFromExercise) {
            return this.isImporting || !this.sourceExercise || !this.sourceTeams || !this.importStrategy || !this.numberOfTeamsToBeImported;
        }
        return (
            !this.sourceTeams ||
            this.sourceTeams.length === 0 ||
            !this.importStrategy ||
            !this.numberOfTeamsToBeImported ||
            this.studentRegistrationNumbersAlreadyExistingInOtherTeams.length > 0 ||
            this.studentLoginsAlreadyExistingInOtherTeams.length > 0
        );
    }

    /**
     * Cancel the import dialog
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Method is called if the strategy "Purge existing" has been chosen and the user has confirmed the delete action
     */
    purgeAndImportTeams() {
        this.dialogErrorSource.next('');
        this.importTeams();
    }

    /**
     * Is called when the user clicks on "Import" in the modal and sends the import request to the server
     */
    importTeams() {
        if (this.isSubmitDisabled) {
            return;
        }
        if (this.showImportFromExercise) {
            this.isImporting = true;
            this.teamService.importTeamsFromSourceExercise(this.exercise, this.sourceExercise!, this.importStrategy!).subscribe(
                (res) => this.onSaveSuccess(res),
                (error) => this.onSaveError(error),
            );
        } else if (this.sourceTeams) {
            this.notFoundRegistrationNumbers = [];
            this.teamService.importTeams(this.exercise, this.sourceTeams, this.importStrategy!).subscribe(
                (res) => this.onSaveSuccess(res),
                (error) => this.onSaveError(error),
            );
        }
    }

    /**
     * Update source teams to given teams
     * @param {Team[]} fileTeams - Teams which its students only have visible registration number
     */
    onTeamsChanged(fileTeams: Team[]) {
        this.initImportStrategy();
        this.sourceTeams = fileTeams;
        this.studentRegistrationNumbersAlreadyExistingInOtherTeams = [];
        this.notFoundRegistrationNumbers = [];
        const students: User[] = flatMap(fileTeams, (fileTeam) => fileTeam.students ?? []);
        const loginHistogram = students.reduce(function (map, student) {
            const login: string | undefined = student.login;
            if (login) {
                if (map[login]) {
                    map[login] += 1;
                } else {
                    map[login] = 1;
                }
            }
            return map;
        }, {});
        const registrationNumberHistogram = students.reduce(function (map, student) {
            const registrationNumber: string | undefined = student.visibleRegistrationNumber;
            if (registrationNumber) {
                if (map[registrationNumber]) {
                    map[registrationNumber] += 1;
                } else {
                    map[registrationNumber] = 1;
                }
            }
            return map;
        }, {});
        this.studentLoginsAlreadyExistingInOtherTeams = Object.keys(loginHistogram).filter((key) => loginHistogram[key] > 1);
        this.studentRegistrationNumbersAlreadyExistingInOtherTeams = Object.keys(registrationNumberHistogram).filter((key) => registrationNumberHistogram[key] > 1);
        this.computeSourceTeamsFreeOfConflicts();
    }

    /**
     * Hook to indicate a success on save
     * @param {HttpResponse<Team[]>} teams - Successfully updated teams
     */
    onSaveSuccess(teams: HttpResponse<Team[]>) {
        this.activeModal.close(teams.body);
        this.isImporting = false;

        setTimeout(() => {
            this.jhiAlertService.success('artemisApp.team.importSuccess', { numberOfImportedTeams: this.numberOfTeamsToBeImported });
        }, 500);
    }

    /**
     * Hook to indicate an error on save
     * @param {HttpErrorResponse} httpErrorResponse - The occurred error
     */
    onSaveError(httpErrorResponse: HttpErrorResponse) {
        const { errorKey, params } = httpErrorResponse.error;
        let studentsAppearMultipleTimes = [];
        type Pair = { first: string; second: string };
        switch (errorKey) {
            case 'studentsNotFound':
                const { registrationNumbers, logins } = params;
                this.notFoundRegistrationNumbers = registrationNumbers;
                this.notFoundLogins = logins;
                break;
            case 'studentsAppearMultipleTimes':
                const { students } = params;
                studentsAppearMultipleTimes = students;

                this.studentLoginsAlreadyExistingInOtherTeams = studentsAppearMultipleTimes.map((student: Pair) => student.first);
                this.studentRegistrationNumbersAlreadyExistingInOtherTeams = studentsAppearMultipleTimes.map((student: Pair) => student.second);
                break;
            default:
                break;
        }

        if (this.notFoundRegistrationNumbers.length > 0) {
            this.jhiAlertService.error('artemisApp.team.errors.registrationNumbersNotFound', { registrationNumbers: this.notFoundRegistrationNumbers });
        }
        if (this.notFoundLogins.length > 0) {
            this.jhiAlertService.error('artemisApp.team.errors.loginsNotFound', { logins: this.notFoundLogins });
        }
        if (this.notFoundLogins.length === 0 && this.notFoundRegistrationNumbers.length === 0) {
            this.jhiAlertService.error('artemisApp.team.importError');
        }

        if (studentsAppearMultipleTimes.length > 0) {
            this.jhiAlertService.error('artemisApp.team.errors.studentsAppearMultipleTimes', {
                students: studentsAppearMultipleTimes.map((student: Pair) => `${student.first}:${student.second}`).join(','),
            });
        }

        this.isImporting = false;
    }

    /**
     * Update showing whether the source selection or file selection and reset values
     * @param {boolean} showFromExercise - New value to set
     */
    setShowImportFromExercise(showFromExercise: boolean) {
        this.showImportFromExercise = showFromExercise;
        this.sourceTeams = undefined;
        this.sourceExercise = undefined;
        this.initImportStrategy();
        this.isImporting = false;
        this.studentRegistrationNumbersAlreadyExistingInOtherTeams = [];
        this.notFoundRegistrationNumbers = [];
        this.notFoundLogins = [];
        this.studentLoginsAlreadyExistingInOtherTeams = [];
    }

    /**
     * Returns a sample team that can be used to render the different conflict states in the legend below the source teams
     */
    get sampleTeamForLegend() {
        const team = new Team();
        const student = new User(1, 'ga12abc', 'John', 'Doe', 'john.doe@tum.de');
        student.name = `${student.firstName} ${student.lastName}`;
        team.students = [student];
        return team;
    }

    get sampleErrorStudentLoginsForLegend() {
        return this.sampleTeamForLegend.students!.map((student) => student.login);
    }

    get showLegend() {
        return Boolean(this.sourceTeams && this.numberOfConflictFreeSourceTeams !== this.sourceTeams.length);
    }

    get problematicRegistrationNumbers() {
        return [
            ...new Set([
                ...this.notFoundRegistrationNumbers,
                ...this.studentRegistrationNumbersAlreadyExistingInOtherTeams,
                ...this.studentRegistrationNumbersAlreadyExistingInExercise,
            ]),
        ];
    }

    get problematicLogins() {
        return [...new Set([...this.notFoundLogins, ...this.studentLoginsAlreadyExistingInOtherTeams, ...this.studentLoginsAlreadyExistingInExercise])];
    }
}
