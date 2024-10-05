import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TeamUpdateDialogComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-dialog.component';
import { Team } from 'app/entities/team.model';
import { Exercise } from 'app/entities/exercise.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { faPencilAlt, faPlus } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-team-update-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize"
            [icon]="team ? faPencilAlt : faPlus"
            [title]="team ? 'artemisApp.team.updateTeam.label' : 'artemisApp.team.createTeam.label'"
            (onClick)="openTeamCreateDialog($event)"
        />
    `,
})
export class TeamUpdateButtonComponent {
    private modalService = inject(NgbModal);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() team: Team | undefined;
    @Input() exercise: Exercise;
    @Input() buttonSize: ButtonSize = ButtonSize.SMALL;

    @Output() save: EventEmitter<Team> = new EventEmitter();

    // Icons
    faPencilAlt = faPencilAlt;
    faPlus = faPlus;

    /**
     * Open the dialog for team creation
     * @param {MouseEvent} event - Occurred Mouse Event
     */
    openTeamCreateDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(TeamUpdateDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.team = this.team || new Team();
        modalRef.componentInstance.exercise = this.exercise;

        modalRef.result.then(
            (team: Team) => this.save.emit(team),
            () => {},
        );
    }
}
