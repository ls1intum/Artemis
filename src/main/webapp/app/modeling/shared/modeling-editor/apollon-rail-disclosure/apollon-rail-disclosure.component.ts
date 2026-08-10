import { Component, computed, input, linkedSignal, model, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ResizableDirective, ResizableSizeEvent } from 'app/shared-ui/directives/resizable.directive';

let nextDisclosureId = 0;

/**
 * A side panel that hangs off one of Apollon's rails: a chrome-styled trigger island and a resizable
 * panel that floats out from underneath it. The host element is what a caller hands to
 * `editor.getRegionElement('right-rail')`.
 *
 * The panel floats over the canvas instead of reserving a column, so the rail only ever gives up the
 * trigger's width and the diagram keeps its framing whether the panel is open or shut.
 */
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
    protected readonly panelId = `apollon-rail-disclosure-${++nextDisclosureId}`;
    protected readonly faExpand = faChevronDown;
    protected readonly faCollapse = faChevronUp;

    /** Names the surface on the trigger, and labels the region for assistive tech. */
    readonly label = input.required<string>();
    readonly icon = input.required<IconDefinition>();
    readonly testId = input<string>();
    readonly visible = model(false);
    /** Only the host can see what else the editor has parked below the trigger; see `calculateRailDisclosureMaxHeight`. */
    readonly maxHeight = input(720);
    readonly initialWidth = input(416);
    /** Undefined sizes the panel to its content (capped by {@link maxHeight}); set it only for a surface that is always long. */
    readonly initialHeight = input<number | undefined>(undefined);

    readonly resized = output<ResizableSizeEvent>();

    protected readonly width = linkedSignal(() => this.initialWidth());
    protected readonly height = linkedSignal<number | undefined>(() => this.initialHeight());
    protected readonly resizeConstraints = computed(() => ({ minWidth: 288, maxWidth: 704, minHeight: 224, maxHeight: this.maxHeight() }));

    protected toggle(): void {
        this.visible.update((visible) => !visible);
    }

    protected resize(event: ResizableSizeEvent): void {
        this.width.set(event.width);
        this.height.set(event.height);
        this.resized.emit(event);
    }
}
