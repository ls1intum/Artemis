import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiButtonDirective, TumAetUiCheckboxComponent, TumAetUiDialogComponent, TumAetUiInputDirective } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import {
    SecurityCommandPermission,
    SecurityFilePermission,
    SecurityNetworkPermission,
    SecurityPackagePermission,
    SecurityThreadPermission,
} from 'app/programming/shared/entities/security-policy.model';
import { SecurityBrowseDialogComponent, SecurityBrowseMode } from 'app/programming/manage/update/update-components/security/policies/security-browse-dialog.component';
import { ResourceKind, ResourceRow, joinArguments, rowKey, splitArguments } from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

/** The actions a file permission grants, in the order Ares lists them. */
export const FILE_ACTIONS = ['read', 'overwrite', 'create', 'execute', 'delete'] as const;
export type FileAction = (typeof FILE_ACTIONS)[number];

/** The actions a network permission grants. */
export const NETWORK_ACTIONS = ['open', 'send', 'receive'] as const;
export type NetworkAction = (typeof NETWORK_ACTIONS)[number];

/** One part of a fully qualified Java name, with the characters Java allows in identifiers, e.g. {@code ArbeiterÄ}; the server checks reserved words. */
const IDENTIFIER = /^[\p{L}\p{Nl}\p{Sc}\p{Pc}][\p{L}\p{Nl}\p{Sc}\p{Pc}\p{Nd}\p{Mn}\p{Mc}]*$/u;

/** Whether the text is a fully qualified Java name, a package or a class: identifiers separated by dots. */
function isQualifiedName(text: string): boolean {
    return text.split('.').every((part) => IDENTIFIER.test(part));
}

/** The special thread tokens Ares accepts besides class names. */
const THREAD_TOKENS = /^(\*|Lambda-Expression|<implicit-thread-op:(parallelStream|parallel|Thread\.sleep|SubmissionPublisher\.(submit|offer))>)$/;

const MAX_PORT = 65535;

const ROW_PREFIX = 'artemisApp.programmingExercise.security.advanced.row.';

/**
 * Adds or edits one row of a category of the Allow Resource Access box (screens "Add Path", "Add Endpoint", "Add Command", "Add Thread Class" and "Add
 * Package"). It warns what student code may do afterwards, and the row becomes an unsaved change; nothing reaches the repository before the commit. Several
 * paths or commands can be entered at once, separated by commas, or picked with Browse…; each becomes its own row with the same permissions.
 */
@Component({
    selector: 'jhi-security-permission-dialog',
    templateUrl: './security-permission-dialog.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        FormsModule,
        TumAetUiDialogComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiInputDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
        SecurityBrowseDialogComponent,
    ],
})
export class SecurityPermissionDialogComponent {
    private readonly translateService = inject(TranslateService);

    protected readonly FILE_ACTIONS = FILE_ACTIONS;
    protected readonly NETWORK_ACTIONS = NETWORK_ACTIONS;

    readonly visible = model(false);
    readonly kind = input.required<ResourceKind>();
    /** The row being edited; undefined to add rows. */
    readonly editing = input<ResourceRow | undefined>(undefined);
    /** The keys of the rows the category already has, so a row cannot be added twice. */
    readonly existingKeys = input<ReadonlySet<string>>(new Set());
    /** The files of the template repository for Browse…; undefined until loaded. */
    readonly templateFiles = input<string[] | undefined>(undefined);
    readonly templateFilesTruncated = input(false);
    /** Whether the files of the template repository could not be loaded. */
    readonly templateFilesFailed = input(false);
    /** The tag of each path or script that already has an entry, for Browse…. */
    readonly browseBadges = input<Map<string, string>>(new Map());

    /** Emits the rows to add, or the one row replacing the edited row. */
    readonly saved = output<ResourceRow[]>();
    /** Asks the parent to load the files of the template repository. */
    readonly templateFilesRequested = output<void>();

