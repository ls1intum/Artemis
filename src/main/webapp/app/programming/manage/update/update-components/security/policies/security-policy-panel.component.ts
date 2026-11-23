import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCode, faLock } from '@fortawesome/free-solid-svg-icons';
import { faTrashCan } from '@fortawesome/free-regular-svg-icons';
import {
    TumAetUiButtonDirective,
    TumAetUiInputDirective,
    TumAetUiMessageComponent,
    TumAetUiMultiSelectComponent,
    TumAetUiPanelComponent,
    TumAetUiTagComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { SecurityPolicyDocument, SecurityPreset, SecurityTestBehavior, SecurityTestStructure } from 'app/programming/shared/entities/security-policy.model';
import { SecurityHelpComponent } from 'app/programming/manage/update/update-components/security/policies/security-help.component';
import { SecurityResourceAccessComponent } from 'app/programming/manage/update/update-components/security/policies/security-resource-access.component';
import { SecurityTestBehaviourComponent } from 'app/programming/manage/update/update-components/security/policies/security-test-behaviour.component';
import { SecurityTestCasesComponent } from 'app/programming/manage/update/update-components/security/policies/security-test-cases.component';
import {
    EditorPolicy,
    PolicyChangeSummary,
    countKey,
    fileNameOf,
    withAssignment,
} from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

/** The boxes of a policy, which open and close on their own. */
export type PolicyBox = 'resources' | 'behaviour' | 'tests';

/** Numbers the panels of the page, see {@link SecurityPolicyPanelComponent.idPrefix}. */
let panelCounter = 0;

/**
 * One security policy of the Manage Security Policies section (screen "Security Policy"): its file and unsaved changes in the header, its name and presets,
 * the supervised code it applies to, and the boxes Allow Resource Access, Configure Test Behaviour and Assign Test Cases. A policy the editor cannot keep
 * completely is shown read-only, with the reason.
 */
const PREFIX = 'artemisApp.programmingExercise.security.advanced.policy';

@Component({
    selector: 'jhi-security-policy-panel',
    templateUrl: './security-policy-panel.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        FormsModule,
        TumAetUiPanelComponent,
        TumAetUiButtonDirective,
        TumAetUiInputDirective,
        TumAetUiMultiSelectComponent,
        TumAetUiTagComponent,
        TumAetUiMessageComponent,
        TumAetUiTooltipDirective,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        SecurityHelpComponent,
        SecurityResourceAccessComponent,
        SecurityTestBehaviourComponent,
        SecurityTestCasesComponent,
    ],
})
export class SecurityPolicyPanelComponent {
    protected readonly faCode = faCode;
    protected readonly faLock = faLock;
    protected readonly faTrashCan = faTrashCan;

    readonly policy = input.required<EditorPolicy>();
    readonly summary = input.required<PolicyChangeSummary>();
    /** The translation keys of the tooltips of the change counts of the policy and of its boxes, in the singular for exactly one change. */
    protected readonly changesHelpKeys = computed(() => {
        const summary = this.summary();
        return {
            policy: countKey(`${PREFIX}.changesHelp`, summary.total),
            resources: countKey(`${PREFIX}.boxChangesHelp`, summary.restrictions),
            behaviour: countKey(`${PREFIX}.boxChangesHelp`, summary.behaviours),
            tests: countKey(`${PREFIX}.boxChangesHelp`, summary.testCases + summary.testClasses),
        };
    });
    /** The path the policy is saved to with the unsaved changes. */
    readonly policyPath = input('');
    readonly presets = input<SecurityPreset[]>([]);
    readonly structure = input<SecurityTestStructure | undefined>(undefined);
    /** The label of the policy that captures each test of the other policies. */
    readonly otherTests = input<Map<string, string>>(new Map());
    readonly templateFiles = input<string[] | undefined>(undefined);
    readonly templateFilesTruncated = input(false);
    readonly templateFilesFailed = input(false);
    readonly disabled = input(false);

    readonly policyChange = output<EditorPolicy>();
    readonly viewYaml = output<void>();
    readonly deleteRequested = output<void>();
    readonly structureRequested = output<void>();
    readonly templateFilesRequested = output<void>();

    readonly collapsed = signal(true);
    readonly boxCollapsed = signal<Record<PolicyBox, boolean>>({ resources: true, behaviour: true, tests: true });

    readonly working = computed(() => this.policy().working);
    readonly fileName = computed(() => fileNameOf(this.policyPath()) || fileNameOf(this.policy().file?.policyPath ?? ''));
    readonly readOnly = computed(() => !this.policy().working);
    readonly selectedPresets = computed(() => {
        const preset = this.working()?.preset;
        return preset ? [preset] : [];
    });
    readonly preset = computed(() => this.presets().find((candidate) => candidate.name === this.working()?.preset));
    /** The id prefix of the fields of this policy, unique on the page. */
    /** Unique per panel, so the labels of two panels never point to each other's controls, whatever the paths of their policies. */
    readonly idPrefix = signal(`securityPolicy${++panelCounter}`);

    protected setBoxCollapsed(box: PolicyBox, collapsed: boolean): void {
        this.boxCollapsed.update((boxes) => cloneWith(boxes, { [box]: collapsed }));
    }

    protected onNameChange(name: string): void {
        this.policyChange.emit(cloneWith(this.policy(), { name }));
    }

    /** Ares bases a policy on one preset, so the latest choice replaces an earlier one. */
    protected onPresetsChange(values: unknown[]): void {
        const names = values.filter((value): value is string => typeof value === 'string');
        const working = this.working();
        if (working) {
            this.updateWorking(cloneWith(working, { preset: names.at(-1) }));
        }
    }

    protected onDocumentChange(document: SecurityPolicyDocument): void {
        this.updateWorking(document);
    }

    protected onBehaviorChange(behavior: SecurityTestBehavior | undefined): void {
        const working = this.working();
        if (working) {
            this.updateWorking(cloneWith(working, { testBehavior: behavior }));
        }
    }

    protected onAssignmentChange(change: { tests: string[]; extraClasses: string[] }): void {
        this.policyChange.emit(withAssignment(this.policy(), change.tests, change.extraClasses));
    }

    private updateWorking(working: SecurityPolicyDocument): void {
        this.policyChange.emit(cloneWith(this.policy(), { working }));
    }
}
