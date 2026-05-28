import { Component, inject, input, output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { TeamsImportDialogComponent } from 'app/exercise/team/teams-import-dialog/teams-import-dialog.component';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-teams-import-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize()"
            [icon]="faPlus"
            [title]="'artemisApp.team.importTeams.buttonLabel'"
            (onClick)="openTeamsImportDialog($event)"
        />
    `,
    imports: [ButtonComponent],
})
export class TeamsImportButtonComponent {
    private modalService = inject(NgbModal);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    readonly exercise = input<Exercise>(undefined!);
    readonly teams = input<Team[]>(undefined!);
    readonly buttonSize = input<ButtonSize>(ButtonSize.SMALL);

    readonly save = output<Team[]>();

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
