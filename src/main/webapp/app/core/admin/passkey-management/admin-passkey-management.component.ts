import { Component, OnInit, inject, signal } from '@angular/core';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { AdminPasskeyManagementService } from './admin-passkey-management.service';
import { AdminPasskeyDTO } from './admin-passkey.dto';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-admin-passkey-management',
    templateUrl: './admin-passkey-management.component.html',
    imports: [NgxDatatableModule, ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective],
})
export class AdminPasskeyManagementComponent implements OnInit {
    private adminPasskeyService = inject(AdminPasskeyManagementService);
    private alertService = inject(AlertService);

    passkeys = signal<AdminPasskeyDTO[]>([]);
    isLoading = signal<boolean>(false);

    ngOnInit(): void {
        this.loadPasskeys();
    }

    loadPasskeys(): void {
        this.isLoading.set(true);
        this.adminPasskeyService.getAllPasskeys().subscribe({
            next: (passkeys) => {
                this.passkeys.set(passkeys);
                this.isLoading.set(false);
            },
            error: (error: HttpErrorResponse) => {
                this.isLoading.set(false);
                onError(this.alertService, error);
            },
        });
    }

    async onApprovalToggle(passkey: AdminPasskeyDTO): Promise<void> {
        const newApprovalStatus = !passkey.isSuperAdminApproved;

        try {
            await this.adminPasskeyService.updatePasskeyApproval(passkey.credentialId, newApprovalStatus);
            passkey.isSuperAdminApproved = newApprovalStatus;
            this.passkeys.update((passkeys) => [...passkeys]);
        } catch (error) {
            onError(this.alertService, error);
        }
    }
}
