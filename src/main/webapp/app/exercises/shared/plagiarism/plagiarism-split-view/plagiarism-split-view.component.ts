import { AfterViewInit, Component, Directive, ElementRef, Input, OnChanges, OnInit, QueryList, SimpleChanges, ViewChildren } from '@angular/core';
// @ts-ignore
import Split from 'split.js';
import { Subject } from 'rxjs';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { Exercise, ExerciseType, getCourseId } from 'app/entities/exercise.model';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { HttpResponse } from '@angular/common/http';

@Directive({ selector: '[jhiPane]' })
export class SplitPaneDirective {
    constructor(public el: ElementRef) {}
}

@Component({
    selector: 'jhi-plagiarism-split-view',
    styleUrls: ['./plagiarism-split-view.component.scss'],
    templateUrl: './plagiarism-split-view.component.html',
})
export class PlagiarismSplitViewComponent implements AfterViewInit, OnChanges, OnInit {
    @Input() comparison: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    @Input() exercise: Exercise;
    @Input() splitControlSubject: Subject<string>;
    @Input() sortByStudentLogin: string;

    @ViewChildren(SplitPaneDirective) panes!: QueryList<SplitPaneDirective>;

    plagiarismComparison: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;

    public split: Split.Instance;

    public isModelingExercise: boolean;
    public isProgrammingOrTextExercise: boolean;

    public matchesA: Map<string, { from: TextSubmissionElement; to: TextSubmissionElement }[]>;
    public matchesB: Map<string, { from: TextSubmissionElement; to: TextSubmissionElement }[]>;

    constructor(private plagiarismCasesService: PlagiarismCasesService) {}

    /**
     * Initialize third party libs inside this lifecycle hook.
     */
    ngAfterViewInit(): void {
        const paneElements = this.panes.map((pane: SplitPaneDirective) => pane.el.nativeElement);

        this.split = Split(paneElements, {
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

    /**
     * Swaps fields of A with fields of B in-place.
     * More specifically, swaps submissionA with submissionB and startA with startB in matches.
     * @param plagiarismComparison plagiarism comparison that will be modified in-place
     * @private
     */
    private swapSubmissions(plagiarismComparison: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>) {
        const temp = plagiarismComparison.submissionA;
        plagiarismComparison.submissionA = plagiarismComparison.submissionB;
        plagiarismComparison.submissionB = temp;

        plagiarismComparison.matches.forEach((match) => {
            const tempStart = match.startA;
            match.startA = match.startB;
            match.startB = tempStart;
        });
    }

    parseTextMatches({ submissionA, submissionB, matches }: PlagiarismComparison<TextSubmissionElement>) {
        const matchesA = matches
            .map((match) => ({
                start: match.startA,
                length: match.length,
            }))
            .sort((a, b) => a.start - b.start);

        const matchesB = matches
            .map((match) => ({
                start: match.startB,
                length: match.length,
            }))
            .sort((a, b) => a.start - b.start);

        this.matchesA = this.mapMatchesToElements(matchesA, submissionA);
        this.matchesB = this.mapMatchesToElements(matchesB, submissionB);
    }

    /**
     * Create a map of file names to matches elements.
     * @param matches list of objects containing the index and length of matched elements
     * @param submission the submission to map the elements of
     */
    mapMatchesToElements(matches: { start: number; length: number }[], submission: PlagiarismSubmission<TextSubmissionElement>) {
        const filesToMatchedElements = new Map();

        matches.forEach(({ start, length }) => {
            // skip empty jplag (whitespace) matches
            if (length === 0) {
                return;
            }
            const file = submission.elements![start]?.file || 'none';

            if (!filesToMatchedElements.has(file)) {
                filesToMatchedElements.set(file, []);
            }

            const fileMatches = filesToMatchedElements.get(file)!;

            fileMatches.push({
                from: submission.elements![start],
                to: submission.elements![start + length - 1],
            });
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
}
