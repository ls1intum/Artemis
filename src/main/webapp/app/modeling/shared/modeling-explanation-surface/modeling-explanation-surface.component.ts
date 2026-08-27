import { AfterViewInit, Component, ElementRef, OnDestroy, computed, contentChild, input, signal, viewChild } from '@angular/core';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ResizableDirective, ResizableSizeEvent } from 'app/shared-ui/directives/resizable.directive';

@Component({
    selector: 'jhi-modeling-explanation-surface',
    templateUrl: './modeling-explanation-surface.component.html',
    styleUrls: ['./modeling-explanation-surface.component.scss'],
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, ResizableDirective],
})
export class ModelingExplanationSurfaceComponent implements AfterViewInit, OnDestroy {
    readonly labelId = input.required<string>();
    readonly labelKey = input('artemisApp.modelingSubmission.explanationText');
    readonly icon = input<IconDefinition>(faCommentDots);
    readonly notchWidth = input(104);
    readonly minHeight = input(42);
    readonly maxHeight = input(320);

    private readonly surface = viewChild<ElementRef<HTMLElement>>('surface');
    private readonly notch = viewChild<ElementRef<HTMLElement>>('notch');
    private readonly autosize = contentChild(CdkTextareaAutosize);
    private readonly resizableContent = contentChild<ElementRef<HTMLElement>>('resizableContent');
    private readonly surfaceWidth = signal<number | undefined>(undefined);
    private readonly notchContentWidth = signal(0);
    private resizeObserver?: ResizeObserver;
    private notchMutationObserver?: MutationObserver;
    protected readonly manuallySized = signal(false);
    protected readonly effectiveNotchWidth = computed(() => {
        const requestedWidth = Math.max(this.notchWidth(), this.notchContentWidth());
        return Math.min(requestedWidth, this.surfaceWidth() ?? requestedWidth);
    });
    protected readonly notchPath = computed(() => {
        const right = this.effectiveNotchWidth() - 0.5;
        const curveStart = right - 8.5;
        return `M 0.5 1 V -13 C 0.5 -17.7 4.3 -21.5 9 -21.5 H ${curveStart} C ${right - 3.8} -21.5 ${right} -17.7 ${right} -13 V 1`;
    });

    ngAfterViewInit(): void {
        const surface = this.surface()?.nativeElement;
        if (!surface) {
            return;
        }

        const updateMeasurements = () => {
            const width = surface.getBoundingClientRect().width;
            if (width > 0) {
                this.surfaceWidth.set(width);
            }

            const notch = this.notch()?.nativeElement;
            if (notch) {
                const style = getComputedStyle(notch);
                const gap = Number.parseFloat(style.columnGap) || 0;
                const horizontalPadding = (Number.parseFloat(style.paddingLeft) || 0) + (Number.parseFloat(style.paddingRight) || 0);
                const children = Array.from(notch.children) as HTMLElement[];
                const contentWidth = children.reduce((total, child) => total + Math.max(child.scrollWidth, child.getBoundingClientRect().width), 0);
                this.notchContentWidth.set(Math.ceil(horizontalPadding + contentWidth + Math.max(0, children.length - 1) * gap));
            }
        };
        updateMeasurements();
        this.resizeObserver = new ResizeObserver(updateMeasurements);
        this.resizeObserver.observe(surface);
        const notch = this.notch()?.nativeElement;
        if (notch) {
            this.notchMutationObserver = new MutationObserver(updateMeasurements);
            this.notchMutationObserver.observe(notch, { childList: true, characterData: true, subtree: true });
        }
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
        this.notchMutationObserver?.disconnect();
    }

    protected startManualResize(_size: ResizableSizeEvent): void {
        const content = this.resizableContent()?.nativeElement;
        content?.style.setProperty('height', '100%', 'important');
        content?.style.setProperty('max-height', 'none', 'important');
        this.manuallySized.set(true);
    }

    protected resetManualSize(): void {
        this.surface()?.nativeElement.style.removeProperty('height');
        const content = this.resizableContent()?.nativeElement;
        content?.style.removeProperty('height');
        content?.style.removeProperty('max-height');
        this.manuallySized.set(false);
        this.autosize()?.resizeToFitContent(true);
    }
}
