import { Component, Input, OnDestroy, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { Team, TeamImportStrategyType as ImportStrategy } from 'app/entities/team.model';
import { Exercise } from 'app/entities/exercise.model';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { flatMap } from 'lodash-es';
import { User } from 'app/core/user/user.model';
import { faBan, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';

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
    sourceTeamsFreeOfConflicts: Team[] = [];

    conflictingRegistrationNumbersSet: Set<string> = new Set<string>();

    conflictingLoginsSet: Set<string> = new Set<string>();

    studentsAppearInMultipleTeams = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCircleNotch = faCircleNotch;
    faUpload = faUpload;

    constructor(private teamService: TeamService, private activeModal: NgbActiveModal, private alertService: AlertService) {}

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
        this.teamService.findAllByExerciseId(sourceExercise.id!).subscribe({
            next: (teamsResponse) => {
                this.sourceTeams = teamsResponse.body!;
                this.computeSourceTeamsFreeOfConflicts();
                this.loadingSourceTeams = false;
            },
            error: () => {
                this.loadingSourceTeams = false;
                this.loadingSourceTeamsFailed = true;
            },
        });
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
     * If the exercise has no teams yet, the user doesn't have to choose an import strategy
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
        const studentLoginsAlreadyExistingInExercise = flatMap(this.teams, (team) => team.students!.map((student) => student.login!));
        const studentRegistrationNumbersAlreadyExistingInExercise = flatMap(this.teams, (team) => team.students!.map((student) => student.visibleRegistrationNumber || ''));
        this.conflictingRegistrationNumbersSet = this.addArrayToSet(this.conflictingRegistrationNumbersSet, studentRegistrationNumbersAlreadyExistingInExercise);
        this.conflictingLoginsSet = this.addArrayToSet(this.conflictingLoginsSet, studentLoginsAlreadyExistingInExercise);
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
        if (sourceTeam.students!.some((student) => student.login && this.conflictingLoginsSet.has(student.login))) {
            return false;
        }

        // One of the students of the source team is already part of a team in the destination exercise or of another imported team
        if (!this.showImportFromExercise) {
            if (sourceTeam.students!.some((student) => student.visibleRegistrationNumber && this.conflictingRegistrationNumbersSet.has(student.visibleRegistrationNumber))) {
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
        return this.studentsAppearInMultipleTeams || (this.sourceTeams !== undefined && this.sourceTeams.length > 0 && Boolean(this.importStrategy));
    }

    /**
     * The import button is disabled if one of the following conditions apply:
     *
     * 1. Import is already in progress
     * 2. No source exercise has been selected yet
     * 3. Source teams have not been loaded yet
     * 4. No import strategy has been chosen yet
     * 5. There are no (conflict-free depending on strategy) source teams to be imported
     * 6. Student's registration number appears more than once
     * 7. Student's login appears more than once
     */
    get isSubmitDisabled(): boolean {
        if (this.showImportFromExercise) {
            return this.isImporting || !this.sourceExercise || !this.sourceTeams || !this.importStrategy || !this.numberOfTeamsToBeImported;
        }
        return !this.sourceTeams || this.sourceTeams.length === 0 || !this.importStrategy || !this.numberOfTeamsToBeImported || this.studentsAppearInMultipleTeams;
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
            this.teamService.importTeamsFromSourceExercise(this.exercise, this.sourceExercise!, this.importStrategy!).subscribe({
                next: (res) => this.onSaveSuccess(res),
                error: (error) => this.onSaveError(error),
            });
        } else if (this.sourceTeams) {
            this.resetConflictingSets();
            this.teamService.importTeams(this.exercise, this.sourceTeams, this.importStrategy!).subscribe({
                next: (res) => this.onSaveSuccess(res),
                error: (error) => this.onSaveError(error),
            });
        }
    }

    /**
     * Updates source teams to given teams
     * Finds logins and registration numbers which appears multiple times
     * Resets current state of arrays of registration numbers and logins
     * @param {Team[]} fileTeams - Teams which its students only have visible registration number
     */
    onTeamsChanged(fileTeams: Team[]) {
        this.initImportStrategy();
        this.sourceTeams = fileTeams;
        this.resetConflictingSets();
        const students: User[] = flatMap(fileTeams, (fileTeam) => fileTeam.students ?? []);
        const studentLoginsAlreadyExistingInOtherTeams = this.findIdentifiersWhichAppearsMultipleTimes(students, 'login');
        const studentRegistrationNumbersAlreadyExistingInOtherTeams = this.findIdentifiersWhichAppearsMultipleTimes(students, 'visibleRegistrationNumber');
        this.studentsAppearInMultipleTeams = studentLoginsAlreadyExistingInOtherTeams.length > 0 || studentRegistrationNumbersAlreadyExistingInOtherTeams.length > 0;
        this.conflictingRegistrationNumbersSet = this.addArrayToSet(this.conflictingRegistrationNumbersSet, studentRegistrationNumbersAlreadyExistingInOtherTeams);
        this.conflictingLoginsSet = this.addArrayToSet(this.conflictingLoginsSet, studentLoginsAlreadyExistingInOtherTeams);
        this.computeSourceTeamsFreeOfConflicts();
    }

    /**
     * Calculates which identifier appeared how many times and returns those appear more than once
     * @param users Users array to find identifiers that appear multiple times in
     * @param identifier Which identifier to use when searching for multiple occurrences
     * @returns Identifiers which appeared multiple times in user array
     */
    private findIdentifiersWhichAppearsMultipleTimes(users: User[], identifier: 'login' | 'visibleRegistrationNumber') {
        const occurrenceMap = new Map();
        users.forEach((user) => {
            const identifierValue = user[identifier];
            if (identifierValue) {
                if (occurrenceMap.get(identifierValue)) {
                    occurrenceMap.set(identifierValue, occurrenceMap.get(identifierValue) + 1);
                } else {
                    occurrenceMap.set(identifierValue, 1);
                }
            }
        });
        return [...occurrenceMap.keys()].filter((key) => occurrenceMap.get(key) > 1);
    }

    /**
     * Hook to indicate a success on save
     * @param {HttpResponse<Team[]>} teams - Successfully updated teams
     */
    onSaveSuccess(teams: HttpResponse<Team[]>) {
        this.activeModal.close(teams.body);
        this.isImporting = false;

        setTimeout(() => {
            this.alertService.success('artemisApp.team.importSuccess', { numberOfImportedTeams: this.numberOfTeamsToBeImported });
        }, 500);
    }

    /**
     * Hook to indicate an error on save
     * Handles studentsNotFound and studentsAppearMultipleTimes errors
     * Shows generic import error message if error is not one of the above
     * @param {HttpErrorResponse} httpErrorResponse - The occurred error
     */
    onSaveError(httpErrorResponse: HttpErrorResponse) {
        const { errorKey, params } = httpErrorResponse.error;
        switch (errorKey) {
            case 'studentsNotFound':
                const { registrationNumbers, logins } = params;
                this.onStudentsNotFoundError(registrationNumbers, logins);
                break;
            case 'studentsAppearMultipleTimes':
                const { students } = params;
                this.onStudentsAppearMultipleTimesError(students);
                break;
            default:
                this.alertService.error('artemisApp.team.importError');
                break;
        }
        this.isImporting = false;
    }

    /**
     * Gets not found registration numbers and logins
     * Assigns them to appropriate array on the class
     * Shows user which registration numbers and logins could not be found
     * @param registrationNumbers with which a student could not be found
     * @param logins with which a student could not be found
     */
    private onStudentsNotFoundError(registrationNumbers: string[], logins: string[]) {
        const notFoundRegistrationNumbers = registrationNumbers;
        const notFoundLogins = logins;
        if (notFoundRegistrationNumbers.length > 0) {
            this.alertService.error('artemisApp.team.errors.registrationNumbersNotFound', { registrationNumbers: notFoundRegistrationNumbers });
            this.conflictingRegistrationNumbersSet = this.addArrayToSet(this.conflictingRegistrationNumbersSet, notFoundRegistrationNumbers);
        }
        if (notFoundLogins.length > 0) {
            this.alertService.error('artemisApp.team.errors.loginsNotFound', { logins: notFoundLogins });
            this.conflictingLoginsSet = this.addArrayToSet(this.conflictingLoginsSet, notFoundLogins);
        }
    }

    /**
     * Gets pairs of login-registration number of students which could not be found
     * Assigns them to appropriate array on the class
     * Shows user which students could not be found
     * @param studentsAppearMultipleTimes login-registration number pairs of students which could not be found
     */
    private onStudentsAppearMultipleTimesError(studentsAppearMultipleTimes: { first: string; second: string }[]) {
        if (studentsAppearMultipleTimes.length > 0) {
            this.studentsAppearInMultipleTeams = true;
            const studentLoginsAlreadyExistingInOtherTeams = studentsAppearMultipleTimes.map((student) => student.first);
            this.conflictingLoginsSet = this.addArrayToSet(this.conflictingLoginsSet, studentLoginsAlreadyExistingInOtherTeams);
            const studentRegistrationNumbersAlreadyExistingInOtherTeams = studentsAppearMultipleTimes.map((student) => student.second);
            this.conflictingRegistrationNumbersSet = this.addArrayToSet(this.conflictingRegistrationNumbersSet, studentRegistrationNumbersAlreadyExistingInOtherTeams);
            this.alertService.error('artemisApp.team.errors.studentsAppearMultipleTimes', {
                students: studentsAppearMultipleTimes.map((student) => `${student.first}:${student.second}`).join(','),
            });
        }
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
        this.resetConflictingSets();
    }

    /**
     * @returns A sample team that can be used to render the different conflict states in the legend below the source teams
     */
    get sampleTeamForLegend() {
        const team = new Team();
        const student = new User(1, 'ga12abc', 'John', 'Doe', 'john.doe@tum.de');
        student.name = `${student.firstName} ${student.lastName}`;
        team.students = [student];
        return team;
    }

    get sampleErrorStudentLoginsForLegend(): string[] {
        return this.sampleTeamForLegend.students!.map((student) => student.login).filter((login) => login !== undefined) as string[];
    }

    get showLegend() {
        return Boolean(this.sourceTeams && this.numberOfConflictFreeSourceTeams !== this.sourceTeams.length);
    }

    get problematicRegistrationNumbers() {
        return [...this.conflictingRegistrationNumbersSet];
    }

    get problematicLogins() {
        return [...this.conflictingLoginsSet];
    }

    /**
     * Adds given array to given set and returns a new set
     * @param set to add array to
     * @param array that will be added to set
     * @returns A set that has values of both given set and array
     */
    private addArrayToSet(set: Set<string>, array: string[]) {
        return new Set([...array, ...set.values()]);
    }

    /**
     * Reset conflicting logins and registration numbers set
     * Sets students appear in multiple teams to false
     * Recomputes potential conflicts
     */
    private resetConflictingSets() {
        this.conflictingLoginsSet = new Set();
        this.conflictingRegistrationNumbersSet = new Set();
        this.studentsAppearInMultipleTeams = false;
        this.computePotentialConflictsBasedOnExistingTeams();
    }
}
