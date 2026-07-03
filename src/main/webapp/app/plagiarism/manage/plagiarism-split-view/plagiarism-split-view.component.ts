import { Component, ElementRef, OnChanges, OnDestroy, OnInit, SimpleChanges, afterNextRender, inject, input, signal } from '@angular/core';
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
import { SplitterModule } from 'primeng/splitter';
import { TooltipModule } from 'primeng/tooltip';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PlagiarismCaseExercise } from 'app/plagiarism/shared/entities/PlagiarismCase';

@Component({
    selector: 'jhi-plagiarism-split-view',
    styleUrls: ['./plagiarism-split-view.component.scss'],
    templateUrl: './plagiarism-split-view.component.html',
    imports: [SplitterModule, TextSubmissionViewerComponent, FaIconComponent, TooltipModule, ArtemisTranslatePipe],
})
export class PlagiarismSplitViewComponent implements OnChanges, OnInit, OnDestroy {
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private readonly hostElement = inject<ElementRef<HTMLElement>>(ElementRef);

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
     * Sizes (in percent) of the two splitter panels. Bound to p-splitter via [panelSizes];
     * updating this signal re-applies the panel sizes at runtime through the panelSizes setter.
     */
    readonly panelSizes = signal<number[]>([50, 50]);

    /**
     * Which pane the "show only left/right" controls have fully collapsed, or undefined when both are shown.
     * Collapsing is applied via a CSS class (see the component SCSS), NOT via `panelSizes`: p-splitter coerces a
     * falsy size to an even split (`panelInitialSize || 100 / panels.length`), so setting a pane to `0` would
     * render it at 50% and never actually hide it.
     */
    readonly collapsedSide = signal<'left' | 'right' | undefined>(undefined);

    /**
     * Live x-position (px, from the splitter's left edge) of the gutter centre. The lock-files button is positioned
     * here so it stays on the divider while dragging, instead of staying fixed at the centre (a reviewer finding).
     * Updated by a ResizeObserver on the first panel because p-splitter emits no event during a drag.
     */
    readonly gutterCenterPx = signal<number | undefined>(undefined);
    /** Splitter gutter width (px); single source of truth, bound to the template's [gutterSize] and used to centre the lock button. */
    protected readonly gutterSize = 12;
    private panelResizeObserver?: ResizeObserver;

    readonly isProgrammingOrTextExercise = signal(false);

    readonly matchesA = signal<Map<string, FromToElement[]> | undefined>(undefined);
    readonly matchesB = signal<Map<string, FromToElement[]> | undefined>(undefined);
    readonly isLockFilesEnabled = signal(false);

    readonly dayjs = dayjs;
    protected readonly faLock: IconDefinition = faLock;
    protected readonly faUnlock: IconDefinition = faUnlock;

    constructor() {
        // Track the gutter position once the splitter (and its panels) have rendered.
        afterNextRender(() => this.observeGutterPosition());
    }

    /**
     * Observes the first splitter panel so the lock-files button can follow the gutter live while dragging.
     * The gutter centre sits at the first panel's width plus half the gutter size, measured from the splitter's left.
     */
    private observeGutterPosition(): void {
        const firstPanel = this.hostElement.nativeElement.querySelector<HTMLElement>('.p-splitterpanel');
        if (!firstPanel || typeof ResizeObserver === 'undefined') {
            return;
        }
        const update = () => {
            // Skip a meaningless measurement (panel not laid out yet, e.g. inside an inactive ngbNav tab): writing
            // gutterSize/2 here would jam the lock button near the far-left edge. Leaving gutterCenterPx undefined
            // keeps the SCSS fallback (left: 50%; translateX(-50%)) centring it until a real width arrives.
            const width = firstPanel.offsetWidth;
            if (width > 0) {
                this.gutterCenterPx.set(width + this.gutterSize / 2);
            }
        };
        this.panelResizeObserver = new ResizeObserver(update);
        this.panelResizeObserver.observe(firstPanel);
        update();
    }

    ngOnInit(): void {
        this.splitControlSubject()?.subscribe((pane: string) => this.handleSplitControl(pane));
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.exercise) {
            const exercise = changes.exercise.currentValue as Exercise | PlagiarismCaseExercise | undefined;

            this.isProgrammingOrTextExercise.set(exercise?.type === ExerciseType.PROGRAMMING || exercise?.type === ExerciseType.TEXT);
        }

        if (changes.comparison || changes.exercise) {
            this.fetchComparisonIfReady(changes.comparison?.currentValue, changes.exercise?.currentValue);
        }
    }

    private fetchComparisonIfReady(changedComparison?: PlagiarismComparison | PlagiarismComparisonSummary, changedExercise?: Exercise | PlagiarismCaseExercise): void {
        const comparison = changedComparison ?? this.comparison();
        const courseId = this.getCourseIdForExercise(changedExercise);
        if (courseId === undefined || comparison?.id === undefined) {
            return;
        }

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
    }

    private getCourseIdForExercise(changedExercise?: Exercise | PlagiarismCaseExercise): number | undefined {
        const exercise = changedExercise ?? this.exercise();
        if (!exercise) {
            return undefined;
        }
        if ('courseId' in exercise && exercise.courseId !== undefined) {
            return exercise.courseId;
        }
        return getCourseId(exercise as Exercise);
    }

    ngOnDestroy() {
        this.fileSelectedSubject.complete();
        this.showFilesSubject.complete();
        this.dropdownHoverSubject.complete();
        this.panelResizeObserver?.disconnect();
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
                return;
            }
            case 'right': {
                // show only the right pane: fully collapse the left one
                this.collapsedSide.set('left');
                return;
            }
            case 'even': {
                this.collapsedSide.set(undefined);
                this.panelSizes.set([50, 50]);
            }
        }
    }

    /**
     * Toggles the state of file locking and emits the new state to the parent component.
     */
    toggleLockFiles() {
        this.isLockFilesEnabled.update((enabled) => !enabled);
    }
}
