import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import {
    TumAetUiInputDirective,
    TumAetUiSelectButtonComponent,
    TumAetUiSelectComponent,
    TumAetUiTabComponent,
    TumAetUiTabListComponent,
    TumAetUiTabsComponent,
    TumAetUiTagComponent,
    TumAetUiToggleSwitchComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';
import { SecurityStrictTimeout, SecurityTestBehavior } from 'app/programming/shared/entities/security-policy.model';
import { SecurityHelpComponent } from 'app/programming/manage/update/update-components/security/policies/security-help.component';
import {
    BEHAVIOUR_CATEGORY_OF_TAB,
    BEHAVIOUR_TABS,
    BehaviourCategory,
    BehaviourTab,
    TIME_UNITS,
    formatAresDate,
    isSameValue,
    parseAresDate,
    setBehaviour,
} from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

/** The error feedback levels, from the least to the most information, as the options list them from left to right. */
const FEEDBACK_LEVELS = ['I0', 'I1', 'I2', 'I3'] as const;

/** The level the released Ares versions use: they report the full message of a violation, without the rule that would allow it. */
const RELEASED_FEEDBACK_LEVEL: (typeof FEEDBACK_LEVELS)[number] = 'I2';

const DEFAULT_TIMEOUT: SecurityStrictTimeout = { timeout: 5, timeUnit: 'SECONDS', terminationGrace: 1, terminationGraceUnit: 'SECONDS' };

const DEFAULT_MAXIMUM_CHARACTERS = 100_000_000;

/** The format of a {@code datetime-local} input. */
const INPUT_DATE_FORMAT = 'YYYY-MM-DDTHH:mm';

/**
 * The content of the Configure Test Behaviour box of a security policy (screens "Security Feedback", "Standard Feedback", "Visibility", "Timeout" and
 * "Output"). Each tab edits one category of the test behaviour of the policy and marks itself while it differs from the commit. The error feedback level has
 * no field in any Ares release yet, so its slider shows the level Ares uses and cannot be moved.
 */
@Component({
    selector: 'jhi-security-test-behaviour',
    templateUrl: './security-test-behaviour.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        FormsModule,
        TumAetUiTabsComponent,
        TumAetUiTabListComponent,
        TumAetUiTabComponent,
        TumAetUiTagComponent,
        TumAetUiToggleSwitchComponent,
        TumAetUiSelectButtonComponent,
        TumAetUiSelectComponent,
        TumAetUiInputDirective,
        TumAetUiTooltipDirective,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        SecurityHelpComponent,
    ],
})
export class SecurityTestBehaviourComponent {
    private readonly translateService = inject(TranslateService);

    protected readonly faCircleXmark = faCircleXmark;
    protected readonly TABS = BEHAVIOUR_TABS;
    protected readonly FEEDBACK_LEVELS = FEEDBACK_LEVELS;
    protected readonly feedbackLevel = RELEASED_FEEDBACK_LEVEL;

    /** The behaviour with the unsaved changes. */
    readonly behavior = input<SecurityTestBehavior | undefined>(undefined);
    /** The committed behaviour. */
    readonly baseBehavior = input<SecurityTestBehavior | undefined>(undefined);
    readonly disabled = input(false);
    /** The file name of the policy, which the preview of a security violation names. */
    readonly policyFile = input('');
    /** The id prefix of the fields, unique per policy. */
    readonly idPrefix = input('securityBehaviour');

    readonly behaviorChange = output<SecurityTestBehavior | undefined>();

    readonly activeTab = signal<BehaviourTab>('securityFeedback');

    readonly timeUnitOptions = computed(() =>
        TIME_UNITS.map((unit) => ({ value: unit, label: this.translateService.instant(`artemisApp.programmingExercise.security.advanced.behaviour.unit.${unit}`) })),
    );

