import { Component, EventEmitter, Input, OnDestroy, Output, inject } from '@angular/core';
import { Team } from 'app/entities/team.model';
import { Subject } from 'rxjs';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { Exercise } from 'app/entities/exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-team-delete-button',
    template: `
        @if (exercise.isAtLeastInstructor) {
            <button
                jhiDeleteButton
                [buttonSize]="buttonSize"
                [entityTitle]="team.shortName || ''"
                deleteQuestion="artemisApp.team.delete.question"
                deleteConfirmationText="artemisApp.team.delete.typeNameToConfirm"
                (delete)="removeTeam()"
                [dialogError]="dialogError$"
            >
                <fa-icon [icon]="faTrashAlt" class="me-1" />
            </button>
        }
    `,
    imports: [DeleteButtonDirective, FaIconComponent],
})
export class TeamDeleteButtonComponent implements OnDestroy {
    private alertService = inject(AlertService);
    private teamService = inject(TeamService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() team: Team;
    @Input() exercise: Exercise;
    @Input() buttonSize: ButtonSize = ButtonSize.SMALL;

    @Output() delete = new EventEmitter<Team>();

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faTrashAlt = faTrashAlt;

    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Deletes the provided team on the server and emits the delete event
     *
     */
    removeTeam = () => {
        this.teamService.delete(this.exercise, this.team.id!).subscribe({
            next: () => {
                this.delete.emit(this.team);
                this.dialogErrorSource.next('');
            },
            error: () => this.alertService.error('artemisApp.team.removeTeam.error'),
        });
    };
}
