import { Component, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiSelectComponent, TumAetUiTagComponent, TumAetUiToggleSwitchComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SecurityFrameworkVersionOption } from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityCommit } from 'app/programming/shared/entities/security-policy.model';
import { SecurityHelpComponent } from 'app/programming/manage/update/update-components/security/policies/security-help.component';

/** What the card shows next to the toggle. */
export type SecurityCardState = 'inactive' | 'configured' | 'turningOn' | 'turningOff' | 'committing';

/**
 * The card at the top of the Advanced view (screen "Configure Security"): the Activate Ares2 toggle with its status, dependency scanning, the commit the
 * settings come from and the pinned framework version. It only shows the state and reports what the instructor asks for; the view decides what happens.
 */
@Component({
    selector: 'jhi-security-activation-card',
    templateUrl: './security-activation-card.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        FormsModule,
        FaIconComponent,
        TumAetUiToggleSwitchComponent,
        TumAetUiSelectComponent,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        SecurityHelpComponent,
    ],
})
export class SecurityActivationCardComponent {
    protected readonly faCheck = faCheck;
    protected readonly faSpinner = faSpinner;

    readonly state = input.required<SecurityCardState>();
    /** The position of the toggle; it shows the requested position while a confirmation is open. */
    readonly toggleOn = input(false);
    /** Whether the controls are locked, e.g. while a commit runs or the exercise is saved. */
    readonly locked = input(false);
    readonly scanDependencies = input(false);
    /** Whether the build attaches the Ares agent, which dependency scanning needs. */
    readonly scanAvailable = input(false);
    /** Whether a policy is read-only, so dependency scanning, which changes every policy, cannot be switched either way. */
    readonly scanBlocked = input(false);
    readonly lastCommit = input<SecurityCommit | undefined>(undefined);
    readonly versionOptions = input<SecurityFrameworkVersionOption[]>([]);
    /** The version the select shows: the staged one, or the pinned one. */
    readonly version = input<string | undefined>(undefined);
    /** Whether only the version select is locked, e.g. while the policies whose version it stages are not loaded yet. */
    readonly versionLocked = input(false);
    /** Whether the version differs from the pinned one and is committed with the next commit. */
    readonly versionUncommitted = input(false);

    readonly toggleRequested = output<boolean>();
    readonly scanRequested = output<boolean>();
    readonly versionChange = output<string>();

    protected readonly active = computed(() => this.state() === 'configured' || this.state() === 'committing' || this.state() === 'turningOff');
    protected readonly lastCommitDate = computed(() => {
        const date = this.lastCommit()?.date;
        return date ? dayjs(date) : undefined;
    });

    protected onToggle(checked: boolean): void {
        this.toggleRequested.emit(checked);
    }

    protected onScan(checked: boolean): void {
        this.scanRequested.emit(checked);
    }

    protected onVersion(version: unknown): void {
        if (typeof version === 'string') {
            this.versionChange.emit(version);
        }
    }
}
