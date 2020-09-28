import { AfterViewInit, Component, Directive, ElementRef, Input, OnChanges, OnInit, QueryList, SimpleChanges, ViewChildren } from '@angular/core';
// @ts-ignore
import Split from 'split.js';
import { Observable } from 'rxjs';
import { ModelingSubmissionComparisonDTO } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
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
    @Input() splitControl: Observable<string>;
    @Input() comparison: ModelingSubmissionComparisonDTO;

    submission1: ModelingSubmission;
    submission2: ModelingSubmission;

    @ViewChildren(SplitPaneDirective) panes!: QueryList<SplitPaneDirective>;

    private split: Split;

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
        this.splitControl.subscribe((pane: string) => this.handleSplitControl(pane));
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.comparison) {
            const comp: ModelingSubmissionComparisonDTO = changes.comparison.currentValue;

            this.submissionService.getSubmission(comp.element1.submissionId).subscribe((submission: ModelingSubmission) => {
                submission.model = JSON.parse(submission.model);
                this.submission1 = submission;
            });

            this.submissionService.getSubmission(comp.element2.submissionId).subscribe((submission) => {
                submission.model = JSON.parse(submission.model);
                this.submission2 = submission;
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
