import { Component, ElementRef, ViewEncapsulation, effect, input, output, signal, viewChild } from '@angular/core';

/**
 * Output event payload emitted when the user rates by clicking a star.
 */
export interface StarRatingChangeEvent {
    /** The rating value before the change */
    oldValue: number;
    /** The new rating value after the change */
    newValue: number;
}

/**
 * A star rating component that allows users to rate items on a configurable scale.
 *
 * This component renders a row of clickable stars that can be used for rating.
 * It supports half-star ratings (for display), customizable colors, sizes,
 * and read-only mode.
 *
 * Uses Angular signals for reactive input handling and Shadow DOM encapsulation
 * to isolate styles.
 *
 * @example
 * ```html
 * <star-rating
 *   [value]="3.5"
 *   [totalStars]="5"
 *   [checkedColor]="'gold'"
 *   [uncheckedColor]="'gray'"
 *   [size]="'32px'"
 *   [readOnly]="false"
 *   (rate)="onRatingChange($event)">
 * </star-rating>
 * ```
 */
@Component({
    selector: 'star-rating',
    template: `<div #starMain></div>`,
    styleUrls: ['./star-rating.component.scss'],
    encapsulation: ViewEncapsulation.ShadowDom,
})
export class StarRatingComponent {
    // =========================================================================
    // CSS Custom Property Names (used for styling stars via Shadow DOM)
    // =========================================================================
    private static readonly VAR_CHECKED_COLOR = '--checkedColor';
    private static readonly VAR_UNCHECKED_COLOR = '--unCheckedColor';
    private static readonly VAR_SIZE = '--size';
    private static readonly VAR_HALF_WIDTH = '--halfWidth';

    // =========================================================================
    // CSS Class Names (applied to star elements for styling)
    // =========================================================================
    private static readonly CLS_CHECKED_STAR = 'on';
    private static readonly CLS_DEFAULT_STAR = 'star';
    private static readonly CLS_HALF_STAR = 'half';

    // =========================================================================
    // View References
    // =========================================================================

    /** Reference to the main container element that holds all star elements */
    private readonly starContainer = viewChild.required<ElementRef>('starMain');

    // =========================================================================
    // Signal Inputs
    // =========================================================================

    /**
     * The color applied to checked (filled) stars.
     * @example 'gold', '#FFD700', 'rgb(255, 215, 0)'
     */
    readonly checkedColor = input<string | undefined>(undefined);

    /**
     * The color applied to unchecked (empty) stars.
     * @example 'gray', '#808080', 'rgb(128, 128, 128)'
     */
    readonly uncheckedColor = input<string | undefined>(undefined);

    /**
     * The current rating value input. Supports decimal values for half-star display.
     * Use the `currentRating` getter/setter for programmatic access to the internal value.
     */
    readonly value = input<number>(0);

    /**
     * The size of each star. Can be specified with or without 'px' suffix.
     * Use the `normalizedSize` getter for the value with guaranteed 'px' suffix.
     * @example '24px', '32', '48px'
     */
    readonly size = input<string>('24px');

    /**
     * When true, the rating cannot be changed by user interaction.
     * Stars will not respond to hover or click events.
     */
    readonly readOnly = input<boolean>(false);

    /**
     * The total number of stars to display.
     * Use the `starCount` getter for the validated value.
     * Invalid values (<=0) default to 5.
     */
    readonly totalStars = input<number>(5);

    // =========================================================================
    // Internal State
    // =========================================================================

    /** Array of star DOM elements managed by this component */
    private starElements: HTMLElement[] = [];

    /**
     * Internal writable signal for the current rating value.
     * This is separate from valueInput because the value can be changed
     * both externally (via input) and internally (via user click).
     */
    private readonly currentValue = signal<number>(0);

    /**
     * Tracks whether the component has completed initial setup.
     * Prevents effects from running before the view is ready.
     */
    private readonly isInitialized = signal(false);

    // =========================================================================
    // Bound Event Handlers (stored to enable proper removal)
    // =========================================================================

    /** Bound reference to handleMouseLeave for container mouseleave event */
    private readonly boundHandleMouseLeave = this.handleMouseLeave.bind(this);

    /** Bound reference to handleStarClick for star click events */
    private readonly boundHandleStarClick = this.handleStarClick.bind(this);

    /** Bound reference to handleStarHover for star mouseenter events */
    private readonly boundHandleStarHover = this.handleStarHover.bind(this);

    // =========================================================================
    // Outputs
    // =========================================================================

    /**
     * Emits when the user clicks a star to change the rating.
     * Only emits when readOnly is false.
     */
    readonly rate = output<StarRatingChangeEvent>();

    // =========================================================================
    // Public Accessors (for template and external access)
    // =========================================================================

