import { Component, Input } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from 'app/entities/exercise';
import { TeamCreateDialogComponent } from 'app/entities/team/team-create-dialog/team-create-dialog.component';

@Component({
    selector: 'jhi-team-create-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="ButtonSize.MEDIUM"
            [icon]="'plus'"
            [title]="'artemisApp.team.createTeam.label'"
            (onClick)="openTeamCreateDialog($event)"
        ></jhi-button>
    `,
})
export class TeamCreateButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() exercise: Exercise;

    constructor(private modalService: NgbModal) {}

    openTeamCreateDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(TeamCreateDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exercise = this.exercise;
    }
}
