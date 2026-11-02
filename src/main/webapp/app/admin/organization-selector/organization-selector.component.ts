import { OrganizationListDirective } from 'app/admin/organization-management/organization-list.directive';
import { ChangeDetectionStrategy, Component, computed, input, output, viewChild } from '@angular/core';
import { Organization } from 'app/admin/organization-management/organization.model';
import { CellTemplateRef, ColumnDef, TumAetUiButtonDirective, TumAetUiTableComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-organization-selector',
    templateUrl: './organization-selector.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, TumAetUiTableComponent, TumAetUiButtonDirective],
})
export class OrganizationSelectorComponent extends OrganizationListDirective {
    /** Organizations already assigned to the user, used to disable their select button. */
    readonly assignedOrganizations = input<Organization[]>([]);

    /** Emitted with the chosen organization when the user selects one. */
    readonly selected = output<Organization>();
    /** Emitted when the user cancels the selection. */
    readonly cancelled = output<void>();

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

    selectOrganization(organization: Organization): void {
        this.selected.emit(organization);
    }

    cancel(): void {
        this.cancelled.emit();
    }
}
