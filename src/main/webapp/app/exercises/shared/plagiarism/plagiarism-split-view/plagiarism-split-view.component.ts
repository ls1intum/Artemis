import { AfterViewInit, Component, Directive, ElementRef, Input, OnChanges, OnInit, QueryList, SimpleChanges, ViewChildren } from '@angular/core';
// @ts-ignore
import Split from 'split.js';
import { Subject } from 'rxjs';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';

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
    @Input() splitControlSubject: Subject<string>;

    public submissionA: ModelingSubmission;
    public submissionB: ModelingSubmission;

    public loadingSubmissionA: boolean;
    public loadingSubmissionB: boolean;

    @ViewChildren(SplitPaneDirective) panes!: QueryList<SplitPaneDirective>;

    private split: Split.Instance;

    constructor(private submissionService: ModelingSubmissionService) {}

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

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.comparison) {
            this.loadingSubmissionA = true;
            this.loadingSubmissionB = true;

            const currentComparison: PlagiarismComparison<ModelingSubmissionElement> = changes.comparison.currentValue;

            this.submissionService.getSubmission(currentComparison.submissionA.submissionId).subscribe((submission: ModelingSubmission) => {
                this.loadingSubmissionA = false;

                submission.model = JSON.parse(submission.model!);
                this.submissionA = submission;
            });

            this.submissionService.getSubmission(currentComparison.submissionB.submissionId).subscribe((submission: ModelingSubmission) => {
                this.loadingSubmissionB = false;

                submission.model = JSON.parse(submission.model!);
                this.submissionB = submission;
            });
        }
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
