import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { Organization } from 'app/admin/organization-management/organization.model';
import { CellTemplateRef, ColumnDef, TumUiButtonDirective, TumUiTableComponent, TumUiTableQueryEvent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { buildDbQueryFromTableEvent } from 'app/shared-ui/tum-ui-integration/tum-ui-table-request-builder';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-organization-selector',
    templateUrl: './organization-selector.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, TumUiTableComponent, TumUiButtonDirective],
})
export class OrganizationSelectorComponent {
    private readonly organizationService = inject(OrganizationManagementService);
    private readonly alertService = inject(AlertService);

    /** Organizations already assigned to the user, used to disable their select button. */
    readonly assignedOrganizations = input<Organization[]>([]);

    /** Emitted with the chosen organization when the user selects one. */
    readonly selected = output<Organization>();
    /** Emitted when the user cancels the selection. */
    readonly cancelled = output<void>();

    readonly organizations = signal<Organization[]>([]);
    readonly totalCount = signal(0);
    readonly isLoading = signal(false);

    private loadRequestId = 0;

    private readonly logoTemplate = viewChild<CellTemplateRef<Organization>>('logoCell');

    protected readonly assignedOrgIds = computed(
        () => new Set<number>((this.assignedOrganizations() ?? []).map((o: Organization) => o.id).filter((id: number | undefined): id is number => id !== undefined)),
    );

    readonly columns = computed<ColumnDef<Organization>[]>(() => [
        { field: 'logoUrl', templateRef: this.logoTemplate() },
        { field: 'name', headerKey: 'artemisApp.organizationManagement.name', sort: true },
        { field: 'shortName', headerKey: 'artemisApp.organizationManagement.shortName', sort: true },
        { field: 'emailPattern', headerKey: 'artemisApp.organizationManagement.emailPattern', sort: true },
    ]);

    isAlreadyAssigned = computed(() => (org: Organization) => org.id !== undefined && this.assignedOrgIds().has(org.id));

    loadOrganizations(event: TumUiTableQueryEvent): void {
        this.isLoading.set(true);
        const requestId = ++this.loadRequestId;
        const query = buildDbQueryFromTableEvent(event);
        this.organizationService.getOrganizations(query).subscribe({
            next: (response) => {
                if (requestId !== this.loadRequestId) return;
                this.organizations.set(response.content);
                this.totalCount.set(response.totalElements);
                this.isLoading.set(false);
            },
            error: (error: HttpErrorResponse) => {
                if (requestId !== this.loadRequestId) return;
                this.organizations.set([]);
                this.totalCount.set(0);
                this.isLoading.set(false);
                onError(this.alertService, error);
            },
        });
    }

    selectOrganization(organization: Organization): void {
        this.selected.emit(organization);
    }

    cancel(): void {
        this.cancelled.emit();
    }
}
