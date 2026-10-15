import { AfterViewInit, Component, ElementRef, OnDestroy, computed, inject, input, signal } from '@angular/core';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { SplitterModule } from 'primeng/splitter';
import { SPLITTER_GUTTER_SIZE, splitterPassThrough } from 'app/assessment/manage/assessment-workspace/splitter-pass-through';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

const STACK_PRIMARY_AT = 840;
const SPLIT_SUPPORT_AT = 560;

@Component({
    selector: 'jhi-assessment-workspace',
    templateUrl: './assessment-workspace.component.html',
    styleUrls: ['./assessment-workspace.component.scss'],
    imports: [CdkScrollable, FaIconComponent, NgTemplateOutlet, SplitterModule, TranslateDirective],
})
export class AssessmentWorkspaceComponent implements AfterViewInit, OnDestroy {
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly width = signal(Number.POSITIVE_INFINITY);
    private resizeObserver?: ResizeObserver;

    readonly detailsLabelKey = input('artemisApp.assessment.feedbackAndNotes');
    readonly showDetails = input(true);
    /** Whether the page fills the details header itself through `[assessmentWorkspaceDetailsHeader]`, e.g. with tabs. */
    readonly customDetailsHeader = input(false);
    readonly storageKey = input.required<string>();

    protected readonly sidebarStorageKey = computed(() => `${this.storageKey()}-support`);
    protected readonly primaryVertical = computed(() => this.width() < STACK_PRIMARY_AT);
    protected readonly supportHorizontal = computed(() => this.width() >= SPLIT_SUPPORT_AT && this.primaryVertical());
    protected readonly primaryLayout = computed(() => (this.primaryVertical() ? 'vertical' : 'horizontal'));
    protected readonly supportLayout = computed(() => (this.supportHorizontal() ? 'horizontal' : 'vertical'));
    protected readonly primarySizes = computed(() => (this.primaryVertical() ? [60, 40] : [65, 35]));
    protected readonly primaryPassThrough = computed(() => splitterPassThrough(this.primaryLayout()));
    protected readonly supportPassThrough = computed(() => splitterPassThrough(this.supportLayout()));
    protected readonly gutterSize = SPLITTER_GUTTER_SIZE;
    protected readonly faInstructions = faListAlt;
    protected readonly faDetails = faCommentDots;

    ngAfterViewInit(): void {
        this.resizeObserver = new ResizeObserver(([entry]) => this.width.set(entry.contentRect.width));
        this.resizeObserver.observe(this.elementRef.nativeElement);
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }
}
