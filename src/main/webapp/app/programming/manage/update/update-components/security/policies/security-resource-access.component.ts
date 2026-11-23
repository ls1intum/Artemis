import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faEllipsisVertical, faLock, faPen, faPlus } from '@fortawesome/free-solid-svg-icons';
import { faTrashCan } from '@fortawesome/free-regular-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import {
    TumAetUiButtonDirective,
    TumAetUiMenuComponent,
    TumAetUiMenuItemDirective,
    TumAetUiMenuTriggerDirective,
    TumAetUiTabComponent,
    TumAetUiTabListComponent,
    TumAetUiTabsComponent,
    TumAetUiTagComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import {
    SecurityCommandPermission,
    SecurityFilePermission,
    SecurityNetworkPermission,
    SecurityPackagePermission,
    SecurityPolicyDocument,
    SecurityPreset,
    SecurityThreadPermission,
} from 'app/programming/shared/entities/security-policy.model';
import {
    SecurityConfirmDetail,
    SecurityConfirmDialogComponent,
    SecurityTranslatable,
} from 'app/programming/manage/update/update-components/security/policies/security-confirm-dialog.component';
import {
    FILE_ACTIONS,
    NETWORK_ACTIONS,
    SecurityPermissionDialogComponent,
} from 'app/programming/manage/update/update-components/security/policies/security-permission-dialog.component';
import {
    RESOURCE_KINDS,
    ResourceKind,
    ResourceRow,
    RowSource,
    SourcedRow,
    countKey,
    countRowChanges,
    joinArguments,
    rowKey,
    rowsOf,
    sourcedRows,
    withRows,
} from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

const PREFIX = 'artemisApp.programmingExercise.security.advanced.resources';

/** A granted action of a row, as a tag with its explanation. */
interface GrantedTag {
    labelKey: string;
    tooltipKey: string;
    params?: Record<string, string | number>;
}

/**
 * The content of the Allow Resource Access box of a security policy (screens "Allow Resource Access"): one tab per category with its number of rows, each
 * row with its granted actions as tags and its source. Rows change only through the dialog, which opens from the Add button of a tab or from Edit in the
 * actions menu of a row; deleted rows stay visible until the commit and can be restored, and rows a preset adds are locked.
 */
@Component({
    selector: 'jhi-security-resource-access',
    templateUrl: './security-resource-access.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        TumAetUiTabsComponent,
        TumAetUiTabListComponent,
        TumAetUiTabComponent,
        TumAetUiTagComponent,
        TumAetUiButtonDirective,
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
        TumAetUiTooltipDirective,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        SecurityPermissionDialogComponent,
        SecurityConfirmDialogComponent,
    ],
})
export class SecurityResourceAccessComponent {
    private readonly translateService = inject(TranslateService);

    protected readonly faPlus = faPlus;
    protected readonly faLock = faLock;
    protected readonly faPen = faPen;
    protected readonly faTrashCan = faTrashCan;
    protected readonly faEllipsisVertical = faEllipsisVertical;
    protected readonly RESOURCE_KINDS = RESOURCE_KINDS;
    protected readonly RowSource = RowSource;

    /** The document with the unsaved changes. */
    readonly document = input.required<SecurityPolicyDocument>();
    /** The committed document; undefined for a new policy. */
    readonly base = input<SecurityPolicyDocument | undefined>(undefined);
    /** The preset the policy is based on, whose rows are shown locked. */
    readonly preset = input<SecurityPreset | undefined>(undefined);
    readonly disabled = input(false);
    readonly templateFiles = input<string[] | undefined>(undefined);
    readonly templateFilesTruncated = input(false);
    readonly templateFilesFailed = input(false);

    readonly documentChange = output<SecurityPolicyDocument>();
    readonly templateFilesRequested = output<void>();

    readonly activeTab = signal<ResourceKind>('files');
    readonly dialogOpen = signal(false);
    readonly editingRow = signal<ResourceRow | undefined>(undefined);
    readonly confirmOpen = signal(false);
    readonly deleting = signal<SourcedRow | undefined>(undefined);

    /** The rows of every category, with their source. */
    readonly rowsByKind = computed(
        () =>
            Object.fromEntries(RESOURCE_KINDS.map((kind) => [kind, sourcedRows<ResourceKind>(kind, this.base(), this.document(), this.preset())])) as Record<
                ResourceKind,
                SourcedRow[]
            >,
    );

    /** The number of rows each tab shows, without the deleted ones, and whether the category has unsaved changes. */
    readonly tabCounts = computed(
        () =>
            Object.fromEntries(
                RESOURCE_KINDS.map((kind) => [
                    kind,
                    { rows: this.rowsByKind()[kind].filter((row) => row.source !== RowSource.REMOVED).length, changed: countRowChanges(kind, this.base(), this.document()) > 0 },
                ]),
            ) as Record<ResourceKind, { rows: number; changed: boolean }>,
    );

