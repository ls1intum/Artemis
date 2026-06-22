import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { AdminPasskeyManagementService } from './admin-passkey-management.service';
import { AdminPasskeyDTO } from './admin-passkey.dto';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { isErrorAlert, onError } from 'app/foundation/util/global.utils';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';

@Component({
    selector: 'jhi-admin-passkey-management',
    templateUrl: './admin-passkey-management.component.html',
    imports: [ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective, TableViewComponent, FormsModule, ToggleSwitchModule, AdminTitleBarTitleDirective],
})
export class AdminPasskeyManagementComponent implements OnInit {
    private readonly adminPasskeyService = inject(AdminPasskeyManagementService);
    private readonly alertService = inject(AlertService);

    passkeys = signal<AdminPasskeyDTO[]>([]);
    isLoading = signal<boolean>(false);

    private readonly userNameCellTemplate = viewChild<CellTemplateRef<AdminPasskeyDTO>>('userNameCellTemplate');
    private readonly labelCellTemplate = viewChild<CellTemplateRef<AdminPasskeyDTO>>('labelCellTemplate');
    private readonly createdCellTemplate = viewChild<CellTemplateRef<AdminPasskeyDTO>>('createdCellTemplate');
    private readonly lastUsedCellTemplate = viewChild<CellTemplateRef<AdminPasskeyDTO>>('lastUsedCellTemplate');
    private readonly approvedCellTemplate = viewChild<CellTemplateRef<AdminPasskeyDTO>>('approvedCellTemplate');

    readonly tableOptions: TableViewOptions = {
        lazy: false,
        showSearch: false,
        pageSize: 20,
        hidePageSizeOptions: true,
        striped: true,
    };

    readonly columns = computed<ColumnDef<AdminPasskeyDTO>[]>(() => [
        { field: 'userLogin', headerKey: 'artemisApp.adminPasskeyManagement.userLogin', sort: true },
        { field: 'userName', headerKey: 'artemisApp.adminPasskeyManagement.userName', sort: true, templateRef: this.userNameCellTemplate() },
        { field: 'label', headerKey: 'artemisApp.adminPasskeyManagement.label', sort: true, templateRef: this.labelCellTemplate() },
        { field: 'created', headerKey: 'artemisApp.adminPasskeyManagement.created', sort: true, templateRef: this.createdCellTemplate() },
        { field: 'lastUsed', headerKey: 'artemisApp.adminPasskeyManagement.lastUsed', sort: true, templateRef: this.lastUsedCellTemplate() },
        { field: 'isSuperAdminApproved', headerKey: 'artemisApp.adminPasskeyManagement.approved', templateRef: this.approvedCellTemplate() },
    ]);

    ngOnInit(): void {
        this.loadPasskeys().then();
    }

    /**
     * @param showLoading can be set as false to avoid flickering on silent loading in background
     */
    async loadPasskeys(showLoading: boolean = true): Promise<void> {
        if (showLoading) {
            this.isLoading.set(true);
        }

        try {
            const loadedPasskeys = await this.adminPasskeyService.getAllPasskeys();
            this.passkeys.set(loadedPasskeys);
        } catch (error) {
            onError(this.alertService, error);
        }

        this.isLoading.set(false);
    }

    async onApprovalToggle(passkey: AdminPasskeyDTO): Promise<void> {
        const newApprovalStatus = !passkey.isSuperAdminApproved;

        try {
            await this.adminPasskeyService.updatePasskeyApproval(passkey.credentialId, newApprovalStatus);
            passkey.isSuperAdminApproved = newApprovalStatus;
            this.passkeys.update((passkeys) => [...passkeys]);
        } catch (error) {
            if (!isErrorAlert(error)) {
                if (error.status === 404) {
                    this.alertService.error('artemisApp.adminPasskeyManagement.errors.passkeyNotFound');
                    return;
                }

                onError(this.alertService, error);
            }

            await this.loadPasskeys(false);
        }
    }
}
