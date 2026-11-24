import { Component, computed, effect, input, model, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleInfo, faCircleXmark, faMagnifyingGlass, faPlus, faShieldHalved, faTriangleExclamation, faUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';
import { faTrashCan } from '@fortawesome/free-regular-svg-icons';
import { TumAetUiButtonDirective, TumAetUiDialogComponent, TumAetUiMessageComponent, TumAetUiSelectComponent } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SecurityAresApiUse, SecurityFrameworkVersionOption } from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityHelpComponent } from 'app/programming/manage/update/update-components/security/policies/security-help.component';
import { countKey } from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

/** What the dialog confirms: turning the sandbox or dependency scanning on or off. */
export type SecurityActivationMode = 'activate' | 'deactivate' | 'scanOn' | 'scanOff';

/**
 * Confirms a change of the Advanced view that is committed at once (screens "Turn On Ares2", "Turn Off Ares2" and the dependency scanning dialogs). It
 * lists what the one commit changes in the tests repository and, when turning the sandbox on, lets the instructor pick the version to pin.
 */
@Component({
    selector: 'jhi-security-activation-dialog',
    templateUrl: './security-activation-dialog.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        FormsModule,
        RouterLink,
        TumAetUiDialogComponent,
        TumAetUiButtonDirective,
        TumAetUiMessageComponent,
        TumAetUiSelectComponent,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        SecurityHelpComponent,
    ],
})
export class SecurityActivationDialogComponent {
    protected readonly countKey = countKey;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faCircleXmark = faCircleXmark;
    protected readonly faMagnifyingGlass = faMagnifyingGlass;
    protected readonly faPlus = faPlus;
    protected readonly faShieldHalved = faShieldHalved;
    protected readonly faTrashCan = faTrashCan;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faUpRightFromSquare = faUpRightFromSquare;

    readonly visible = model(false);
    readonly mode = input<SecurityActivationMode>('activate');
    /** The versions the instructor can pin. */
    readonly versionOptions = input<SecurityFrameworkVersionOption[]>([]);
    /** The version selected when the dialog opens. */
    readonly initialVersion = input<string | undefined>(undefined);
    readonly latestVersion = input<string | undefined>(undefined);
    /** The policy files the commit deletes when turning the sandbox off. */
    readonly policyPaths = input<string[]>([]);
    /** The parts of Ares the tests use besides the sandbox. */
    readonly aresApiUses = input<SecurityAresApiUse[]>([]);
    /** The route to the tests repository; undefined where there is none. */
    readonly repositoryLink = input<(string | number)[] | undefined>(undefined);
    /** The number of unsaved changes; turning the sandbox off discards them. */
    readonly unsavedChanges = input(0);
    /** Whether a policy enforces a strict timeout, which slower test cases may exceed. */
    readonly strictTimeouts = input(false);
    /** Whether the confirmation has to wait, e.g. while the policies reload, so the change it commits is based on the reloaded state. */
    readonly busy = input(false);

    /** Emits the version to pin when turning the sandbox on, and undefined otherwise. */
    readonly confirmed = output<string | undefined>();
    readonly cancelled = output<void>();

    readonly version = signal<string | undefined>(undefined);

    /** The test files that use other parts of Ares, by their file name, each once. */
    readonly apiFiles = computed(() => [...new Set(this.aresApiUses().map((use) => use.location.substring(use.location.lastIndexOf('/') + 1).replace(/\.java$/, '')))]);
    readonly apis = computed(() => [...new Set(this.aresApiUses().map((use) => use.api))]);

    private confirming = false;

    constructor() {
        effect(() => {
            if (this.visible()) {
                const initial = this.initialVersion();
                untracked(() => {
                    this.confirming = false;
                    this.version.set(initial);
                });
            }
        });
    }

    protected onVersionChange(version: unknown): void {
        if (typeof version === 'string') {
            this.version.set(version);
        }
    }

    protected confirm(): void {
        this.confirming = true;
        this.confirmed.emit(this.mode() === 'activate' ? this.version() : undefined);
        this.visible.set(false);
    }

    /** Closing the dialog in any other way than confirming cancels it, so the toggle returns to its position. */
    protected onHidden(): void {
        if (!this.confirming) {
            this.cancelled.emit();
        }
    }
}
