import { AfterViewInit, Component, Directive, ElementRef, OnDestroy, OnInit, computed, effect, inject, input, signal, untracked, viewChildren } from '@angular/core';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { FromToElement } from 'app/plagiarism/shared/entities/PlagiarismSubmissionElement';
import Split from 'split.js';
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

@Directive({ selector: '[jhiPane]' })
export class SplitPaneDirective {
    elementRef = inject(ElementRef);
}

@Component({
    selector: 'jhi-plagiarism-split-view',
    styleUrls: ['./plagiarism-split-view.component.scss'],
    templateUrl: './plagiarism-split-view.component.html',
    imports: [SplitPaneDirective, TextSubmissionViewerComponent, FaIconComponent],
})
export class PlagiarismSplitViewComponent implements AfterViewInit, OnInit, OnDestroy {
    private plagiarismCasesService = inject(PlagiarismCasesService);

    readonly comparison = input<PlagiarismComparison | undefined>(undefined!);
    readonly exercise = input<Exercise>();
    readonly splitControlSubject = input<Subject<string>>();
    readonly sortByStudentLogin = input<string>();
    readonly forStudent = input<boolean>();

    readonly panes = viewChildren(SplitPaneDirective);

    readonly plagiarismComparison = signal<PlagiarismComparison | undefined>(undefined);
    fileSelectedSubject = new Subject<PlagiarismFileElement>();
    showFilesSubject = new Subject<boolean>();
    dropdownHoverSubject = new Subject<PlagiarismFileElement>();

    public split: Split.Instance;

    // Replaces the ngOnChanges 'exercise' branch: pure derived state, recomputed whenever the exercise input changes.
    readonly isProgrammingOrTextExercise = computed(() => {
        const exercise = this.exercise();
        return exercise?.type === ExerciseType.PROGRAMMING || exercise?.type === ExerciseType.TEXT;
    });

    readonly matchesA = signal<Map<string, FromToElement[]> | undefined>(undefined);
    readonly matchesB = signal<Map<string, FromToElement[]> | undefined>(undefined);
    readonly isLockFilesEnabled = signal(false);

    readonly dayjs = dayjs;
    protected readonly faLock: IconDefinition = faLock;
    protected readonly faUnlock: IconDefinition = faUnlock;

    constructor() {
        // Replaces the ngOnChanges 'comparison' branch: fetch the full comparison for the split view whenever the
        // selected comparison changes. The fetch and downstream signal writes run untracked so only comparison()
        // retriggers the effect; exercise()/sortByStudentLogin() are read at fetch time, not as triggers.
        effect(() => {
            const comparison = this.comparison();
            if (comparison) {
                untracked(() => {
                    this.plagiarismCasesService
                        .getPlagiarismComparisonForSplitView(getCourseId(this.exercise())!, comparison.id)
                        .subscribe((resp: HttpResponse<PlagiarismComparison>) => {
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
            }
        });
    }

    /**
     * Initialize third-party libraries inside this lifecycle hook.
     */
    ngAfterViewInit(): void {
        const paneElements = this.panes().map((pane: SplitPaneDirective) => pane.elementRef.nativeElement);

        this.split = Split(paneElements, {
            minSize: 100,
            sizes: [50, 50],
            gutterSize: 8,
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
        return this.plagiarismComparison()!.submissionA as PlagiarismSubmission;
    }

    getTextSubmissionB() {
        return this.plagiarismComparison()!.submissionB as PlagiarismSubmission;
    }

    handleSplitControl(pane: string) {
        switch (pane) {
            case 'left': {
                this.split.collapse(1);
                return;
            }
            case 'right': {
                this.split.collapse(0);
                return;
            }
            case 'even': {
                this.split.setSizes([50, 50]);
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
