import { Directive, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TumAetUiTableQueryEvent } from '@tumaet/ui-angular';
import { OrganizationManagementService } from './organization-management.service';
import { Organization } from './organization.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { buildDbQueryFromTableEvent } from 'app/shared-ui/tum-aet-ui-integration/tumaet-ui-table-request-builder';

/** Common organization pagination with protection against stale responses. */
@Directive()
export abstract class OrganizationListDirective {
    protected readonly organizationService = inject(OrganizationManagementService);
    private readonly alertService = inject(AlertService);

    readonly organizations = signal<Organization[]>([]);
    readonly totalCount = signal(0);
    readonly isLoading = signal(false);

    private loadRequestId = 0;

    loadOrganizations(event: TumAetUiTableQueryEvent, withCounts = false): void {
        this.isLoading.set(true);
        const requestId = ++this.loadRequestId;
        const query = buildDbQueryFromTableEvent(event);
        const request = withCounts ? this.organizationService.getOrganizations(query, true) : this.organizationService.getOrganizations(query);
        request.subscribe({
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
}
