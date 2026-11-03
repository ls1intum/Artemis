import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { faBan, faPlus, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiFormFieldComponent,
    TumAetUiInputDirective,
    TumAetUiListComponent,
    TumAetUiListItemDirective,
    TumAetUiTableDirective,
} from '@tumaet/ui-angular';
import { Subject } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { CopyToClipboardButtonComponent } from 'app/shared-ui/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';
import { PersonalAccessTokenApiService } from 'app/account/user/settings/agent-access-settings/personal-access-token-api.service';
import { PersonalAccessTokenDTO } from 'app/account/user/settings/agent-access-settings/dto/personal-access-token.dto';

const DEFAULT_LIFETIME_IN_DAYS = 30;

@Component({
    selector: 'jhi-agent-access-settings',
    templateUrl: './agent-access-settings.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [
        TranslateDirective,
        FaIconComponent,
        DeleteButtonDirective,
        TumAetUiButtonComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiFormFieldComponent,
        TumAetUiInputDirective,
        TumAetUiListComponent,
        TumAetUiListItemDirective,
        TumAetUiTableDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        CopyToClipboardButtonComponent,
    ],
})
export class AgentAccessSettingsComponent implements OnInit {
    private readonly api = inject(PersonalAccessTokenApiService);
    private readonly alertService = inject(AlertService);

    protected readonly faPlus = faPlus;
    protected readonly faSave = faSave;
    protected readonly faTrash = faTrash;
    protected readonly faBan = faBan;

    readonly tokens = signal<PersonalAccessTokenDTO[]>([]);
    readonly availableScopes = signal<string[]>([]);
    readonly creating = signal(false);
    readonly tokenName = signal('');
    readonly selectedScopes = signal<string[]>([]);
    readonly lifetimeInDays = signal(DEFAULT_LIFETIME_IN_DAYS);
    /** The secret of the token that was just created. It is shown once and dropped when the page is left. */
    readonly createdSecret = signal<string | undefined>(undefined);

    readonly canCreate = computed(() => this.tokenName().trim().length > 0 && this.selectedScopes().length > 0 && this.lifetimeInDays() >= 1);

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        void this.loadData();
    }

    startCreation() {
        this.createdSecret.set(undefined);
        this.creating.set(true);
    }

    cancelCreation() {
        this.creating.set(false);
        this.tokenName.set('');
        this.selectedScopes.set([]);
        this.lifetimeInDays.set(DEFAULT_LIFETIME_IN_DAYS);
    }

    setScopeSelected(scope: string, selected: boolean) {
        this.selectedScopes.update((scopes) => (selected ? [...scopes.filter((existing) => existing !== scope), scope] : scopes.filter((existing) => existing !== scope)));
    }

    onLifetimeInput(value: string) {
        this.lifetimeInDays.set(Number.parseInt(value, 10) || 0);
    }

    async createToken() {
        if (!this.canCreate()) {
            return;
        }
        try {
            const created = await this.api.createToken({ name: this.tokenName().trim(), scopes: this.selectedScopes(), expiresInDays: this.lifetimeInDays() });
            this.createdSecret.set(created.secret);
            this.cancelCreation();
            this.alertService.success('artemisApp.userSettings.agentAccess.createSuccess');
            await this.loadTokens();
        } catch {
            this.alertService.error('artemisApp.userSettings.agentAccess.createFailure');
        }
    }

    async revokeToken(tokenId: number) {
        try {
            await this.api.revokeToken(tokenId);
            this.alertService.success('artemisApp.userSettings.agentAccess.revokeSuccess');
            await this.loadTokens();
        } catch {
            this.alertService.error('artemisApp.userSettings.agentAccess.revokeFailure');
        }
        this.dialogErrorSource.next('');
    }

    /** Scope names contain a colon, which is not safe in a translation key path, so it becomes an underscore. */
    scopeLabelKey(scope: string): string {
        return `artemisApp.userSettings.agentAccess.scopes.${scope.replace(':', '_')}`;
    }

    private async loadData() {
        try {
            const [tokens, scopes] = await Promise.all([this.api.getTokens(), this.api.getScopes()]);
            this.tokens.set(tokens);
            this.availableScopes.set(scopes);
        } catch {
            this.alertService.error('artemisApp.userSettings.agentAccess.loadFailure');
        }
    }

    private async loadTokens() {
        try {
            this.tokens.set(await this.api.getTokens());
        } catch {
            this.alertService.error('artemisApp.userSettings.agentAccess.loadFailure');
        }
    }
}
