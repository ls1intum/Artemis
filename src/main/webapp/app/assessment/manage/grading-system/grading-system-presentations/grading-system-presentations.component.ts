import { Component, effect, input, output } from '@angular/core';
import { ModePickerComponent, ModePickerOption } from 'app/exercise/mode-picker/mode-picker.component';
import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

import { FormsModule } from '@angular/forms';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

/**
 * Enum representing the available presentation types in a course grading system.
 */
export enum PresentationType {
    /** No presentations required for the course */
    NONE = 'none',
    /** Basic presentations that add bonus points to the final grade */
    BASIC = 'basic',
    /** Graded presentations that contribute a weighted percentage to the final grade */
    GRADED = 'graded',
}

/**
 * Configuration object for presentation settings in a grading system.
 * This is used to track the current state of presentation configuration
 * and synchronize it with the grading scale.
 */
export interface PresentationsConfig {
    /** The currently selected presentation type */
    presentationType: PresentationType;
    /** Number of basic presentations required (only for BASIC type) */
    presentationScore?: number;
    /** Combined weight of all graded presentations as percentage (only for GRADED type) */
    presentationsWeight?: number;
    /** Number of graded presentations required (only for GRADED type) */
    presentationsNumber?: number;
}

/**
 * Component for configuring presentation requirements in a course grading system.
 *
 * This component allows instructors to choose between three presentation modes:
 * - None: No presentations required
 * - Basic: Students must give a set number of presentations for bonus points
 * - Graded: Presentations contribute a weighted percentage to the final grade
 *
 * The component uses Angular signals for reactive input handling and automatically
 * synchronizes the presentation configuration with the parent grading scale.
 *
 * @example
 * ```html
 * <jhi-grading-system-presentations
 *   [gradingScale]="gradingScale"
 *   [presentationsConfig]="presentationsConfig">
 * </jhi-grading-system-presentations>
 * ```
 */
@Component({
    selector: 'jhi-grading-system-presentations',
    templateUrl: './grading-system-presentations.component.html',
    styleUrls: ['./grading-system-presentations.component.scss'],
    imports: [TranslateDirective, FormsModule, ModePickerComponent, HelpIconComponent],
})
export class GradingSystemPresentationsComponent {
    // =========================================================================
    // Template Constants (exposed for use in template)
    // =========================================================================

    /** Constant for NONE presentation type, used in template comparisons */
    readonly NONE = PresentationType.NONE;
    /** Constant for BASIC presentation type, used in template comparisons */
    readonly BASIC = PresentationType.BASIC;
    /** Constant for GRADED presentation type, used in template comparisons */
    readonly GRADED = PresentationType.GRADED;

    // =========================================================================
    // Mode Picker Configuration
    // =========================================================================

    /**
     * Options for the presentation type mode picker.
     * Defines the available choices and their display properties.
     */
    readonly modePickerOptions: ModePickerOption<PresentationType>[] = [
        {
            value: PresentationType.NONE,
            labelKey: 'artemisApp.gradingSystem.presentationType.none',
            btnClass: 'btn-secondary',
        },
        {
            value: PresentationType.BASIC,
            labelKey: 'artemisApp.gradingSystem.presentationType.basic',
            btnClass: 'btn-secondary',
        },
        {
            value: PresentationType.GRADED,
            labelKey: 'artemisApp.gradingSystem.presentationType.graded',
            btnClass: 'btn-secondary',
        },
    ];

    // =========================================================================
    // Signal Inputs
    // =========================================================================

    /**
     * The grading scale being configured.
     * This is the parent entity that stores the actual presentation settings.
     * Changes made in this component are reflected back to this object.
     */
    readonly gradingScale = input.required<GradingScale>();

    /**
     * Configuration object for tracking presentation settings.
     * This represents the current state of presentation configuration.
     */
    readonly presentationsConfig = input.required<PresentationsConfig>();

    /**
     * Emits whenever the presentations configuration changes.
     * The parent component should subscribe to this output and update its local
     * presentationsConfig copy to maintain synchronization. This ensures explicit
     * data flow from child to parent rather than relying on implicit object mutation.
     */
    readonly presentationsConfigChange = output<PresentationsConfig>();

    // =========================================================================
    // Constructor
    // =========================================================================

    constructor() {
        this.setupInputChangeEffect();
    }

