import { Component, Input, Output, EventEmitter } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from 'app/entities/exercise';
import { TeamUpdateDialogComponent } from 'app/entities/team/team-update-dialog/team-update-dialog.component';
import { Team } from 'app/entities/team/team.model';

@Component({
    selector: 'jhi-team-update-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize"
            [icon]="team ? 'pencil-alt' : 'plus'"
            [title]="team ? 'artemisApp.team.updateTeam.label' : 'artemisApp.team.createTeam.label'"
            (onClick)="openTeamCreateDialog($event)"
        ></jhi-button>
    `,
})
export class TeamUpdateButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() team: Team | null;
    @Input() exercise: Exercise;
    @Input() buttonSize: ButtonSize = ButtonSize.SMALL;

    @Output() save: EventEmitter<void> = new EventEmitter();

    constructor(private modalService: NgbModal) {}

    openTeamCreateDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(TeamUpdateDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.team = this.team || new Team();
        modalRef.componentInstance.exercise = this.exercise;

        modalRef.result.then(
            () => this.save.emit(),
            () => {},
        );
    }
}
