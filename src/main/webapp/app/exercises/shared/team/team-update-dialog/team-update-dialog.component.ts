import { Component, Input, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { AbstractControl, NgForm } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { Team } from 'app/entities/team.model';
import { User } from 'app/core/user/user.model';
import { cloneDeep, isEmpty, omit } from 'lodash';
import { TeamAssignmentConfig } from 'app/entities/team-assignment-config.model';
import { debounceTime, switchMap } from 'rxjs/operators';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-team-update-dialog',
    templateUrl: './team-update-dialog.component.html',
    styleUrls: ['./team-update-dialog.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TeamUpdateDialogComponent implements OnInit {
    @ViewChild('editForm', { static: false }) editForm: NgForm;

    @Input() team: Team;
    @Input() exercise: Exercise;

    pendingTeam: Team;
    isSaving = false;
    searchingStudents = false;
    searchingStudentsQueryTooShort = false;
    searchingStudentsFailed = false;
    searchingStudentsNoResultsForQuery: string | null = null;
    studentTeamConflicts = [];
    ignoreTeamSizeRecommendation = false;

    private shortNameValidator = new Subject<string>();
    readonly shortNameAlreadyTakenErrorCode = 'alreadyTaken';
    readonly shortNamePattern = '^[a-zA-Z][a-zA-Z0-9]*'; // must start with a letter and cannot contain special characters

    constructor(private participationService: ParticipationService, private teamService: TeamService, private activeModal: NgbActiveModal, private datePipe: DatePipe) {}

    ngOnInit(): void {
        this.initPendingTeam();
        this.shortNameValidation(this.shortNameValidator);
    }

    private initPendingTeam() {
        this.pendingTeam = cloneDeep(this.team);
    }

    onTeamShortNameChanged(shortName: string) {
        // automatically convert shortName to lowercase characters
        this.pendingTeam.shortName = shortName.toLowerCase();

        // check that no other team already uses this short name
        this.shortNameValidator.next(this.pendingTeam.shortName);
    }

    onTeamNameChanged(name: string) {
        if (!this.shortNameReadOnly) {
            // automatically set the shortName based on the name (stripping all non-alphanumeric characters)
            const shortName = name.replace(/[^0-9a-z]/gi, '');
            this.onTeamShortNameChanged(shortName);
            this.shortNameControl.markAsTouched();
        }
    }

    get shortNameReadOnly(): boolean {
        return !!this.pendingTeam.id;
    }

    get shortNameControl(): AbstractControl {
        return this.editForm.control.get('shortName')!;
    }

    get config(): TeamAssignmentConfig {
        return this.exercise.teamAssignmentConfig!;
    }

    get showIgnoreTeamSizeRecommendationOption(): boolean {
        return !this.recommendedTeamSize;
    }

    get teamSizeViolationUnconfirmed(): boolean {
        return this.showIgnoreTeamSizeRecommendationOption && !this.ignoreTeamSizeRecommendation;
    }

    private get recommendedTeamSize(): boolean {
        const pendingTeamSize = this.pendingTeam.students.length;
        return pendingTeamSize >= this.config.minTeamSize && pendingTeamSize <= this.config.maxTeamSize;
    }

    hasConflictingTeam(student: User): boolean {
        return this.findStudentTeamConflict(student) !== undefined;
    }

    getConflictingTeam(student: User): string | null {
        const conflict = this.findStudentTeamConflict(student);
        return conflict ? conflict['teamId'] : null;
    }

    private findStudentTeamConflict(student: User) {
        return this.studentTeamConflicts.find((c) => c['studentLogin'] === student.login);
    }

    private resetStudentTeamConflict(student: User) {
        return (this.studentTeamConflicts = this.studentTeamConflicts.filter((c) => c['studentLogin'] !== student.login));
    }

    private isStudentAlreadyInPendingTeam(student: User): boolean {
        return this.pendingTeam.students.find((s) => s.id === student.id) !== undefined;
    }

    onAddStudent(student: User) {
        if (!this.isStudentAlreadyInPendingTeam(student)) {
            this.pendingTeam.students.push(student);
        }
    }

    onRemoveStudent(student: User) {
        this.pendingTeam.students = this.pendingTeam.students.filter((user) => user.id !== student.id);
        this.resetStudentTeamConflict(student); // conflict might no longer exist when the student is added again
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
            this.subscribeToSaveResponse(this.teamService.update(this.exercise, this.team));
        } else {
            this.subscribeToSaveResponse(this.teamService.create(this.exercise, this.team));
        }
    }

    private subscribeToSaveResponse(team: Observable<HttpResponse<Team>>) {
        this.isSaving = true;
        team.subscribe(
            (res) => this.onSaveSuccess(res),
            (error) => this.onSaveError(error),
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

    private shortNameValidation(shortName$: Subject<string>) {
        shortName$
            .pipe(
                debounceTime(500),
                switchMap((shortName) => this.teamService.existsByShortName(shortName)),
            )
            .subscribe(
                (alreadyTakenResponse) => {
                    const alreadyTaken = alreadyTakenResponse.body;
                    const errors = alreadyTaken
                        ? { ...this.shortNameControl.errors, [this.shortNameAlreadyTakenErrorCode]: alreadyTaken }
                        : omit(this.shortNameControl.errors, this.shortNameAlreadyTakenErrorCode);
                    this.shortNameControl.setErrors(isEmpty(errors) ? null : errors);
                },
                () => {},
            );
    }
}