    /** Gets the current rating value from internal state */
    get currentRating(): number {
        return this.currentValue();
    }

    /**
     * Sets the current rating value.
     * Falsy values (null, undefined, 0) are normalized to 0.
     */
    set currentRating(newValue: number) {
        const normalizedValue = newValue || 0;
        this.currentValue.set(normalizedValue);
    }

    /**
     * Gets the star size with 'px' suffix guaranteed.
     * @returns Size string with 'px' suffix (e.g., '24px')
     */
    get normalizedSize(): string {
        const sizeValue = this.size() || '24px';
        return sizeValue.includes('px') ? sizeValue : `${sizeValue}px`;
    }

    /** Gets whether the component is in read-only mode */
    get isReadOnly(): boolean {
        return this.readOnly();
    }

    /**
     * Gets the total number of stars to display.
     * Invalid values are normalized to 5.
     */
    get starCount(): number {
        const inputValue = this.totalStars();
        return inputValue <= 0 ? 5 : Math.round(inputValue);
    }

    // =========================================================================
    // Constructor with Reactive Effects
    // =========================================================================

    constructor() {
        this.setupTotalStarsEffect();
        this.setupValueInputSyncEffect();
        this.setupValueChangeEffect();
        this.setupCheckedColorEffect();
        this.setupUncheckedColorEffect();
        this.setupSizeEffect();
        this.setupReadOnlyEffect();
        this.setupInitializationEffect();
    }

    /**
     * Effect: Rebuilds stars when totalStars input changes.
     * Must recreate all star elements and re-apply styles/events.
     */
    private setupTotalStarsEffect(): void {
        effect(() => {
            // Access totalStars signal to subscribe to changes
            // The value is used implicitly via this.starCount getter in rebuildStarDisplay
            void this.totalStars();
            if (this.isInitialized()) {
                this.rebuildStarDisplay();
            }
        });
    }

    /**
     * Effect: Syncs the external value input to internal currentValue signal.
     * This ensures the component responds to programmatic value changes.
     */
    private setupValueInputSyncEffect(): void {
        effect(() => {
            const externalValue = this.value();
            const normalizedValue = externalValue || 0;
            this.currentValue.set(normalizedValue);
        });
    }

    /**
     * Effect: Updates star display when the internal value changes.
     * Triggers visual update of which stars are filled/half-filled.
     */
    private setupValueChangeEffect(): void {
        effect(() => {
            const ratingValue = this.currentValue();
            if (this.isInitialized() && ratingValue >= 0) {
                this.renderStarStates();
                this.applyStarSizes();
            }
        });
    }

    /**
     * Effect: Applies checked color to all stars when it changes.
     */
    private setupCheckedColorEffect(): void {
        effect(() => {
            const color = this.checkedColor();
            if (this.isInitialized() && color) {
                this.applyColorToAllStars(true);
            }
        });
    }

    /**
     * Effect: Applies unchecked color to all stars when it changes.
     */
    private setupUncheckedColorEffect(): void {
        effect(() => {
            const color = this.uncheckedColor();
            if (this.isInitialized() && color) {
                this.applyColorToAllStars(false);
            }
        });
    }

    /**
     * Effect: Applies new size to all stars when size input changes.
     */
    private setupSizeEffect(): void {
        effect(() => {
            // Access size signal to subscribe to changes
            // The value is used implicitly via this.normalizedSize getter in applyStarSizes
            void this.size();
            if (this.isInitialized()) {
                this.applyStarSizes();
            }
        });
    }

    /**
     * Effect: Toggles editable/read-only mode when readOnly input changes.
     */
    private setupReadOnlyEffect(): void {
        effect(() => {
            const isReadOnlyValue = this.readOnly();
            if (this.isInitialized()) {
                if (isReadOnlyValue) {
                    this.disableInteraction();
                } else {
                    this.enableInteraction();
                }
            }
        });
    }

    /**
     * Effect: Runs once when the view is ready to initialize the component.
     * Sets up initial star display and event handlers.
     */
    private setupInitializationEffect(): void {
        effect(() => {
            const containerElement = this.starContainer();
            if (containerElement && !this.isInitialized()) {
                this.isInitialized.set(true);
                this.rebuildStarDisplay();
            }
        });
    }

    // =========================================================================
    // Star Display Management
    // =========================================================================

    /**
     * Completely rebuilds the star display from scratch.
     * Called when totalStars changes or during initialization.
     */
    private rebuildStarDisplay(): void {
        this.createStarElements();
        this.renderStarStates(true);
        this.applyStarSizes();
        this.applyColorToAllStars(false);
        this.attachEventHandlers();
    }