    readonly activeRows = computed(() => this.rowsByKind()[this.activeTab()]);

    /** The rows of the active category as the table shows them: the text of each column and the granted actions. */
    readonly activeRowViews = computed(() => {
        const kind = this.activeTab();
        return this.activeRows().map((row) => cloneWith(row, { cells: this.cellsOf(kind, row.row), tags: this.grantedTags(kind, row.row) }));
    });

    /** @returns the text of each column of a row before the granted actions; empty for a command without arguments */
    private cellsOf(kind: ResourceKind, row: ResourceRow): string[] {
        switch (kind) {
            case 'files':
                return [this.asFile(row).path];
            case 'network':
                return [this.asEndpoint(row).host, String(this.asEndpoint(row).port)];
            case 'commands':
                return [this.asCommand(row).command, joinArguments(this.asCommand(row).arguments ?? [])];
            case 'threads':
                return [String(this.asThread(row).maxThreads), this.asThread(row).threadClass];
            case 'packages':
                return [this.asPackage(row).packageName];
            default:
                return [];
        }
    }

    readonly existingKeys = computed(
        () =>
            new Set(
                this.activeRows()
                    .filter((row) => row.source !== RowSource.REMOVED)
                    .map((row) => row.key),
            ),
    );

    /** The tag of each path or script that already has an entry, for Browse…: the actions it grants. */
    readonly browseBadges = computed(() => {
        const badges = new Map<string, string>();
        for (const row of this.activeRows()) {
            if (row.source === RowSource.REMOVED) {
                continue;
            }
            if (this.activeTab() === 'files') {
                const labels = this.grantedTags('files', row.row).map((tag) => this.translateService.instant(tag.labelKey) as string);
                badges.set((row.row as SecurityFilePermission).path, labels.join(', '));
            } else if (this.activeTab() === 'commands') {
                const command = row.row as SecurityCommandPermission;
                const label = this.translateService.instant(`${PREFIX}.granted.execute`) as string;
                [command.command, ...(command.arguments ?? [])].forEach((part) => badges.set(part, label));
            }
        }
        return badges;
    });

    // The rows of a category as their own types, so the template can read their fields.
    private asFile(row: ResourceRow): SecurityFilePermission {
        return row as SecurityFilePermission;
    }

    private asEndpoint(row: ResourceRow): SecurityNetworkPermission {
        return row as SecurityNetworkPermission;
    }

    private asCommand(row: ResourceRow): SecurityCommandPermission {
        return row as SecurityCommandPermission;
    }

    private asThread(row: ResourceRow): SecurityThreadPermission {
        return row as SecurityThreadPermission;
    }

    private asPackage(row: ResourceRow): SecurityPackagePermission {
        return row as SecurityPackagePermission;
    }

    /** @returns the tags of the Granted column: the actions of a file or network row, and the one thing the other rows allow */
    private grantedTags(kind: ResourceKind, row: ResourceRow): GrantedTag[] {
        switch (kind) {
            case 'files': {
                const file = row as SecurityFilePermission;
                return FILE_ACTIONS.filter((action) => file[action]).map((action) => ({
                    labelKey: `${PREFIX}.granted.${action}`,
                    tooltipKey: `${PREFIX}.grantedHelp.files.${action}`,
                }));
            }
            case 'network': {
                const endpoint = row as SecurityNetworkPermission;
                return NETWORK_ACTIONS.filter((action) => endpoint[action]).map((action) => ({
                    labelKey: `${PREFIX}.granted.${action}`,
                    tooltipKey: `${PREFIX}.grantedHelp.network.${action}`,
                }));
            }
            case 'commands':
                return [{ labelKey: `${PREFIX}.granted.execute`, tooltipKey: `${PREFIX}.grantedHelp.commands` }];
            case 'threads':
                return [
                    {
                        labelKey: `${PREFIX}.granted.create`,
                        tooltipKey: countKey(`${PREFIX}.grantedHelp.threads`, (row as SecurityThreadPermission).maxThreads),
                        params: { count: (row as SecurityThreadPermission).maxThreads },
                    },
                ];
            case 'packages':
                return [{ labelKey: `${PREFIX}.granted.import`, tooltipKey: `${PREFIX}.grantedHelp.packages` }];
            default:
                return [];
        }
    }

    protected onTabChange(value: string | number | undefined): void {
        if (RESOURCE_KINDS.includes(value as ResourceKind)) {
            this.activeTab.set(value as ResourceKind);
        }
    }

    protected openAdd(): void {
        this.editingRow.set(undefined);
        this.dialogOpen.set(true);
    }

