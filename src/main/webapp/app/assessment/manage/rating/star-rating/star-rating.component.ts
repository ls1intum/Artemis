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
     * Use the `currentValue` getter/setter for programmatic access to the internal value.
     */
    // eslint-disable-next-line @angular-eslint/no-input-rename
    readonly valueInput = input<number>(0, { alias: 'value' });

    /**
     * The size of each star. Can be specified with or without 'px' suffix.
     * Use the `normalizedSize` getter for the value with guaranteed 'px' suffix.
     * @example '24px', '32', '48px'
     */
    // eslint-disable-next-line @angular-eslint/no-input-rename
    readonly sizeInput = input<string>('24px', { alias: 'size' });

    /**
     * When true, the rating cannot be changed by user interaction.
     * Stars will not respond to hover or click events.
     */
    // eslint-disable-next-line @angular-eslint/no-input-rename
    readonly readOnlyInput = input<boolean>(false, { alias: 'readOnly' });

    /**
     * The total number of stars to display.
     * Use the `normalizedTotalStars` getter for the validated value.
     * Invalid values (<=0) default to 5.
     */
    // eslint-disable-next-line @angular-eslint/no-input-rename
    readonly totalStarsInput = input<number>(5, { alias: 'totalStars' });

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

    /** Gets the current rating value */
    get value(): number {
        return this.currentValue();
    }

    /**
     * Sets the current rating value.
     * Falsy values (null, undefined, 0) are normalized to 0.
     */
    set value(newValue: number) {
        const normalizedValue = newValue || 0;
        this.currentValue.set(normalizedValue);
    }

    /**
     * Gets the star size with 'px' suffix guaranteed.
     * @returns Size string with 'px' suffix (e.g., '24px')
     */
    get size(): string {
        const sizeValue = this.sizeInput() || '24px';
        return sizeValue.includes('px') ? sizeValue : `${sizeValue}px`;
    }

    /** Gets whether the component is in read-only mode */
    get readOnly(): boolean {
        return this.readOnlyInput();
    }

    /**
     * Gets the total number of stars to display.
     * Invalid values are normalized to 5.
     */
    get totalStars(): number {
        const inputValue = this.totalStarsInput();
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
            // Access totalStarsInput signal to subscribe to changes
            // The value is used implicitly via this.totalStars getter in rebuildStarDisplay
            void this.totalStarsInput();
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
            const externalValue = this.valueInput();
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
            // Access sizeInput signal to subscribe to changes
            // The value is used implicitly via this.size getter in applyStarSizes
            void this.sizeInput();
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
            const isReadOnly = this.readOnlyInput();
            if (this.isInitialized()) {
                if (isReadOnly) {
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
     * Clears any existing stars first.
     */
    private createStarElements(): void {
        const container = this.starContainer();
        if (!container) {
            return;
        }

        const containerElement: HTMLDivElement = container.nativeElement;
        const starIndices = [...Array(this.totalStars).keys()];

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
        if (this.readOnly && !forceRender) {
            return;
        }

        if (this.starElements.length === 0) {
            this.createStarElements();
        }

        container.nativeElement.title = this.value;

        const ratingValue = this.value;
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
        if (!this.sizeInput()) {
            return;
        }

        if (this.starElements.length === 0) {
            this.createStarElements();
        }

        // Extract numeric value from size string (e.g., '24' from '24px')
        const sizeMatch = this.size.match(/\d+/);
        if (!sizeMatch) {
            return;
        }

        const numericSize = Number(sizeMatch[0]);
        const decimalPart = this.value - Math.floor(this.value);
        const halfStarWidth = numericSize * (1 - decimalPart);

        this.starElements.forEach((star) => {
            star.style.setProperty(StarRatingComponent.VAR_SIZE, this.size);

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
        container.nativeElement.title = this.value;

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
        container.nativeElement.title = this.value;

        this.starElements.forEach((star) => {
            star.style.cursor = 'default';
            star.title = '';
        });
    }

    /**
     * Attaches mouse event handlers for rating interaction.
     * Only attaches if not in read-only mode.
     */
    private attachEventHandlers(): void {
        const container = this.starContainer();
        if (!container || this.readOnly) {
            return;
        }

        container.nativeElement.addEventListener('mouseleave', this.handleMouseLeave.bind(this));
        container.nativeElement.style.cursor = 'pointer';
        container.nativeElement.title = this.value;

        this.starElements.forEach((star) => {
            star.addEventListener('click', this.handleStarClick.bind(this));
            star.addEventListener('mouseenter', this.handleStarHover.bind(this));
            star.style.cursor = 'pointer';
            star.title = star.dataset.index!;
        });
    }

    /**
     * Handles click on a star to set the rating.
     * Emits the rate output with old and new values.
     */
    private handleStarClick(event: MouseEvent): void {
        if (this.readOnly) {
            return;
        }

        const clickedStar = event.target as HTMLElement;
        const previousValue = this.value;
        const newValue = parseInt(clickedStar.dataset.index!, 10);

        this.value = newValue;
        this.rate.emit({ oldValue: previousValue, newValue: this.value });
    }

    /**
     * Handles mouse hover over a star to show preview of potential rating.
     * Highlights all stars up to the hovered position.
     */
    private handleStarHover(event: MouseEvent): void {
        if (this.readOnly) {
            return;
        }

        const hoveredStar = event.target as HTMLElement;
        const hoveredIndex = parseInt(hoveredStar.dataset.index!, 10);

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