    readonly privilegedExceptions = computed(() => this.behavior()?.privilegedExceptions);
    readonly hiddenTests = computed(() => this.behavior()?.hiddenTests);
    readonly strictTimeout = computed(() => this.behavior()?.strictTimeout);
    readonly outputMirroring = computed(() => this.behavior()?.outputMirroring);

    /** The values the fields show; a switched-off category shows its defaults, disabled. */
    readonly shownTimeout = computed(() => this.strictTimeout() ?? DEFAULT_TIMEOUT);
    readonly shownMaximumCharacters = computed(() => this.outputMirroring()?.maximumCharacters ?? DEFAULT_MAXIMUM_CHARACTERS);
    readonly shownMessage = computed(() => this.privilegedExceptions()?.failureMessage ?? '');

    readonly hiddenFromInput = computed(() => parseAresDate(this.hiddenTests()?.hiddenFrom)?.format(INPUT_DATE_FORMAT) ?? '');
    readonly hiddenUntilInput = computed(() => parseAresDate(this.hiddenTests()?.hiddenUntil)?.format(INPUT_DATE_FORMAT) ?? '');
    /** Whether the hidden period ends before it starts. */
    readonly hiddenPeriodInvalid = computed(() => {
        const from = parseAresDate(this.hiddenTests()?.hiddenFrom);
        const until = parseAresDate(this.hiddenTests()?.hiddenUntil);
        return !!from && !!until && !until.isAfter(from);
    });

    /** The tabs whose category differs from the committed policy. */
    readonly uncommittedTabs = computed(() => new Set(BEHAVIOUR_TABS.filter((tab) => this.isUncommitted(tab))));

    /** Whether the category of a tab differs from the committed policy. */
    isUncommitted(tab: BehaviourTab): boolean {
        const category = BEHAVIOUR_CATEGORY_OF_TAB[tab];
        return category !== undefined && !isSameValue(this.behavior()?.[category], this.baseBehavior()?.[category]);
    }

    protected onTabChange(value: string | number | undefined): void {
        if (BEHAVIOUR_TABS.includes(value as BehaviourTab)) {
            this.activeTab.set(value as BehaviourTab);
        }
    }

    /** Emits the behaviour with one category replaced; an empty behaviour becomes undefined, so the policy gets no section. */
    private update<C extends BehaviourCategory>(category: C, value: SecurityTestBehavior[C] | undefined): void {
        const behaviour: SecurityTestBehavior = deepClone(this.behavior() ?? {});
        setBehaviour(behaviour, category, value);
        this.behaviorChange.emit(Object.keys(behaviour).length ? behaviour : undefined);
    }

    /** Switching a category off restores the committed value if it was switched off there, so switching on and off again is no change. */
    private offValue<C extends 'privilegedExceptions' | 'outputMirroring'>(category: C): SecurityTestBehavior[C] | undefined {
        const committed = this.baseBehavior()?.[category];
        if (category === 'privilegedExceptions') {
            return this.baseBehavior()?.privilegedExceptions?.enabled === false ? committed : undefined;
        }
        return this.baseBehavior()?.outputMirroring?.mirrored === false ? committed : undefined;
    }

    protected setReplaceExceptions(enabled: boolean): void {
        if (enabled) {
            this.update('privilegedExceptions', { enabled: true, failureMessage: this.privilegedExceptions()?.failureMessage });
        } else {
            this.update('privilegedExceptions', this.offValue('privilegedExceptions'));
        }
    }

    protected setFailureMessage(message: string): void {
        const current = this.privilegedExceptions() ?? { enabled: true };
        this.update('privilegedExceptions', cloneWith(current, { failureMessage: message.length ? message : undefined }));
    }

    protected setHideTests(hidden: boolean): void {
        if (hidden) {
            const from = dayjs().startOf('minute');
            this.update('hiddenTests', {
                hiddenFrom: this.hiddenTests()?.hiddenFrom ?? formatAresDate(from),
                hiddenUntil: this.hiddenTests()?.hiddenUntil ?? formatAresDate(from.add(7, 'day').endOf('day').startOf('minute')),
            });
        } else {
            this.update('hiddenTests', undefined);
        }
    }

