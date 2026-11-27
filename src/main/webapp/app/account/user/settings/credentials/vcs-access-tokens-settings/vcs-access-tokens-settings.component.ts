import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, Subscription, tap } from 'rxjs';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiListComponent, TumAetUiListItemDirective, TumAetUiTableDirective } from '@tumaet/ui-angular';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';

/**
 * Shows the deprecation notice of the personal VCS access token and lets the user revoke a token that still exists.
 * New personal tokens cannot be created any more; the repository access tokens replace them.
 */
@Component({
    selector: 'jhi-vcs-access-tokens-settings',
    templateUrl: './vcs-access-tokens-settings.component.html',
    styleUrls: ['../../user-settings.css'],
    imports: [
        TranslateDirective,
        FaIconComponent,
        DeleteButtonDirective,
        TumAetUiButtonDirective,
        TumAetUiListComponent,
        TumAetUiListItemDirective,
        TumAetUiTableDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
})
export class VcsAccessTokensSettingsComponent implements OnInit, OnDestroy {
    private accountService = inject(AccountService);
    private alertService = inject(AlertService);

    protected readonly faTrash = faTrash;

    // `equal: () => false` so re-setting the same reference emits after the token fields are cleared in place.
    readonly currentUser = signal<User | undefined>(undefined, { equal: () => false });
    /** The server only sends the expiry date of the personal token, which is set exactly when the user still has one (even an expired one). */
    readonly hasPersonalToken = computed(() => !!this.currentUser()?.vcsAccessTokenExpiryDate);

    private authStateSubscription!: Subscription; // assigned in ngOnInit(), before ngOnDestroy() unsubscribes

    private dialogErrorSource = new Subject<string>();

    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User | undefined) => {
                    this.currentUser.set(user);
                    return user;
                }),
            )
            .subscribe();
    }

    ngOnDestroy(): void {
        this.authStateSubscription.unsubscribe();
    }

    deleteVcsAccessToken() {
        this.accountService.deleteUserVcsAccessToken().subscribe({
            next: () => {
                this.clearRevokedToken();
                this.alertService.success('artemisApp.userSettings.vcsAccessTokensSettingsPage.deleteSuccess');
            },
            error: () => {
                this.alertService.error('artemisApp.userSettings.vcsAccessTokensSettingsPage.deleteFailure');
            },
        });
        this.dialogErrorSource.next('');
    }

    clearRevokedToken(): void {
        const current = this.currentUser();
        if (current) {
            current.vcsAccessTokenExpiryDate = undefined;
            this.currentUser.set(current);
        }
    }
}