    protected openEdit(row: SourcedRow): void {
        this.editingRow.set(row.row);
        this.dialogOpen.set(true);
    }

    protected openDelete(row: SourcedRow): void {
        this.deleting.set(row);
        this.confirmOpen.set(true);
    }

    /** Adds the rows of the dialog, or replaces the edited row with the one it returns. */
    protected onSaved(rows: ResourceRow[]): void {
        const kind = this.activeTab();
        const current = rowsOf(this.document(), kind) as ResourceRow[];
        const edited = this.editingRow();
        let updated: ResourceRow[];
        if (edited) {
            const editedKey = rowKey(kind, edited);
            updated = current.map((row) => (rowKey(kind, row) === editedKey ? rows[0] : row));
        } else {
            updated = [...current, ...rows];
        }
        this.documentChange.emit(withRows(this.document(), kind, updated));
    }

    protected onDeleteConfirmed(): void {
        const row = this.deleting();
        if (!row) {
            return;
        }
        const kind = this.activeTab();
        const remaining = (rowsOf(this.document(), kind) as ResourceRow[]).filter((entry) => rowKey(kind, entry) !== row.key);
        this.documentChange.emit(withRows(this.document(), kind, remaining));
        this.deleting.set(undefined);
    }

    /** Restores a deleted committed row at its committed position. */
    protected undoDelete(row: SourcedRow): void {
        const kind = this.activeTab();
        const order = (rowsOf(this.base(), kind) as ResourceRow[]).map((entry) => rowKey(kind, entry));
        const restored = [...(rowsOf(this.document(), kind) as ResourceRow[]), row.row];
        const position = (entry: ResourceRow) => {
            const index = order.indexOf(rowKey(kind, entry));
            return index < 0 ? order.length : index;
        };
        restored.sort((first, second) => position(first) - position(second));
        this.documentChange.emit(withRows(this.document(), kind, restored));
    }

    readonly deleteHeader = computed<SecurityTranslatable>(() => ({ key: `${PREFIX}.delete.title`, params: { noun: this.noun('noun') } }));
    // Lower-casing the title noun would also lower-case German nouns, so each language names the noun for a sentence itself.
    readonly deleteQuestion = computed<SecurityTranslatable>(() => ({
        key: `${PREFIX}.delete.question`,
        params: { noun: this.noun('nounInSentence'), tab: this.translateService.instant(`${PREFIX}.tab.${this.activeTab()}`) },
    }));
    readonly deleteNote: SecurityTranslatable = { key: `${PREFIX}.delete.note` };

    private noun(form: 'noun' | 'nounInSentence'): string {
        return this.translateService.instant(`artemisApp.programmingExercise.security.advanced.row.${this.activeTab()}.${form}`);
    }

    /** What the deletion dialog names: the fields of the row and its granted actions. */
    readonly deleteDetails = computed<SecurityConfirmDetail[]>(() => {
        const row = this.deleting();
        if (!row) {
            return [];
        }
        const kind = this.activeTab();
        const column = `${PREFIX}.column`;
        const granted = this.grantedTags(kind, row.row)
            .map((tag) => this.translateService.instant(tag.labelKey) as string)
            .join(', ');
        switch (kind) {
            case 'files':
                return [
                    { labelKey: `${column}.path`, value: this.asFile(row.row).path },
                    { labelKey: `${column}.granted`, value: granted },
                ];
            case 'network':
                return [
                    { labelKey: `${column}.host`, value: this.asEndpoint(row.row).host },
                    { labelKey: `${column}.port`, value: String(this.asEndpoint(row.row).port) },
                    { labelKey: `${column}.granted`, value: granted },
                ];
            case 'commands':
                return [
                    { labelKey: `${column}.command`, value: this.asCommand(row.row).command },
                    {
                        labelKey: `${column}.arguments`,
                        value: joinArguments(this.asCommand(row.row).arguments ?? []) || this.translateService.instant(`${PREFIX}.noArguments`),
                    },
                ];
            case 'threads':
                return [
                    { labelKey: `${column}.maxThreads`, value: String(this.asThread(row.row).maxThreads) },
                    { labelKey: `${column}.threadClass`, value: this.asThread(row.row).threadClass },
                ];
            case 'packages':
                return [{ labelKey: `${column}.package`, value: this.asPackage(row.row).packageName }];
            default:
                return [];
        }
    });

    /** What student code can no longer do once the row is deleted. */
    readonly deleteWarning = computed<SecurityTranslatable | undefined>(() => {
        const row = this.deleting();
        if (!row) {
            return undefined;
        }
        return { key: `${PREFIX}.delete.warning.${this.activeTab()}`, params: { target: row.key } };
    });
}
