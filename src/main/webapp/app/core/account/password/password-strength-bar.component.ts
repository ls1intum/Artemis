import { ChangeDetectionStrategy, Component, ElementRef, Renderer2, effect, inject, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * Visual password strength indicator component.
 * Displays a 5-segment bar that fills with color based on password strength.
 * Colors range from red (weak) through yellow (medium) to green (strong).
 */
@Component({
    selector: 'jhi-password-strength-bar',
    template: ` <div id="strength">
        <small jhiTranslate="global.messages.validate.newpassword.strength">Password strength:</small>
        <ul id="strengthBar">
            <li class="point"></li>
            <li class="point"></li>
            <li class="point"></li>
            <li class="point"></li>
            <li class="point"></li>
        </ul>
    </div>`,
    styleUrls: ['password-strength-bar.scss'],
    imports: [TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordStrengthBarComponent {
    private renderer = inject(Renderer2);
    private elementRef = inject(ElementRef);

    /** The password string to evaluate for strength */
    passwordToCheck = input<string>();

    /**
     * Color progression from weakest to strongest:
     * Red (#F00) -> Orange (#F90) -> Yellow (#FF0) -> Light Green (#9F0) -> Green (#0F0)
     */
    readonly strengthColors = ['#F00', '#F90', '#FF0', '#9F0', '#0F0'];

    /** Color used for unfilled bar segments */
    private readonly INACTIVE_SEGMENT_COLOR = '#DDD';

    constructor() {
        // Reactively update the strength bar whenever the password input changes
        effect(() => {
            const password = this.passwordToCheck();
            this.updateStrengthBar(password ?? '');
        });
    }

    /**
     * Updates the visual strength bar by coloring segments based on password strength.
     * Filled segments use the strength-appropriate color, unfilled segments are gray.
     * Empty password clears all segments to inactive state.
     */
    private updateStrengthBar(password: string): void {
        const hostElement = this.elementRef.nativeElement;
        const barSegments = hostElement.getElementsByTagName('li');

        if (!password) {
            for (let index = 0; index < barSegments.length; index++) {
                this.renderer.setStyle(barSegments[index], 'backgroundColor', this.INACTIVE_SEGMENT_COLOR);
            }
            return;
        }

        const strengthResult = this.getStrengthColorAndLevel(this.calculateStrengthScore(password));

        for (let index = 0; index < barSegments.length; index++) {
            const isFilled = index < strengthResult.filledSegments;
            const segmentColor = isFilled ? strengthResult.color : this.INACTIVE_SEGMENT_COLOR;
            this.renderer.setStyle(barSegments[index], 'backgroundColor', segmentColor);
        }
    }

    /**
     * Calculates a numeric strength score for the given password.
     * The score is based on length and character variety (lowercase, uppercase, numbers, symbols).
     * Penalties are applied for short passwords or limited character variety.
     *
     * @param password - The password to evaluate
     * @returns A numeric score where higher values indicate stronger passwords
     */
    calculateStrengthScore(password: string): number {
        // Regex matches common special characters and symbols
        const symbolPattern = /[$-/:-?{-~!"^_`[\]]/g;

        // Check which character types are present in the password
        const hasLowercase = /[a-z]+/.test(password);
        const hasUppercase = /[A-Z]+/.test(password);
        const hasNumbers = /[0-9]+/.test(password);
        const hasSymbols = symbolPattern.test(password);

        const characterTypes = [hasLowercase, hasUppercase, hasNumbers, hasSymbols];
        const varietyCount = characterTypes.filter((hasType) => hasType).length;

        // Base score: 2 points per character, bonus point for 10+ characters
        let strengthScore = 2 * password.length + (password.length >= 10 ? 1 : 0);

        // Bonus: 10 points per character type used
        strengthScore += varietyCount * 10;

        // Penalty: Cap score for short passwords (6 characters or less)
        if (password.length <= 6) {
            strengthScore = Math.min(strengthScore, 10);
        }

        // Penalty: Cap score based on limited character variety
        if (varietyCount === 1) {
            strengthScore = Math.min(strengthScore, 10);
        } else if (varietyCount === 2) {
            strengthScore = Math.min(strengthScore, 20);
        } else if (varietyCount === 3) {
            strengthScore = Math.min(strengthScore, 40);
        }

        return strengthScore;
    }

    /**
     * Maps a strength score to a color and number of filled bar segments.
     * Score thresholds: 0-10 (1 segment), 11-20 (2), 21-30 (3), 31-40 (4), 41+ (5)
     *
     * @param strengthScore - The calculated password strength score
     * @returns Object containing the number of filled segments and corresponding color
     */
    getStrengthColorAndLevel(strengthScore: number): { filledSegments: number; color: string } {
        let colorIndex: number;

        if (strengthScore <= 10) {
            colorIndex = 0;
        } else if (strengthScore <= 20) {
            colorIndex = 1;
        } else if (strengthScore <= 30) {
            colorIndex = 2;
        } else if (strengthScore <= 40) {
            colorIndex = 3;
        } else {
            colorIndex = 4;
        }

        return {
            filledSegments: colorIndex + 1,
            color: this.strengthColors[colorIndex],
        };
    }
}
