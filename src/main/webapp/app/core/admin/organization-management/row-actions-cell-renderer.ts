import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faEye, faPenToSquare, faPlus, faTimes, faTrashCan, faWrench } from '@fortawesome/free-solid-svg-icons';
import { Organization } from 'app/core/shared/entities/organization.model';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CellRendererParams } from 'app/shared/table-view/table-view';
import { ButtonModule } from 'primeng/button';
import { Subject } from 'rxjs';

@Component({
    standalone: true,
    templateUrl: './row-actions-cell-renderer.html',
    imports: [TranslateDirective, RouterLink, FaIconComponent, DeleteButtonDirective, ButtonModule],
})
export class RowActionsCellRenderer {
    params = input.required<CellRendererParams<Organization>>();

    organization = computed(() => {
        return this.params().value;
    });

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faPenToSquare = faPenToSquare;
    faTrashCan = faTrashCan;

    /**
     * Deletes an organization by ID.
     * @param organizationId - The ID of the organization to delete
     */
    deleteOrganization(organizationId: number): void {
        // this.organizationService.deleteOrganization(organizationId).subscribe({
        //     next: () => {
        //         this.dialogErrorSource.next('');
        //         this.organizations.set(this.organizations().filter((org) => org.id !== organizationId));
        //     },
        //     error: (error: HttpErrorResponse) => {
        //         this.dialogErrorSource.next('An error occurred while removing the organization: ' + error.message);
        //     },
        // });
    }
}