    /**
     * Creates star DOM elements and adds them to the container.
     * Clears any existing stars first, removing event listeners to prevent memory leaks.
     */
    private createStarElements(): void {
        const container = this.starContainer();
        if (!container) {
            return;
        }

        const containerElement: HTMLDivElement = container.nativeElement;
        const starIndices = [...Array(this.starCount).keys()];

        // Remove existing event listeners before clearing to prevent memory leaks
        this.removeEventListeners();

        // Clear existing stars
        this.starElements.length = 0;
        containerElement.innerHTML = '';

        // Create new star elements
        starIndices.forEach((index) => {
            const starElement = document.createElement('span');
            const starNumber = index + 1;
            starElement.dataset.index = starNumber.toString();
            starElement.title = starElement.dataset.index;
            containerElement.appendChild(starElement);
            this.starElements.push(starElement);
        });
    }

    /**
     * Removes all event listeners from star elements and the container.
     * Must be called before clearing starElements to prevent memory leaks.
     */
    private removeEventListeners(): void {
        const container = this.starContainer();
        if (container) {
            container.nativeElement.removeEventListener('mouseleave', this.boundHandleMouseLeave);
        }

        this.starElements.forEach((star) => {
            star.removeEventListener('click', this.boundHandleStarClick);
            star.removeEventListener('mouseenter', this.boundHandleStarHover);
        });
    }

    /**
     * Renders the visual state of each star based on the current value.
     * Applies appropriate CSS classes for full, half, or empty stars.
     *
     * @param forceRender - If true, renders even in read-only mode
     */
    private renderStarStates(forceRender = false): void {
        const container = this.starContainer();
        if (!container) {
            return;
        }
        if (this.isReadOnly && !forceRender) {
            return;
        }

        if (this.starElements.length === 0) {
            this.createStarElements();
        }

        container.nativeElement.title = this.currentRating;

        const ratingValue = this.currentRating;
        const fullStarCount = Math.floor(ratingValue);
        let hasDecimalPart = ratingValue % 1 !== 0;

        this.starElements.forEach((star, index) => {
            // Reset classes and apply base styling
            star.className = '';
            this.applyBothColorsToStar(star);
            StarRatingComponent.addDefaultClass(star);

            if (index < fullStarCount) {
                // Full star (checked)
                StarRatingComponent.addCheckedStarClass(star);
            } else if (index === fullStarCount && hasDecimalPart) {
                // Half star (for decimal values like 3.5)
                StarRatingComponent.addHalfStarClass(star);
                hasDecimalPart = false;
            }
            // Remaining stars stay unchecked (default class only)
        });
    }

    // =========================================================================
    // Styling Methods
    // =========================================================================

    /**
     * Applies size CSS custom property to all stars.
     * Also calculates and applies half-width for partial stars.
     */
    private applyStarSizes(): void {
        if (!this.size()) {
            return;
        }

        if (this.starElements.length === 0) {
            this.createStarElements();
        }

        // Extract numeric value from size string (e.g., '24' from '24px')
        const sizeMatch = this.normalizedSize.match(/\d+/);
        if (!sizeMatch) {
            return;
        }

        const numericSize = Number(sizeMatch[0]);
        const decimalPart = this.currentRating - Math.floor(this.currentRating);
        const halfStarWidth = numericSize * (1 - decimalPart);

        this.starElements.forEach((star) => {
            star.style.setProperty(StarRatingComponent.VAR_SIZE, this.normalizedSize);

            // Apply special width for half stars to show partial fill
            if (star.classList.contains(StarRatingComponent.CLS_HALF_STAR)) {
                star.style.setProperty(StarRatingComponent.VAR_HALF_WIDTH, `${halfStarWidth}px`);
            }
        });
    }

    /**
     * Applies either checked or unchecked color to all stars.
     *
     * @param applyCheckedColor - If true, applies checked color; otherwise unchecked
     */
    private applyColorToAllStars(applyCheckedColor: boolean): void {
        if (this.starElements.length === 0) {
            this.createStarElements();
        }

        this.starElements.forEach((star) => {
            if (applyCheckedColor) {
                this.applyCheckedColorToStar(star);
            } else {
                this.applyUncheckedColorToStar(star);
            }
        });
    }

    /**
     * Applies both checked and unchecked colors to a star element.
     * Both are needed because the star uses both colors in its gradient.
     */
    private applyBothColorsToStar(starElement: HTMLSpanElement): void {
        this.applyCheckedColorToStar(starElement);
        this.applyUncheckedColorToStar(starElement);
    }

    /**
     * Sets the checked (filled) color CSS custom property on a star.
     */
    private applyCheckedColorToStar(starElement: HTMLSpanElement): void {
        starElement.style.setProperty(StarRatingComponent.VAR_CHECKED_COLOR, this.checkedColor() ?? '');
    }

