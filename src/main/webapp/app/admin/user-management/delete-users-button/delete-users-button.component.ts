import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { faEraser } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiButtonComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';

/**
 * Loads not-enrolled deletion candidates and delegates them to the parent component's authoritative impact preview.
 */
@Component({
    selector: 'jhi-delete-users-button',
    templateUrl: './delete-users-button.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiButtonComponent, FaIconComponent, TranslateDirective],
})
export class DeleteUsersButtonComponent {
    private readonly adminUserService = inject(AdminUserService);
    private readonly alertService = inject(AlertService);
    /** Emitted after candidate discovery so the parent can load and confirm the authoritative impact preview. */
    readonly deletionRequested = output<string[]>();

    /** List of users to be deleted */
    readonly users = signal<string[] | undefined>(undefined);

    /** Icons */
    protected readonly faEraser = faEraser;

    /**
     * Load the candidate list and request the impact preview used for confirmation.
     */
    loadUserList() {
        this.adminUserService.queryNotEnrolledUsers().subscribe({
            next: (res: HttpResponse<string[]>) => {
                const users = res.body!;
                this.users.set(users);
                if (users.length === 0) {
                    this.alertService.info('artemisApp.userManagement.notEnrolled.delete.cancel');
                } else {
                    this.openDeleteDialog();
                }
            },
            error: (res: HttpErrorResponse) => {
                onError(this.alertService, res);
            },
        });
    }

    /**
     * Delegates to the user-management impact dialog. This component must not bypass preview by deleting directly.
     */
    openDeleteDialog() {
        this.deletionRequested.emit(this.users() ?? []);
    }
}
