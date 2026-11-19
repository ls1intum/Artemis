import { Component, computed, effect, input, model, output, signal, untracked } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronRight, faXmark } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiDialogComponent,
    TumAetUiProgressSpinnerComponent,
    TumAetUiSearchFieldComponent,
    TumAetUiTagComponent,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** What the dialog selects: paths for the file system, or scripts for the command system. */
export type SecurityBrowseMode = 'paths' | 'scripts';

/** A file or folder of the template repository; a folder path ends with a slash. */
interface BrowseNode {
    name: string;
    path: string;
    folder: boolean;
    children: BrowseNode[];
}

/** A node as it is rendered: flattened, with its depth. */
interface VisibleNode {
    node: BrowseNode;
    depth: number;
    expanded: boolean;
    /** Whether the row is checked: the path itself, or every script of a folder of scripts. */
    selected: boolean;
    /** Whether some but not all scripts of a folder are checked. */
    partlySelected: boolean;
}

/** File names and extensions that are run as programs. */
const SCRIPT_EXTENSIONS = ['.sh', '.bash', '.py', '.rb', '.pl', '.js', '.mjs', '.bat', '.cmd', '.ps1', '.jar'];
const SCRIPT_NAMES = ['gradlew', 'mvnw', 'Makefile'];

/** @returns whether a file is a script a test may run */
export function isScript(path: string): boolean {
    const name = path.substring(path.lastIndexOf('/') + 1);
    return SCRIPT_NAMES.includes(name) || SCRIPT_EXTENSIONS.some((extension) => name.endsWith(extension));
}

/**
 * Lets an instructor pick several paths or scripts of the template repository at once (screen "Browse the Template Repository"). Folders start collapsed and
 * expand on demand, so the dialog stays readable for large repositories; a search expands every match. Badges name the paths that already have an entry.
 */
