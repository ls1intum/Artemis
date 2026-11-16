import { Component, OnDestroy, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { PlagiarismComparison, PlagiarismComparisonSummary } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { FromToElement } from 'app/plagiarism/shared/entities/PlagiarismSubmissionElement';
import { Subject } from 'rxjs';
import { Exercise, ExerciseType, getCourseId } from 'app/exercise/shared/entities/exercise/exercise.model';
import { PlagiarismSubmission } from 'app/plagiarism/shared/entities/PlagiarismSubmission';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { HttpResponse } from '@angular/common/http';
import { SimpleMatch } from 'app/plagiarism/shared/entities/PlagiarismMatch';
import dayjs from 'dayjs/esm';
import { PlagiarismFileElement } from 'app/plagiarism/shared/entities/PlagiarismFileElement';
import { IconDefinition, faLock, faUnlock } from '@fortawesome/free-solid-svg-icons';
import { TextSubmissionViewerComponent } from './text-submission-viewer/text-submission-viewer.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiButtonDirective, TumAetUiSplitterComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PlagiarismCaseExercise } from 'app/plagiarism/shared/entities/PlagiarismCase';

/** Smallest size (in percent) a pane keeps while both panes are shown. */
const MIN_PANE_SIZE = 10;

@Component({
    selector: 'jhi-plagiarism-split-view',
    styleUrls: ['./plagiarism-split-view.component.scss'],
    templateUrl: './plagiarism-split-view.component.html',
    imports: [TumAetUiSplitterComponent, TextSubmissionViewerComponent, FaIconComponent, TumAetUiButtonDirective, TumAetUiTooltipDirective, ArtemisTranslatePipe],
})
export class PlagiarismSplitViewComponent implements OnInit, OnDestroy {
    private plagiarismCasesService = inject(PlagiarismCasesService);

    readonly comparison = input<PlagiarismComparison | PlagiarismComparisonSummary | undefined>(undefined);
    readonly exercise = input<Exercise | PlagiarismCaseExercise>();
    readonly splitControlSubject = input<Subject<string>>();
    readonly sortByStudentLogin = input<string>();
    readonly forStudent = input<boolean>();

    readonly plagiarismComparison = signal<PlagiarismComparison | undefined>(undefined);
    fileSelectedSubject = new Subject<PlagiarismFileElement>();
    showFilesSubject = new Subject<boolean>();
    dropdownHoverSubject = new Subject<PlagiarismFileElement>();

    /**
     * Initial sizes (in percent) of the two splitter panels. Bound to the splitter via [panelSizes]; the splitter applies a
     * new value whenever this signal is set, also when it holds the same numbers as before, which discards the size the
     * user dragged to.
     */
    readonly panelSizes = signal<number[]>([50, 50]);

    /**
     * Which pane the "show only left/right" controls have fully collapsed, or undefined when both are shown.
     * A collapsed pane has size 0 and the splitter minimum of both panes is lifted to 0, since the splitter does not let a
     * pane shrink below its minimum.
     */
    readonly collapsedSide = signal<'left' | 'right' | undefined>(undefined);

    /**
     * Minimum size (in percent) of each pane, which is 0 while a pane is collapsed.
     */
    readonly minSizes = computed(() => (this.collapsedSide() ? [0, 0] : [MIN_PANE_SIZE, MIN_PANE_SIZE]));

    /**
     * Size (in percent) of the first pane as the user last set it. The lock-files button is positioned from it, so that it
     * stays on the divider while dragging.
     */
    readonly splitPercent = signal(50);

    /** Splitter gutter width (px); single source of truth, bound to the template's [gutterSize] and used to centre the lock button. */
    protected readonly gutterSize = 6;

    /**
     * Horizontal position of the gutter centre: the first pane takes its share of the width without the gutter, and the
     * gutter then adds half its own width.
     */
    readonly lockButtonLeft = computed(() => `calc((100% - ${this.gutterSize}px) * ${this.splitPercent() / 100} + ${this.gutterSize / 2}px)`);

    // Replaces the ngOnChanges 'exercise' branch: pure derived state, recomputed whenever the exercise input changes.
    readonly isProgrammingOrTextExercise = computed(() => {
        const exercise = this.exercise();
        return exercise?.type === ExerciseType.PROGRAMMING || exercise?.type === ExerciseType.TEXT;
    });

    /** Course of the exercise under review, undefined until the exercise input is bound. Derived so the comparison fetch can track it. */
    readonly courseId = computed(() => {
        const exercise = this.exercise();
        if (!exercise) {
            return undefined;
        }
        if ('courseId' in exercise && exercise.courseId !== undefined) {
            return exercise.courseId;
        }
        return getCourseId(exercise as Exercise);
    });

    readonly matchesA = signal<Map<string, FromToElement[]> | undefined>(undefined);
    readonly matchesB = signal<Map<string, FromToElement[]> | undefined>(undefined);
    readonly isLockFilesEnabled = signal(false);

    readonly dayjs = dayjs;
    protected readonly faLock: IconDefinition = faLock;
    protected readonly faUnlock: IconDefinition = faUnlock;

    constructor() {
        // Both inputs are tracked, because the parent may bind them in either order. Reading the course id inside untracked() would mean that an exercise arriving
        // after the comparison never retriggers the fetch, which is what the replaced ngOnChanges did handle.
        effect(() => {
            const comparison = this.comparison();
            const courseId = this.courseId();
            if (!comparison || courseId === undefined) {
                return;
            }
            untracked(() => {
                this.plagiarismCasesService.getPlagiarismComparisonForSplitView(courseId, comparison.id).subscribe((resp: HttpResponse<PlagiarismComparison>) => {
                    const plagiarismComparison = resp.body!;
                    const sortByStudentLogin = this.sortByStudentLogin();
                    if (sortByStudentLogin && sortByStudentLogin === plagiarismComparison.submissionB.studentLogin) {
                        this.swapSubmissions(plagiarismComparison);
                    }
                    this.plagiarismComparison.set(plagiarismComparison);
                    if (this.isProgrammingOrTextExercise()) {
                        this.parseTextMatches(plagiarismComparison);
                    }
                });
            });
        });
    }

    ngOnInit(): void {
        this.splitControlSubject()?.subscribe((pane: string) => this.handleSplitControl(pane));
    }

    ngOnDestroy() {
        this.fileSelectedSubject.complete();
        this.showFilesSubject.complete();
        this.dropdownHoverSubject.complete();
    }

    /**
     * Swaps fields of A with fields of B in-place.
     * More specifically, swaps submissionA with submissionB and startA with startB in matches.
     * @param plagiarismComparison plagiarism comparison that will be modified in-place
     */
    private swapSubmissions(plagiarismComparison: PlagiarismComparison) {
        const temp = plagiarismComparison.submissionA;
        plagiarismComparison.submissionA = plagiarismComparison.submissionB;
        plagiarismComparison.submissionB = temp;

        if (plagiarismComparison?.matches) {
            plagiarismComparison.matches.forEach((match) => {
                const tempStart = match.startA;
                match.startA = match.startB;
                match.startB = tempStart;
            });
        }
    }

    parseTextMatches(plagComparison: PlagiarismComparison) {
        if (plagComparison.matches) {
            const matchesA = plagComparison.matches.map((match) => new SimpleMatch(match.startA, match.length)).sort((m1, m2) => m1.start - m2.start);
            const matchesB = plagComparison.matches.map((match) => new SimpleMatch(match.startB, match.length)).sort((m1, m2) => m1.start - m2.start);

            this.matchesA.set(this.mapMatchesToElements(matchesA, plagComparison.submissionA));
            this.matchesB.set(this.mapMatchesToElements(matchesB, plagComparison.submissionB));
        } else {
            // empty map in case no matches are available
            this.matchesA.set(new Map<string, FromToElement[]>());
            this.matchesB.set(new Map<string, FromToElement[]>());
        }
    }

    /**
     * Create a map of file names to matches elements.
     * @param matches list of objects containing the index and length of matched elements
     * @param submission the submission to map the elements of
     */
    mapMatchesToElements(matches: SimpleMatch[], submission: PlagiarismSubmission) {
        // sort submission elements so that from and to indexes from matches reference correct elements
        const elements = submission.elements?.sort((a, b) => a.id - b.id);
        const filesToMatchedElements = new Map<string, FromToElement[]>();

        matches.forEach((match) => {
            // skip empty jplag (whitespace) matches
            if (match.length === 0) {
                return;
            }
            const file = elements![match.start]?.file || 'none';

            if (!filesToMatchedElements.has(file)) {
                filesToMatchedElements.set(file, []);
            }

            const fileMatches = filesToMatchedElements.get(file)!;

            fileMatches.push(new FromToElement(elements![match.start], elements![match.start + match.length - 1]));
        });

        return filesToMatchedElements;
    }

    getTextSubmissionA() {
        return this.plagiarismComparison()!.submissionA;
    }

    getTextSubmissionB() {
        return this.plagiarismComparison()!.submissionB;
    }

    handleSplitControl(pane: string) {
        switch (pane) {
            case 'left': {
                // show only the left pane: fully collapse the right one
                this.collapsedSide.set('right');
                this.panelSizes.set([100, 0]);
                return;
            }
            case 'right': {
                // show only the right pane: fully collapse the left one
                this.collapsedSide.set('left');
                this.panelSizes.set([0, 100]);
                return;
            }
            case 'even': {
                this.collapsedSide.set(undefined);
                this.panelSizes.set([50, 50]);
                this.splitPercent.set(50);
            }
        }
    }

    /**
     * Follows the divider while the user drags it or moves it with the keyboard.
     * @param sizes the sizes (in percent) of the two panes
     */
    onSizesChange(sizes: number[]) {
        this.splitPercent.set(sizes[0]);
    }

    /**
     * Leaves the collapsed state when the user moves the divider that remains at the edge of the collapsed pane.
     * @param sizes the sizes (in percent) of the two panes
     */
    onResizeEnd(sizes: number[]) {
        if (!this.collapsedSide()) {
            return;
        }
        const first = Math.min(100 - MIN_PANE_SIZE, Math.max(MIN_PANE_SIZE, sizes[0]));
        this.collapsedSide.set(undefined);
        this.panelSizes.set([first, 100 - first]);
        this.splitPercent.set(first);
    }

    /**
     * Toggles the state of file locking and emits the new state to the parent component.
     */
    toggleLockFiles() {
        this.isLockFilesEnabled.update((enabled) => !enabled);
    }
}
