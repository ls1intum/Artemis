import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { Component, OnDestroy, inject, input, output } from '@angular/core';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { Subject } from 'rxjs';
import { TeamService } from 'app/exercise/team/team.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';

@Component({
    selector: 'jhi-team-delete-button',
    template: `
        @if (exercise().isAtLeastInstructor) {
            <button
                tumAetUiButton
                [attr.aria-label]="'entity.action.delete' | artemisTranslate"
                jhiDeleteButton
                size="small"
                severity="danger"
                [renderButtonStyle]="false"
                [entityTitle]="team().shortName || ''"
                deleteQuestion="artemisApp.team.delete.question"
                deleteConfirmationText="artemisApp.team.delete.typeNameToConfirm"
                (delete)="removeTeam()"
                [dialogError]="dialogError$"
            >
                <fa-icon [icon]="faTrash" />
            </button>
        }
    `,
    imports: [ArtemisTranslatePipe, FaIconComponent, TumAetUiButtonDirective, DeleteButtonDirective],
})
export class TeamDeleteButtonComponent implements OnDestroy {
    private alertService = inject(AlertService);
    private teamService = inject(TeamService);
    readonly faTrash = faTrash;

    readonly team = input.required<Team>();
    readonly exercise = input.required<Exercise>();

    readonly delete = output<Team>();

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

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
        this.teamService.delete(this.exercise(), this.team().id!).subscribe({
            next: () => {
                this.delete.emit(this.team());
                this.dialogErrorSource.next('');
            },
            error: () => this.alertService.error('artemisApp.team.removeTeam.error'),
        });
    };
}