    readonly path = signal('');
    readonly fileActions = signal<Record<FileAction, boolean>>({ read: false, overwrite: false, create: false, execute: false, delete: false });
    readonly host = signal('');
    /** The text of the port field as typed; {@link port} is its value. */
    readonly portText = signal('');
    readonly port = computed(() => parseWholeNumber(this.portText()));
    readonly networkActions = signal<Record<NetworkAction, boolean>>({ open: false, send: false, receive: false });
    readonly command = signal('');
    readonly argumentsText = signal('');
    readonly threadClass = signal('');
    /** The text of the thread count field as typed; {@link maxThreads} is its value. */
    readonly maxThreadsText = signal('');
    readonly maxThreads = computed(() => parseWholeNumber(this.maxThreadsText()));
    readonly packageName = signal('');
    readonly browseOpen = signal(false);
    /** Whether the instructor tried to save, so the problems of the fields show. */
    readonly submitted = signal(false);

    readonly isEditing = computed(() => this.editing() !== undefined);
    readonly browseMode = computed<SecurityBrowseMode>(() => (this.kind() === 'commands' ? 'scripts' : 'paths'));
    readonly canBrowse = computed(() => !this.isEditing() && (this.kind() === 'files' || this.kind() === 'commands'));
    readonly titleKey = computed(() => `${ROW_PREFIX}${this.kind()}.${this.isEditing() ? 'editTitle' : 'addTitle'}`);
    readonly confirmKey = computed(() => (this.isEditing() ? `${ROW_PREFIX}saveChanges` : `${ROW_PREFIX}${this.kind()}.addTitle`));
    /** The paths or commands the field names; several are separated by commas. */
    readonly browsedEntries = computed(() => splitList(this.kind() === 'commands' ? this.command() : this.path()) ?? []);

    /** The rows the dialog would add, from the fields and the browsed entries. */
    readonly rows = computed<ResourceRow[]>(() => {
        switch (this.kind()) {
            case 'files': {
                // An edited row keeps its exact path, even with surrounding spaces, so saving it does not grant another target.
                const paths = this.isEditing() ? [this.path()] : (splitList(this.path()) ?? []);
                const actions = this.fileActions();
                return paths.map(
                    (path) =>
                        ({
                            path,
                            read: actions.read,
                            overwrite: actions.overwrite,
                            create: actions.create,
                            execute: actions.execute,
                            delete: actions.delete,
                        }) satisfies SecurityFilePermission,
                );
            }
            case 'network': {
                const actions = this.networkActions();
                return [
                    { host: this.host().trim(), port: this.port() ?? -1, open: actions.open, send: actions.send, receive: actions.receive } satisfies SecurityNetworkPermission,
                ];
            }
            case 'commands': {
                const argumentList = splitArguments(this.argumentsText()) ?? [];
                const commands = this.isEditing() ? [this.command()] : (splitList(this.command()) ?? []);
                return commands.map((command) => ({ command, arguments: argumentList }) satisfies SecurityCommandPermission);
            }
            case 'threads':
                return [{ threadClass: this.threadClass().trim(), maxThreads: this.maxThreads() ?? 0 } satisfies SecurityThreadPermission];
            case 'packages':
                return [{ packageName: this.packageName().trim() } satisfies SecurityPackagePermission];
            default:
                return [];
        }
    });

    readonly grantsNothing = computed(() => {
        switch (this.kind()) {
            case 'files':
                return !Object.values(this.fileActions()).some(Boolean);
            case 'network':
                return !Object.values(this.networkActions()).some(Boolean);
            default:
                return false;
        }
    });

