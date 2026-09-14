import { AfterViewInit, Component, ElementRef, OnDestroy, computed, inject, input, signal } from '@angular/core';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { SplitterModule } from 'primeng/splitter';
import type { SplitterPassThrough } from 'primeng/types/splitter';
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
    readonly storageKey = input.required<string>();

    protected readonly sidebarStorageKey = computed(() => `${this.storageKey()}-support`);
    protected readonly primaryVertical = computed(() => this.width() < STACK_PRIMARY_AT);
    protected readonly supportHorizontal = computed(() => this.width() >= SPLIT_SUPPORT_AT && this.primaryVertical());
    protected readonly primaryLayout = computed(() => (this.primaryVertical() ? 'vertical' : 'horizontal'));
    protected readonly supportLayout = computed(() => (this.supportHorizontal() ? 'horizontal' : 'vertical'));
    protected readonly primarySizes = computed(() => (this.primaryVertical() ? [60, 40] : [65, 35]));
    protected readonly primaryPassThrough = computed(() => this.splitterPassThrough(this.primaryLayout()));
    protected readonly supportPassThrough = computed(() => this.splitterPassThrough(this.supportLayout()));
    protected readonly faInstructions = faListAlt;
    protected readonly faDetails = faCommentDots;

    ngAfterViewInit(): void {
        this.resizeObserver = new ResizeObserver(([entry]) => this.width.set(entry.contentRect.width));
        this.resizeObserver.observe(this.elementRef.nativeElement);
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }

    private splitterPassThrough(layout: 'horizontal' | 'vertical'): SplitterPassThrough {
        const dividerColor = 'color-mix(in srgb, var(--tumaet-ui-text-color) 62%, transparent)';
        const handleBackground =
            layout === 'horizontal'
                ? `linear-gradient(to right, transparent 0 3px, ${dividerColor} 3px 5px, transparent 5px 7px, ${dividerColor} 7px 9px, transparent 9px)`
                : `linear-gradient(to bottom, transparent 0 3px, ${dividerColor} 3px 5px, transparent 5px 7px, ${dividerColor} 7px 9px, transparent 9px)`;
        return {
            root: { style: { border: '0', background: 'transparent' } },
            panel: { style: { minWidth: '0', minHeight: '0', overflow: 'hidden' } },
            gutter: {
                style: {
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: 'transparent',
                    cursor: layout === 'horizontal' ? 'ew-resize' : 'ns-resize',
                },
            },
            gutterHandle: {
                style: {
                    width: layout === 'horizontal' ? '0.75rem' : '1.25rem',
                    height: layout === 'horizontal' ? '1.25rem' : '0.75rem',
                    background: handleBackground,
                    pointerEvents: 'none',
                },
            },
        };
    }
}