    protected onHiddenDateChange(field: 'hiddenFrom' | 'hiddenUntil', event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!this.setHiddenDate(field, input.value)) {
            // A refused date, e.g. a cleared end, would otherwise stay on screen while the stored one is saved.
            input.value = field === 'hiddenFrom' ? this.hiddenFromInput() : this.hiddenUntilInput();
        }
    }

    /** @returns whether the date was applied; an invalid date and a cleared end are refused, because the hidden period needs an end */
    private setHiddenDate(field: 'hiddenFrom' | 'hiddenUntil', value: string): boolean {
        const hidden = this.hiddenTests();
        const date = dayjs(value, INPUT_DATE_FORMAT);
        if (!hidden || (value && !date.isValid())) {
            return false;
        }
        if (field === 'hiddenFrom') {
            this.update('hiddenTests', cloneWith(hidden, { hiddenFrom: value ? formatAresDate(date) : undefined }));
            return true;
        }
        if (!value) {
            return false;
        }
        this.update('hiddenTests', cloneWith(hidden, { hiddenUntil: formatAresDate(date) }));
        return true;
    }

    protected setStrictTimeout(enabled: boolean): void {
        this.update('strictTimeout', enabled ? (this.strictTimeout() ?? deepClone(DEFAULT_TIMEOUT)) : undefined);
    }

    protected setTimeoutField(field: 'timeout' | 'terminationGrace', value: string | number | null): void {
        const current = this.strictTimeout();
        if (!current) {
            return;
        }
        // A cleared number input emits null.
        const parsed = value === null || String(value).trim() === '' ? undefined : Number(value);
        if (field === 'timeout') {
            if (parsed !== undefined && Number.isInteger(parsed) && parsed > 0) {
                this.update('strictTimeout', cloneWith(current, { timeout: parsed }));
            }
        } else if (parsed === undefined || (Number.isInteger(parsed) && parsed >= 0)) {
            this.update('strictTimeout', cloneWith(current, { terminationGrace: parsed }));
        }
    }

    protected setTimeoutUnit(field: 'timeUnit' | 'terminationGraceUnit', unit: unknown): void {
        const current = this.strictTimeout();
        if (current && typeof unit === 'string') {
            this.update('strictTimeout', field === 'timeUnit' ? cloneWith(current, { timeUnit: unit }) : cloneWith(current, { terminationGraceUnit: unit }));
        }
    }

    protected setMirrorOutput(mirrored: boolean): void {
        if (mirrored) {
            this.update('outputMirroring', { mirrored: true, maximumCharacters: this.outputMirroring()?.maximumCharacters ?? DEFAULT_MAXIMUM_CHARACTERS });
        } else {
            this.update('outputMirroring', this.offValue('outputMirroring'));
        }
    }

    /**
     * Shows the stored value again when a number field is left, so that a refused input, e.g. 0, 1.5 or a cleared required field, does not stay on screen
     * while the stored value is saved.
     */
    protected showStored(event: Event, stored: number | undefined): void {
        const input = event.target as HTMLInputElement;
        const shown = stored === undefined ? '' : String(stored);
        if (input.value !== shown) {
            input.value = shown;
        }
    }

    protected setMaximumCharacters(value: string | number | null): void {
        const current = this.outputMirroring();
        // A cleared number input emits null; the stored maximum stays until a valid one is entered.
        if (value === null) {
            return;
        }
        // A number from the number input is checked as it is, so 1.5 is refused; only text may group its digits, e.g. 1_000_000.
        const parsed = typeof value === 'number' ? value : Number(value.replace(/[\s_.,]/g, ''));
        if (current && Number.isInteger(parsed) && parsed > 0) {
            this.update('outputMirroring', cloneWith(current, { maximumCharacters: parsed }));
        }
    }
}
