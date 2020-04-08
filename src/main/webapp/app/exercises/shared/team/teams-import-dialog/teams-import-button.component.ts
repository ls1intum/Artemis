import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Team } from 'app/entities/team.model';
import { Exercise } from 'app/entities/exercise.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { TeamsImportDialogComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-dialog.component';

@Component({
    selector: 'jhi-teams-import-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize"
            [icon]="'plus'"
            [title]="'artemisApp.team.importTeams.buttonLabel'"
            (onClick)="openTeamsImportDialog($event)"
        ></jhi-button>
    `,
})
export class TeamsImportButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() exercise: Exercise;
    @Input() teams: Team[];
    @Input() buttonSize: ButtonSize = ButtonSize.SMALL;

    @Output() save: EventEmitter<Team[]> = new EventEmitter();

    constructor(private modalService: NgbModal) {}

    openTeamsImportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(TeamsImportDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exercise = this.exercise;
        modalRef.componentInstance.teams = this.teams;

        modalRef.result.then(
            (teams: Team[]) => this.save.emit(teams),
            () => {},
        );
    }
}
