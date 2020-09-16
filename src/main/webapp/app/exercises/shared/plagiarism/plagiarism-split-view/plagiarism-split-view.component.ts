import { AfterViewInit, Component, Directive, ElementRef, Input, OnInit, QueryList, ViewChildren } from '@angular/core';
// @ts-ignore
import Split from 'split.js';
import { Observable } from 'rxjs';

@Directive({ selector: '[jhiPane]' })
export class SplitPaneDirective {
    constructor(public el: ElementRef) {}
}

@Component({
    selector: 'jhi-plagiarism-split-view',
    styleUrls: ['./plagiarism-split-view.component.scss'],
    templateUrl: './plagiarism-split-view.component.html',
})
export class PlagiarismSplitViewComponent implements AfterViewInit, OnInit {
    @Input() splitControl: Observable<string>;

    @ViewChildren(SplitPaneDirective) panes!: QueryList<SplitPaneDirective>;

    private split: Split;

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
            case 'half': {
                this.split.setSizes([50, 50]);
            }
        }
    }
}
