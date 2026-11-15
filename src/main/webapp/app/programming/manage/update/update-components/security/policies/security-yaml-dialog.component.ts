import { Component, computed, effect, input, model, output, signal, untracked } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleCheck, faCircleInfo, faCircleXmark, faCopy, faDownload, faUpload } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiDialogComponent, TumAetUiMessageComponent, TumAetUiProgressSpinnerComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { changedLineNumbers, fileNameOf } from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

/** Which content the dialog shows. */
export type YamlView = 'committed' | 'unsaved';

/** The largest file an instructor can import; the server reads no larger policy either. */
const MAX_IMPORT_SIZE = 256 * 1024;

/** A message about the last import, shown in the dialog. */
export interface SecurityImportNotice {
    severity: 'success' | 'error';
    text: string;
}

/**
 * Shows the YAML file of a security policy read-only, as committed or with the unsaved changes, whose lines are highlighted (screen "View YAML"). The keys are the field names Ares reads, so an instructor can relate the editor to the documentation of Ares. Export downloads the shown file;
 * Import replaces the policy with a YAML file as unsaved changes.
 */
@Component({
    selector: 'jhi-security-yaml-dialog',
    templateUrl: './security-yaml-dialog.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        TumAetUiDialogComponent,
        TumAetUiButtonDirective,
        TumAetUiProgressSpinnerComponent,
        TumAetUiMessageComponent,
        TumAetUiTooltipDirective,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class SecurityYamlDialogComponent {
    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faCircleXmark = faCircleXmark;
    protected readonly faCopy = faCopy;
    protected readonly faDownload = faDownload;
    protected readonly faUpload = faUpload;

    readonly visible = model(false);
    readonly policyPath = input('');
    readonly label = input('');
    /** The committed content; undefined for a new policy. */
    readonly committedContent = input<string | undefined>(undefined);
    /** The content with the unsaved changes, rendered by the server; undefined while it renders. */
    readonly unsavedContent = input<string | undefined>(undefined);
    readonly hasUnsavedChanges = input(false);
    readonly commitHash = input<string | undefined>(undefined);
    /** Whether Import is offered: not for a read-only policy or without the right to change it. */
    readonly canImport = input(true);
    /** What became of the last import, as the parent reports it. */
    readonly notice = input<SecurityImportNotice | undefined>(undefined);

    readonly unsavedRequested = output<void>();
    readonly imported = output<string>();

    readonly view = signal<YamlView>('unsaved');
    readonly copied = signal(false);
    readonly importError = signal<string | undefined>(undefined);

    readonly viewOptions = computed((): { value: YamlView; labelKey: string; disabled: boolean }[] => [
        { value: 'committed', labelKey: 'artemisApp.programmingExercise.security.advanced.yaml.committed', disabled: this.committedContent() === undefined },
        { value: 'unsaved', labelKey: 'artemisApp.programmingExercise.security.advanced.yaml.unsaved', disabled: false },
    ]);

    readonly shownContent = computed(() => (this.view() === 'unsaved' ? this.unsavedContent() : this.committedContent()));

    readonly lines = computed(() => {
        const content = this.shownContent();
        if (content === undefined) {
            return [];
        }
        const highlighted = this.view() === 'unsaved' ? changedLineNumbers(this.committedContent(), content) : new Set<number>();
        const lines = content.split('\n');
        if (lines[lines.length - 1] === '') {
            lines.pop();
        }
        return lines.map((text, index) => ({ number: index + 1, text, highlighted: highlighted.has(index + 1) }));
    });

    readonly fileName = computed(() => fileNameOf(this.policyPath()));
    readonly changedLines = computed(() => this.lines().filter((line) => line.highlighted).length);

    constructor() {
        effect(() => {
            if (this.visible()) {
                untracked(() => {
                    this.importError.set(undefined);
                    this.copied.set(false);
                    this.setView('unsaved');
                });
            }
        });
    }

    protected setView(view: YamlView): void {
        this.view.set(view);
        if (view === 'unsaved' && this.unsavedContent() === undefined) {
            this.unsavedRequested.emit();
        }
    }

    protected async copy(): Promise<void> {
        const content = this.shownContent();
        if (content !== undefined) {
            await navigator.clipboard.writeText(content);
            this.copied.set(true);
            setTimeout(() => this.copied.set(false), 2000);
        }
    }

    protected export(): void {
        const content = this.shownContent();
        if (content === undefined) {
            return;
        }
        const url = URL.createObjectURL(new Blob([content], { type: 'application/yaml' }));
        const link = document.createElement('a');
        link.href = url;
        link.download = this.fileName() || 'SecurityPolicy.yaml';
        link.click();
        URL.revokeObjectURL(url);
    }

    protected async onFileSelected(event: Event): Promise<void> {
        const inputElement = event.target as HTMLInputElement;
        const file = inputElement.files?.[0];
        inputElement.value = '';
        if (!file) {
            return;
        }
        if (file.size > MAX_IMPORT_SIZE) {
            this.importError.set('artemisApp.programmingExercise.security.advanced.yaml.importTooLarge');
            return;
        }
        this.importError.set(undefined);
        this.imported.emit(await file.text());
    }
}
