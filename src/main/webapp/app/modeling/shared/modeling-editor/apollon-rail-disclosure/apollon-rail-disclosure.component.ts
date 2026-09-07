import { Component, ElementRef, computed, input, linkedSignal, model, output, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ResizableDirective, ResizableSizeEvent } from 'app/shared-ui/directives/resizable.directive';

let nextDisclosureId = 0;

/** Resizable panel projected from an Apollon rail without reserving canvas width. */
@Component({
    selector: 'jhi-apollon-rail-disclosure',
    templateUrl: './apollon-rail-disclosure.component.html',
    styleUrls: ['./apollon-rail-disclosure.component.scss'],
    imports: [ArtemisTranslatePipe, FaIconComponent, ResizableDirective],
    host: {
        class: 'apollon-rail-disclosure nopan nodrag nowheel',
        '[class.apollon-rail-disclosure--expanded]': 'visible()',
        '[attr.aria-label]': 'label()',
    },
})
export class ApollonRailDisclosureComponent {
    private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
    protected readonly panelId = `apollon-rail-disclosure-${++nextDisclosureId}`;
    protected readonly faExpand = faChevronDown;
    protected readonly faCollapse = faChevronUp;

    readonly label = input.required<string>();
    readonly icon = input.required<IconDefinition>();
    readonly testId = input<string>();
    readonly visible = model(false);
    readonly maxHeight = input(720);
    readonly initialWidth = input(416);
    readonly initialHeight = input<number | undefined>(undefined);

    readonly resized = output<ResizableSizeEvent>();

    protected readonly width = linkedSignal(() => this.initialWidth());
    protected readonly height = linkedSignal<number | undefined>(() => this.initialHeight());
    protected readonly resizeConstraints = computed(() => ({ minWidth: 288, maxWidth: 704, minHeight: 224, maxHeight: this.maxHeight() }));

    getPanelElement(): HTMLElement | undefined {
        return this.panel()?.nativeElement;
    }

    getVisiblePanelRect(): DOMRect | undefined {
        return this.visible() ? this.getPanelElement()?.getBoundingClientRect() : undefined;
    }

    protected toggle(): void {
        this.visible.update((visible) => !visible);
    }

    protected resize(event: ResizableSizeEvent): void {
        this.width.set(event.width);
        this.height.set(event.height);
        this.resized.emit(event);
    }
}
