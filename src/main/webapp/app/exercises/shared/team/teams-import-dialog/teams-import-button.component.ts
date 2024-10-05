import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Team } from 'app/entities/team.model';
import { Exercise } from 'app/entities/exercise.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { TeamsImportDialogComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-dialog.component';
import { faPlus } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-teams-import-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize"
            [icon]="faPlus"
            [title]="'artemisApp.team.importTeams.buttonLabel'"
            (onClick)="openTeamsImportDialog($event)"
        />
    `,
})
export class TeamsImportButtonComponent {
    private modalService = inject(NgbModal);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() exercise: Exercise;
    @Input() teams: Team[];
    @Input() buttonSize: ButtonSize = ButtonSize.SMALL;

    @Output() save: EventEmitter<Team[]> = new EventEmitter();

    // Icons
    faPlus = faPlus;

    /**
     * Open up import dialog for teams
     * @param {Event} event - Mouse Event which invoked the opening
     */
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