    /**
     * Sets the unchecked (empty) color CSS custom property on a star.
     */
    private applyUncheckedColorToStar(starElement: HTMLSpanElement): void {
        starElement.style.setProperty(StarRatingComponent.VAR_UNCHECKED_COLOR, this.uncheckedColor() ?? '');
    }

    // =========================================================================
    // CSS Class Helpers
    // =========================================================================

    /** Adds the default star class to an element */
    private static addDefaultClass(star: Element): void {
        star.classList.add(StarRatingComponent.CLS_DEFAULT_STAR);
    }

    /** Adds the checked (filled) star class to an element */
    private static addCheckedStarClass(star: Element): void {
        star.classList.add(StarRatingComponent.CLS_CHECKED_STAR);
    }

    /** Adds the half-star class to an element */
    private static addHalfStarClass(star: Element): void {
        star.classList.add(StarRatingComponent.CLS_HALF_STAR);
    }

    // =========================================================================
    // Interaction Handling
    // =========================================================================

    /**
     * Enables user interaction by setting pointer cursor and attaching event handlers.
     */
    private enableInteraction(): void {
        const container = this.starContainer();
        if (!container) {
            return;
        }

        container.nativeElement.style.cursor = 'pointer';
        container.nativeElement.title = this.currentRating;

        this.starElements.forEach((star) => {
            star.style.cursor = 'pointer';
            star.title = star.dataset.index!;
        });
    }

    /**
     * Disables user interaction by setting default cursor and removing titles.
     */
    private disableInteraction(): void {
        const container = this.starContainer();
        if (!container) {
            return;
        }

        container.nativeElement.style.cursor = 'default';
        container.nativeElement.title = this.currentRating;

        this.starElements.forEach((star) => {
            star.style.cursor = 'default';
            star.title = '';
        });
    }

    /**
     * Attaches mouse event handlers for rating interaction.
     * Only attaches if not in read-only mode.
     * Uses stored bound handler references to enable proper removal later.
     */
    private attachEventHandlers(): void {
        const container = this.starContainer();
        if (!container || this.isReadOnly) {
            return;
        }

        container.nativeElement.addEventListener('mouseleave', this.boundHandleMouseLeave);
        container.nativeElement.style.cursor = 'pointer';
        container.nativeElement.title = this.currentRating;

        this.starElements.forEach((star) => {
            star.addEventListener('click', this.boundHandleStarClick);
            star.addEventListener('mouseenter', this.boundHandleStarHover);
            star.style.cursor = 'pointer';
            if (star.dataset.index) {
                star.title = star.dataset.index;
            }
        });
    }

    /**
     * Handles click on a star to set the rating.
     * Emits the rate output with old and new values.
     * Defensively validates the clicked element and its data-index attribute.
     */
    private handleStarClick(event: MouseEvent): void {
        if (this.isReadOnly) {
            return;
        }

        const target = event.target as Element | undefined;
        if (!target) {
            return;
        }

        // Find the star element (in case click was on a child element)
        const starElement = target.closest('[data-index]') as HTMLElement | undefined;
        if (!starElement) {
            return;
        }

        const indexStr = starElement.dataset.index;
        if (!indexStr) {
            return;
        }

        const newValue = Number.parseInt(indexStr, 10);
        if (!Number.isFinite(newValue) || newValue < 1 || newValue > this.starCount) {
            return;
        }

        const previousValue = this.currentRating;
        this.currentRating = newValue;
        this.rate.emit({ oldValue: previousValue, newValue: this.currentRating });
    }

    /**
     * Handles mouse hover over a star to show preview of potential rating.
     * Highlights all stars up to the hovered position.
     * Defensively validates the hovered element and its data-index attribute.
     */
    private handleStarHover(event: MouseEvent): void {
        if (this.isReadOnly) {
            return;
        }

        const target = event.target as HTMLElement | undefined;
        if (!target) {
            return;
        }

        const indexStr = target.dataset.index;
        if (!indexStr) {
            return;
        }

        const hoveredIndex = Number.parseInt(indexStr, 10);
        if (!Number.isFinite(hoveredIndex) || hoveredIndex < 1 || hoveredIndex > this.starElements.length) {
            return;
        }

        // Highlight all stars up to and including the hovered one
        for (let i = 0; i < hoveredIndex; i++) {
            this.starElements[i].className = '';
            StarRatingComponent.addDefaultClass(this.starElements[i]);
            StarRatingComponent.addCheckedStarClass(this.starElements[i]);
        }

        // Remove highlight from remaining stars
        for (let i = hoveredIndex; i < this.starElements.length; i++) {
            this.starElements[i].className = '';
            StarRatingComponent.addDefaultClass(this.starElements[i]);
        }
    }

    /**
     * Handles mouse leaving the star container.
     * Restores the display to show the actual rating value.
     */
    private handleMouseLeave(): void {
        this.renderStarStates();
    }
}
