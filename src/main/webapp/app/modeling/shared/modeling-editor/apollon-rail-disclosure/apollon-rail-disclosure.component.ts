import { Component, computed, input, linkedSignal, model, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ResizableDirective, ResizableSizeEvent } from 'app/shared-ui/directives/resizable.directive';

let nextDisclosureId = 0;

/**
 * A side panel that hangs off one of Apollon's rails: a chrome-styled trigger
 * island, and a resizable glass panel that floats out from underneath it.
 *
 * The panel floats *over* the canvas rather than reserving a column, so the rail
 * only ever gives up the trigger's width. That is what keeps the diagram framed
 * the same whether the panel is open or shut, and it is why collapsing is cheap
 * enough to be the reader's decision rather than a layout event.
 *
 * The host is the element you hand to `editor.getRegionElement('right-rail')`.
 * Both the editor's problem statement and the assessment's feedback list use it,
 * so the two surfaces cannot drift into two dialects of the same affordance.
 */
@Component({
    selector: 'jhi-apollon-rail-disclosure',
    templateUrl: './apollon-rail-disclosure.component.html',
    styleUrls: ['./apollon-rail-disclosure.component.scss'],
    imports: [ArtemisTranslatePipe, FaIconComponent, ResizableDirective],
    host: {
        class: 'apollon-rail-disclosure nopan nodrag nowheel',
        '[class.apollon-rail-disclosure--expanded]': 'visible()',
        '[class.apollon-rail-disclosure--docked]': 'docked()',
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
    /**
     * Docked, the panel is part of the rail: it reserves its own width, so the canvas
     * shrinks beside it instead of being covered. Floating (the default) keeps the
     * canvas full-bleed and hangs the panel over it, which is right when vertical
     * room is scarce and the diagram is the workspace. Docking is right when the
     * surface is being read rather than edited and there is width to spare.
     */
    readonly docked = input(false);
    readonly visible = model(false);
    /**
     * Set by the host, which is the only party that can see what else the editor
     * has parked below the trigger. See `calculateRailDisclosureMaxHeight`.
     */
    readonly maxHeight = input(720);
    readonly initialWidth = input(416);
    /**
     * Leave undefined to let the panel be as tall as its content (capped by
     * `maxHeight`). A fixed height suits a surface that is always long, like a
     * problem statement; a feedback list with three entries should not open a
     * half-empty card.
     */
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