@Component({
    selector: 'jhi-security-browse-dialog',
    templateUrl: './security-browse-dialog.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        TumAetUiDialogComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiSearchFieldComponent,
        TumAetUiTagComponent,
        TumAetUiProgressSpinnerComponent,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class SecurityBrowseDialogComponent {
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronRight = faChevronRight;
    protected readonly faXmark = faXmark;

    readonly visible = model(false);
    readonly mode = input<SecurityBrowseMode>('paths');
    /** The paths of the files of the template repository; undefined while they load. */
    readonly files = input<string[] | undefined>(undefined);
    /** Whether the repository has more files than were listed. */
    readonly truncated = input(false);
    /** Whether the files could not be loaded; Retry asks for them again. */
    readonly failed = input(false);
    /** The tag of each path that already has an entry, e.g. the actions it grants. */
    readonly badges = input<Map<string, string>>(new Map());
    /** The paths selected when the dialog opens, e.g. those already typed into the field. */
    readonly initialSelection = input<string[]>([]);

    readonly selected = output<string[]>();
    readonly retryRequested = output<void>();

    readonly search = signal('');
    readonly expanded = signal<ReadonlySet<string>>(new Set());
    readonly selection = signal<ReadonlySet<string>>(new Set());

    /** The tree of the files that can be picked: every file and folder for paths, only scripts and their folders for scripts. */
    readonly tree = computed<BrowseNode[]>(() => {
        const files = (this.files() ?? []).filter((path) => this.mode() === 'paths' || isScript(path));
        const root: BrowseNode = { name: '', path: '', folder: true, children: [] };
        for (const file of files) {
            let parent = root;
            const parts = file.split('/');
            parts.forEach((part, index) => {
                const folder = index < parts.length - 1;
                const path = parts.slice(0, index + 1).join('/') + (folder ? '/' : '');
                let child = parent.children.find((node) => node.path === path);
                if (!child) {
                    child = { name: part, path, folder, children: [] };
                    parent.children.push(child);
                }
                parent = child;
            });
        }
        sortNodes(root);
        return root.children;
    });

    /** The rows shown: the expanded part of the tree, or every match of the search with its folders. */
    readonly visibleNodes = computed<VisibleNode[]>(() => {
        const query = this.search().trim().toLowerCase();
        const expanded = this.expanded();
        const rows: VisibleNode[] = [];
        const visit = (nodes: BrowseNode[], depth: number) => {
            for (const node of nodes) {
                if (query && !matches(node, query)) {
                    continue;
                }
                const isExpanded = node.folder && (query.length > 0 || expanded.has(node.path));
                rows.push({ node, depth, expanded: isExpanded, selected: this.isSelected(node), partlySelected: this.isPartlySelected(node) });
                if (isExpanded) {
                    visit(node.children, depth + 1);
                }
            }
        };
        visit(this.tree(), 0);
        return rows;
    });

    readonly selectedPaths = computed(() => [...this.selection()].sort((first, second) => first.localeCompare(second)));
    /** The selected entries the tree has no row for, e.g. a command typed into the field, which only the summary can remove. */
    readonly unlistedSelection = computed(() => {
        const listed = new Set<string>();
        const collect = (nodes: BrowseNode[]) =>
            nodes.forEach((node) => {
                listed.add(node.path);
                collect(node.children);
            });
        collect(this.tree());
        return this.selectedPaths().filter((path) => !listed.has(path));
    });
    /** Whether confirming changes the field: also when everything that was in it is deselected, which clears it. */
    readonly canConfirm = computed(() => this.selectedPaths().length > 0 || this.initialSelection().length > 0);
    /** Whether a search shows its matches: every folder of a match is open then, so the folders cannot be opened or closed. */
    readonly searching = computed(() => this.search().trim().length > 0);

    constructor() {
        // Every opening starts with the paths of the field and with collapsed folders.
        effect(() => {
            if (this.visible()) {
                const initial = this.initialSelection();
                untracked(() => {
                    this.selection.set(new Set(initial));
                    this.expanded.set(new Set());
                    this.search.set('');
                });
            }
        });
    }

    protected toggleExpanded(node: BrowseNode): void {
        // The folders the tree shows after the search stay as they were before it.
        if (this.searching()) {
            return;
        }
        const expanded = new Set(this.expanded());
        if (expanded.has(node.path)) {
            expanded.delete(node.path);
        } else {
            expanded.add(node.path);
        }
        this.expanded.set(expanded);
    }

    /**
     * Selects a node. For paths, a folder is one path of its own. For scripts, a folder stands for all scripts in it, because a command runs a file.
     */
    protected toggleSelected(node: BrowseNode, checked: boolean): void {
        const selection = new Set(this.selection());
        const paths = this.mode() === 'scripts' && node.folder ? leaves(node) : [node.path];
        for (const path of paths) {
            if (checked) {
                selection.add(path);
            } else {
                selection.delete(path);
            }
        }
        this.selection.set(selection);
    }

    protected isSelected(node: BrowseNode): boolean {
        if (this.mode() === 'scripts' && node.folder) {
            const paths = leaves(node);
            return paths.length > 0 && paths.every((path) => this.selection().has(path));
        }
        return this.selection().has(node.path);
    }

    protected isPartlySelected(node: BrowseNode): boolean {
        if (this.mode() !== 'scripts' || !node.folder) {
            return false;
        }
        const paths = leaves(node);
        const selectedCount = paths.filter((path) => this.selection().has(path)).length;
        return selectedCount > 0 && selectedCount < paths.length;
    }

    protected deselect(path: string): void {
        const selection = new Set(this.selection());
        selection.delete(path);
        this.selection.set(selection);
    }

    protected confirm(): void {
        if (!this.canConfirm()) {
            return;
        }
        this.selected.emit(this.selectedPaths());
        this.visible.set(false);
    }
}

function sortNodes(node: BrowseNode): void {
    node.children.sort((first, second) => (first.folder === second.folder ? first.name.localeCompare(second.name) : first.folder ? -1 : 1));
    node.children.forEach(sortNodes);
}

function matches(node: BrowseNode, query: string): boolean {
    return node.path.toLowerCase().includes(query) || node.children.some((child) => matches(child, query));
}

function leaves(node: BrowseNode): string[] {
    return node.folder ? node.children.flatMap(leaves) : [node.path];
}