    /** The translation key of the first problem of the fields, or undefined if the rows can be saved. */
    readonly problem = computed<string | undefined>(() => {
        const prefix = `${ROW_PREFIX}problem.`;
        switch (this.kind()) {
            case 'files':
                if (!this.isEditing() && splitList(this.path()) === undefined) {
                    return prefix + 'listQuote';
                }
                if (!this.rows().length || this.rows().some((row) => !(row as SecurityFilePermission).path.trim())) {
                    return prefix + 'pathRequired';
                }
                break;
            case 'network':
                if (!this.host() || /\s/.test(this.host())) {
                    return prefix + 'hostRequired';
                }
                if (!isWholeNumberWithin(this.port(), 0, MAX_PORT)) {
                    return prefix + 'portRange';
                }
                break;
            case 'commands':
                if (!this.isEditing() && splitList(this.command()) === undefined) {
                    return prefix + 'listQuote';
                }
                if (!this.rows().length || this.rows().some((row) => !(row as SecurityCommandPermission).command.trim())) {
                    return prefix + 'commandRequired';
                }
                if (!splitArguments(this.argumentsText())) {
                    return prefix + 'argumentsQuote';
                }
                break;
            case 'threads':
                if (!isQualifiedName(this.threadClass().trim()) && !THREAD_TOKENS.test(this.threadClass().trim())) {
                    return prefix + 'className';
                }
                if (!isWholeNumberWithin(this.maxThreads(), 1, Number.MAX_SAFE_INTEGER)) {
                    return prefix + 'threadCount';
                }
                break;
            case 'packages':
                if (!isQualifiedName(this.packageName().trim())) {
                    return prefix + 'packageName';
                }
                break;
            default:
                break;
        }
        const batchKeys = this.rows().map((row) => rowKey(this.kind(), row));
        if (new Set(batchKeys).size < batchKeys.length) {
            return prefix + 'duplicate';
        }
        const edited = this.editing();
        const editedKey = edited ? rowKey(this.kind(), edited) : undefined;
        if (this.rows().some((row) => rowKey(this.kind(), row) !== editedKey && this.existingKeys().has(rowKey(this.kind(), row)))) {
            return prefix + 'duplicate';
        }
        return undefined;
    });

    readonly canSave = computed(() => !this.grantsNothing() && this.problem() === undefined);

    /** The field the problem belongs to, so it is marked invalid. */
    readonly problemField = computed(() => {
        const problem = this.problem();
        if (!problem || !this.submitted()) {
            return undefined;
        }
        if (problem.endsWith('portRange')) {
            return 'port';
        }
        if (problem.endsWith('threadCount')) {
            return 'maxThreads';
        }
        if (problem.endsWith('argumentsQuote')) {
            return 'arguments';
        }
        return 'first';
    });

    /** The rows the dialog names, e.g. {@code data/input.txt} or {@code localhost:8080}. */
    readonly target = computed(() =>
        this.rows()
            .map((row) => rowKey(this.kind(), row))
            .filter((key) => key.length > 0)
            .join(', '),
    );

    /** What student code may do with the first row, e.g. "read or create data/input.txt", summarised under the fields. */
    readonly summaryText = computed(() => {
        const row = this.rows()[0];
        const target = row ? rowKey(this.kind(), row) || '…' : '…';
        const kind = this.kind();
        if (kind === 'files' || kind === 'network') {
            const actions = kind === 'files' ? this.fileActions() : this.networkActions();
            const verbs = Object.entries(actions)
                .filter(([, granted]) => granted)
                .map(([action]) => this.translateService.instant(`${ROW_PREFIX}${kind}.verb.${action}`) as string);
            const phrase = verbs.length ? joinWithOr(verbs, this.translateService.instant(`${ROW_PREFIX}or`)) : this.translateService.instant(`${ROW_PREFIX}${kind}.verb.any`);
            return this.translateService.instant(`${ROW_PREFIX}warning`, { action: `${phrase} ${target}` }) as string;
        }
        const count = kind === 'threads' ? (this.maxThreads() ?? '…') : '';
        const action = this.translateService.instant(`${ROW_PREFIX}${kind}.verb`, { target, count }) as string;
        return this.translateService.instant(`${ROW_PREFIX}warning`, { action }) as string;
    });

    constructor() {
        // Each opening starts from the edited row, or from empty fields.
        effect(() => {
            if (!this.visible()) {
                return;
            }
            const row = this.editing();
            untracked(() => this.reset(row));
        });
    }

    private reset(row: ResourceRow | undefined): void {
        this.submitted.set(false);
        const file = row as SecurityFilePermission | undefined;
        const endpoint = row as SecurityNetworkPermission | undefined;
        const command = row as SecurityCommandPermission | undefined;
        const thread = row as SecurityThreadPermission | undefined;
        const permission = row as SecurityPackagePermission | undefined;
        this.path.set(file?.path ?? '');
        this.fileActions.set({ read: !!file?.read, overwrite: !!file?.overwrite, create: !!file?.create, execute: !!file?.execute, delete: !!file?.delete });
        this.host.set(endpoint?.host ?? '');
        this.portText.set(endpoint?.port === undefined ? '' : String(endpoint.port));
        this.networkActions.set({ open: !!endpoint?.open, send: !!endpoint?.send, receive: !!endpoint?.receive });
        this.command.set(command?.command ?? '');
        this.argumentsText.set(joinArguments(command?.arguments ?? []));
        this.threadClass.set(thread?.threadClass ?? '');
        this.maxThreadsText.set(thread?.maxThreads === undefined ? '' : String(thread.maxThreads));
        this.packageName.set(permission?.packageName ?? '');
    }