    /**
     * Effect that initializes the presentation configuration when inputs change.
     * This replaces the ngOnChanges lifecycle hook with a reactive signal-based approach.
     *
     * When either the grading scale or presentations config changes, this effect
     * determines the current presentation type based on the grading scale settings
     * and updates the config accordingly.
     */
    private setupInputChangeEffect(): void {
        effect(() => {
            const scale = this.gradingScale();
            const config = this.presentationsConfig();

            // Only initialize if both inputs are available
            if (scale && config) {
                this.initializePresentationConfig();
            }
        });
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    /**
     * Initializes the presentation configuration based on the current grading scale settings.
     *
     * This method determines which presentation type is currently active by examining
     * the grading scale properties and updates the config to match. It also syncs
     * the presentation number and weight values.
     */
    private initializePresentationConfig(): void {
        const config = this.presentationsConfig();
        const scale = this.gradingScale();

        // Determine presentation type based on grading scale settings
        // Order matters: check graded first since it's more specific
        if (this.isGradedPresentation()) {
            config.presentationType = PresentationType.GRADED;
        } else if (this.isBasicPresentation()) {
            config.presentationType = PresentationType.BASIC;
        } else {
            config.presentationType = PresentationType.NONE;
        }

        // Sync graded presentation settings from scale to config
        config.presentationsNumber = scale.presentationsNumber;
        config.presentationsWeight = scale.presentationsWeight;

        this.emitConfigChange();
    }

    // =========================================================================
    // Config Change Emission
    // =========================================================================

    /**
     * Emits the current presentations config to notify the parent of changes.
     * Since the child mutates the parent's object directly, this emit serves
     * as a notification that changes occurred rather than providing a new object.
     */
    private emitConfigChange(): void {
        this.presentationsConfigChange.emit(this.presentationsConfig());
    }

    // =========================================================================
    // Presentation Type Detection
    // =========================================================================

    /**
     * Checks if the grading scale has basic presentations enabled.
     * Basic presentations are enabled when the course has a positive presentation score.
     *
     * @returns true if basic presentations are configured
     */
    isBasicPresentation(): boolean {
        const coursePresentationScore = this.gradingScale().course?.presentationScore ?? 0;
        return coursePresentationScore > 0;
    }

    /**
     * Checks if the grading scale has graded presentations enabled.
     * Graded presentations require both a non-negative weight and a positive number.
     *
     * @returns true if graded presentations are configured
     */
    isGradedPresentation(): boolean {
        const scale = this.gradingScale();
        const hasValidWeight = (scale.presentationsWeight ?? -1) >= 0;
        const hasValidNumber = (scale.presentationsNumber ?? -1) > 0;
        return hasValidWeight && hasValidNumber;
    }

    // =========================================================================
    // Event Handlers
    // =========================================================================

    /**
     * Handles changes to the presentation type selection.
     *
     * When the user selects a different presentation type, this method:
     * 1. Updates the config with the new type
     * 2. Clears settings from the previous type
     * 3. Applies default values for the new type
     * 4. Emits the updated config to the parent
     *
     * @param newPresentationType - The newly selected presentation type
     */
    onPresentationTypeChange(newPresentationType: PresentationType): void {
        this.presentationsConfig().presentationType = newPresentationType;

        switch (newPresentationType) {
            case PresentationType.NONE:
                // Clear all presentation-related settings
                this.updatePresentationScore(undefined, false);
                this.updatePresentationsNumber(undefined, false);
                this.updatePresentationsWeight(undefined, false);
                break;

            case PresentationType.BASIC:
                // Set default basic presentation score, clear graded settings
                this.updatePresentationScore(2, false); // Default: 2 presentations required
                this.updatePresentationsNumber(undefined, false);
                this.updatePresentationsWeight(undefined, false);
                break;

            case PresentationType.GRADED:
                // Set default graded settings, clear basic setting
                this.updatePresentationScore(undefined, false);
                this.updatePresentationsNumber(2, false); // Default: 2 presentations required
                this.updatePresentationsWeight(20, false); // Default: 20% of final grade
                break;
        }

        this.emitConfigChange();
    }

    // =========================================================================
    // Update Methods
    // =========================================================================

    /**
     * Updates the basic presentation score (number of required presentations).
     *
     * This updates both the config and the course entity on the grading scale.
     * Invalid values (null, undefined, zero, negative) are normalized to undefined.
     *
     * @param presentationScore - The new presentation score, or undefined to clear
     * @param emitChange - Whether to emit the config change (default: true)
     */
    updatePresentationScore(presentationScore?: number, emitChange: boolean = true): void {
        const scale = this.gradingScale();
        const config = this.presentationsConfig();

        if (!scale.course) {
            return;
        }

        // Normalize invalid values to undefined
        const normalizedScore = (presentationScore ?? -1) > 0 ? presentationScore : undefined;

        // Update both config and the underlying course entity
        config.presentationScore = normalizedScore;
        scale.course.presentationScore = normalizedScore;

        if (emitChange) {
            this.emitConfigChange();
        }
    }

    /**
     * Updates the number of graded presentations required.
     *
     * This updates both the config and the grading scale entity.
     * Invalid values (null, undefined, zero, negative) are normalized to undefined.
     *
     * @param presentationsNumber - The new number of presentations, or undefined to clear
     * @param emitChange - Whether to emit the config change (default: true)
     */
    updatePresentationsNumber(presentationsNumber?: number, emitChange: boolean = true): void {
        const config = this.presentationsConfig();
        const scale = this.gradingScale();

        // Normalize invalid values to undefined
        const normalizedNumber = (presentationsNumber ?? -1) > 0 ? presentationsNumber : undefined;

        // Update both config and the underlying grading scale entity
        config.presentationsNumber = normalizedNumber;
        scale.presentationsNumber = normalizedNumber;

        if (emitChange) {
            this.emitConfigChange();
        }
    }

    /**
     * Updates the combined weight of graded presentations.
     *
     * This updates both the config and the grading scale entity.
     * Invalid values (null, undefined, negative) are normalized to undefined.
     * Note: Zero is a valid weight (presentations worth 0%).
     *
     * @param presentationsWeight - The new weight percentage, or undefined to clear
     * @param emitChange - Whether to emit the config change (default: true)
     */
    updatePresentationsWeight(presentationsWeight?: number, emitChange: boolean = true): void {
        const config = this.presentationsConfig();
        const scale = this.gradingScale();

        // Normalize invalid values to undefined (note: 0 is valid)
        const normalizedWeight = (presentationsWeight ?? -1) >= 0 ? presentationsWeight : undefined;

        // Update both config and the underlying grading scale entity
        config.presentationsWeight = normalizedWeight;
        scale.presentationsWeight = normalizedWeight;

        if (emitChange) {
            this.emitConfigChange();
        }
    }
}
