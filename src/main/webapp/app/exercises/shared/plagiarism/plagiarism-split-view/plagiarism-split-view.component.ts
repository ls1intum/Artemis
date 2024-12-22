import { AfterViewInit, Component, Directive, ElementRef, Input, OnChanges, OnDestroy, OnInit, QueryList, SimpleChanges, ViewChildren } from '@angular/core';
import * as Split from 'split.js';
import { Subject } from 'rxjs';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { FromToElement, TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { Exercise, ExerciseType, getCourseId } from 'app/entities/exercise.model';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { HttpResponse } from '@angular/common/http';
import { SimpleMatch } from 'app/exercises/shared/plagiarism/types/PlagiarismMatch';
import dayjs from 'dayjs/esm';
import { TextPlagiarismFileElement } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismFileElement';
import { IconDefinition, faLock, faUnlock } from '@fortawesome/free-solid-svg-icons';
import { ModelingSubmissionViewerComponent } from './modeling-submission-viewer/modeling-submission-viewer.component';
import { TextSubmissionViewerComponent } from './text-submission-viewer/text-submission-viewer.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Directive({
    selector: '[jhiPane]',
    standalone: true,
})
export class SplitPaneDirective {
    constructor(public elementRef: ElementRef) {}
}

@Component({
    selector: 'jhi-plagiarism-split-view',
    styleUrls: ['./plagiarism-split-view.component.scss'],
    templateUrl: './plagiarism-split-view.component.html',
    standalone: true,
    imports: [SplitPaneDirective, ModelingSubmissionViewerComponent, TextSubmissionViewerComponent, FaIconComponent],
})
export class PlagiarismSplitViewComponent implements AfterViewInit, OnChanges, OnInit, OnDestroy {
    @Input() comparison?: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    @Input() exercise: Exercise;
    @Input() splitControlSubject: Subject<string>;
    @Input() sortByStudentLogin: string;
    @Input() forStudent: boolean;

    @ViewChildren(SplitPaneDirective) panes!: QueryList<SplitPaneDirective>;

    plagiarismComparison: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    fileSelectedSubject = new Subject<TextPlagiarismFileElement>();
    showFilesSubject = new Subject<boolean>();
    dropdownHoverSubject = new Subject<TextPlagiarismFileElement>();

    public split: Split.Instance;

    public isModelingExercise: boolean;
    public isProgrammingOrTextExercise: boolean;

    public matchesA: Map<string, FromToElement[]>;
    public matchesB: Map<string, FromToElement[]>;
    isLockFilesEnabled = false;

    readonly dayjs = dayjs;
    protected readonly faLock: IconDefinition = faLock;
    protected readonly faUnlock: IconDefinition = faUnlock;

    constructor(private plagiarismCasesService: PlagiarismCasesService) {}

    /**
     * Initialize third-party libraries inside this lifecycle hook.
     */
    ngAfterViewInit(): void {
        const paneElements = this.panes.map((pane: SplitPaneDirective) => pane.elementRef.nativeElement);

        this.split = Split.default(paneElements, {
            minSize: 100,
            sizes: [50, 50],
            gutterSize: 8,
        });
    }

    ngOnInit(): void {
        this.splitControlSubject.subscribe((pane: string) => this.handleSplitControl(pane));
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.exercise) {
            const exercise = changes.exercise.currentValue;

            this.isModelingExercise = exercise.type === ExerciseType.MODELING;
            this.isProgrammingOrTextExercise = exercise.type === ExerciseType.PROGRAMMING || exercise.type === ExerciseType.TEXT;
        }

        if (changes.comparison) {
            this.plagiarismCasesService
                .getPlagiarismComparisonForSplitView(getCourseId(this.exercise)!, changes.comparison.currentValue.id)
                .subscribe((resp: HttpResponse<PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>>) => {
                    this.plagiarismComparison = resp.body!;
                    if (this.sortByStudentLogin && this.sortByStudentLogin === this.plagiarismComparison.submissionB.studentLogin) {
                        this.swapSubmissions(this.plagiarismComparison);
                    }
                    if (this.isProgrammingOrTextExercise) {
                        this.parseTextMatches(this.plagiarismComparison as PlagiarismComparison<TextSubmissionElement>);
                    }
                });
        }
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
    private swapSubmissions(plagiarismComparison: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>) {
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

    parseTextMatches(plagComparison: PlagiarismComparison<TextSubmissionElement>) {
        if (plagComparison.matches) {
            const matchesA = plagComparison.matches.map((match) => new SimpleMatch(match.startA, match.length)).sort((m1, m2) => m1.start - m2.start);
            const matchesB = plagComparison.matches.map((match) => new SimpleMatch(match.startB, match.length)).sort((m1, m2) => m1.start - m2.start);

            this.matchesA = this.mapMatchesToElements(matchesA, plagComparison.submissionA);
            this.matchesB = this.mapMatchesToElements(matchesB, plagComparison.submissionB);
        } else {
            // empty map in case no matches are available
            this.matchesA = new Map<string, FromToElement[]>();
            this.matchesB = new Map<string, FromToElement[]>();
        }
    }

    /**
     * Create a map of file names to matches elements.
     * @param matches list of objects containing the index and length of matched elements
     * @param submission the submission to map the elements of
     */
    mapMatchesToElements(matches: SimpleMatch[], submission: PlagiarismSubmission<TextSubmissionElement>) {
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

    getModelingSubmissionA() {
        return this.plagiarismComparison.submissionA as PlagiarismSubmission<ModelingSubmissionElement>;
    }

    getModelingSubmissionB() {
        return this.plagiarismComparison.submissionB as PlagiarismSubmission<ModelingSubmissionElement>;
    }

    getTextSubmissionA() {
        return this.plagiarismComparison.submissionA as PlagiarismSubmission<TextSubmissionElement>;
    }

    getTextSubmissionB() {
        return this.plagiarismComparison.submissionB as PlagiarismSubmission<TextSubmissionElement>;
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
        this.isLockFilesEnabled = !this.isLockFilesEnabled;
    }
}
