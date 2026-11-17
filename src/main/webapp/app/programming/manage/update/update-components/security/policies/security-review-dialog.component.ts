import { Component, computed, effect, input, model, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faChevronRight, faCircleInfo, faCircleXmark, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonDirective,
    TumAetUiDialogComponent,
    TumAetUiInputDirective,
    TumAetUiMessageComponent,
    TumAetUiProgressSpinnerComponent,
    TumAetUiTagComponent,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SecurityVersionCompatibilityVerdict } from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityPolicyPreview } from 'app/programming/shared/entities/security-policy.model';
import { DiffRow, diffLines, diffStats } from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

/** The diff of one changed file. */
interface FileDiff {
    path: string;
    /** What the file is, e.g. "Stress Tests · new file" or "Build settings". */
    description: string;
    rows: DiffRow[];
    added: number;
    removed: number;
}

/** One test case assignment the commit changes. */
export interface AssignmentChange {
    sign: '+' | '−';
    entry: string;
    file: string;
}

/**
 * Shows every unsaved change before it is committed (screen "Review and Commit Changes"): a side-by-side diff of each changed file, the test case
 * assignments that change, and the problems that keep the commit from being made. It checks the files against the pinned Ares version and commits them in
 * one commit with the instructor's message. A version that does not accept a file blocks the commit, because Ares would then fail every test.
 */
@Component({
    selector: 'jhi-security-review-dialog',
    templateUrl: './security-review-dialog.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        FormsModule,
        TumAetUiDialogComponent,
        TumAetUiButtonDirective,
        TumAetUiInputDirective,
        TumAetUiMessageComponent,
        TumAetUiTagComponent,
        TumAetUiProgressSpinnerComponent,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class SecurityReviewDialogComponent {
    protected readonly faCheck = faCheck;
    protected readonly faChevronRight = faChevronRight;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faCircleXmark = faCircleXmark;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly SecurityVersionCompatibilityVerdict = SecurityVersionCompatibilityVerdict;
    /** The tab index of the horizontally scrolling diffs: it takes keyboard focus, so keyboard users can scroll it too. */
    protected readonly scrollableRegionTabIndex = 0;

    readonly visible = model(false);
    /** The rendered files; undefined while the preview loads. */
    readonly preview = input<SecurityPolicyPreview | undefined>(undefined);
    /** What each changed file is, by its path. */
    readonly descriptions = input<Map<string, string>>(new Map());
    /** The test case assignments the commit changes. */
    readonly assignments = input<AssignmentChange[]>([]);
    /** The problems that keep the changes from being committed, translated. */
    readonly problems = input<string[]>([]);
    /** The summary of the changes, e.g. "2 allowed resource accesses changed · based on commit abc1234". */
    readonly summary = input('');
    /** The short hash of the commit the changes are based on. */
    readonly baseCommit = input<string | undefined>(undefined);
    readonly defaultMessage = input('');
    /** Whether no commit can start now, because one is running or the policies are loading. */
    readonly busy = input(false);

    readonly commitRequested = output<string>();

    readonly message = signal('');

    readonly diffs = computed<FileDiff[]>(() =>
        (this.preview()?.files ?? [])
            .filter((file) => file.before !== file.after || (file.previousPath !== undefined && file.previousPath !== file.path))
            .map((file) => {
                const rows = diffLines(file.before ?? '', file.after ?? '');
                const stats = diffStats(rows);
                return { path: file.path, description: this.descriptions().get(file.path) ?? '', rows, added: stats.added, removed: stats.removed };
            }),
    );

    readonly assignmentsAdded = computed(() => this.assignments().filter((assignment) => assignment.sign === '+').length);
    readonly assignmentsRemoved = computed(() => this.assignments().filter((assignment) => assignment.sign === '−').length);

    readonly verdict = computed(() => this.preview()?.verdict);
    readonly blocked = computed(() => this.verdict() === SecurityVersionCompatibilityVerdict.INCOMPATIBLE || this.problems().length > 0);
    readonly canCommit = computed(
        () => !!this.preview() && !this.blocked() && !this.busy() && (this.diffs().length > 0 || this.assignments().length > 0) && this.message().trim().length > 0,
    );

    constructor() {
        effect(() => {
            if (this.visible()) {
                const message = this.defaultMessage();
                untracked(() => this.message.set(message));
            }
        });
    }

    protected commit(): void {
        if (this.canCommit()) {
            this.commitRequested.emit(this.message().trim());
        }
    }
}