    protected setFileAction(action: FileAction, granted: boolean): void {
        this.fileActions.update((actions) => this.withAction(actions, action, granted));
    }

    protected setNetworkAction(action: NetworkAction, granted: boolean): void {
        this.networkActions.update((actions) => this.withAction(actions, action, granted));
    }

    private withAction<A extends string>(actions: Record<A, boolean>, action: A, granted: boolean): Record<A, boolean> {
        return Object.fromEntries(Object.entries(actions).map(([key, value]) => [key, key === action ? granted : value])) as Record<A, boolean>;
    }

    protected openBrowse(): void {
        if (this.templateFiles() === undefined) {
            this.templateFilesRequested.emit();
        }
        this.browseOpen.set(true);
    }

    /** The picked paths or scripts replace the entries of the field; each becomes its own row, also one whose name contains a comma. */
    protected onBrowsed(paths: string[]): void {
        if (this.kind() === 'commands') {
            this.command.set(joinList(paths));
        } else {
            this.path.set(joinList(paths));
        }
    }

    protected save(): void {
        this.submitted.set(true);
        if (!this.canSave()) {
            return;
        }
        this.saved.emit(this.rows());
        this.visible.set(false);
    }
}

/**
 * Splits a field that lists several paths or commands, separated by commas. An entry in double quotes keeps its commas and spaces, and two double quotes
 * inside it stand for one, so every file name can be listed; {@link joinList} writes such a field.
 * @returns the entries without the spaces around them, or undefined if a double quote is not closed
 */
export function splitList(text: string): string[] | undefined {
    const entries: string[] = [];
    // An entry is the quoted text, kept as it is, between the unquoted text before and after it, whose surrounding spaces are dropped.
    let entry = '';
    let pendingSpace = '';
    let quoted = false;
    const finish = () => {
        if (entry.length > 0) {
            entries.push(entry);
        }
        entry = '';
        pendingSpace = '';
    };
    for (let index = 0; index < text.length; index++) {
        const character = text[index];
        if (quoted) {
            if (character === '"' && text[index + 1] === '"') {
                entry += '"';
                index++;
            } else if (character === '"') {
                quoted = false;
            } else {
                entry += character;
            }
        } else if (character === '"') {
            entry += entry.length > 0 ? pendingSpace : '';
            pendingSpace = '';
            quoted = true;
        } else if (character === ',') {
            finish();
        } else if (/\s/.test(character)) {
            pendingSpace += character;
        } else {
            entry += (entry.length > 0 ? pendingSpace : '') + character;
            pendingSpace = '';
        }
    }
    if (quoted) {
        return undefined;
    }
    finish();
    return entries;
}

/** Writes entries into a field that {@link splitList} reads back as the same entries, quoting those with a comma, a double quote or surrounding spaces. */
export function joinList(entries: string[]): string {
    return entries.map((entry) => (/[,"]|^\s|\s$/.test(entry) ? `"${entry.replaceAll('"', '""')}"` : entry)).join(', ');
}

/** @returns the number of a field that holds digits only; undefined for any other text, which the field's problem then names */
function parseWholeNumber(text: string): number | undefined {
    const trimmed = text.trim();
    return /^\d+$/.test(trimmed) ? Number(trimmed) : undefined;
}

/** Whether the value is a whole number between the bounds, both included. */
function isWholeNumberWithin(value: number | undefined, min: number, max: number): boolean {
    return value !== undefined && Number.isInteger(value) && value >= min && value <= max;
}

/** Joins words like "read, create or delete". */
function joinWithOr(words: string[], or: string): string {
    return words.length <= 1 ? words.join('') : `${words.slice(0, -1).join(', ')} ${or} ${words[words.length - 1]}`;
}
