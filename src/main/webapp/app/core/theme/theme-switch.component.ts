import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective, TumAetUiToggleSwitchComponent } from '@tumaet/ui-angular';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export type ThemeSwitchPopoverPlacement = 'bottom' | 'bottom-right';

/**
 * Displays a sun or a moon in the navbar, depending on the current theme.
 * Additionally, allows to switch themes by clicking it.
 * Shows a popover with additional options while the toggle is hovered or focused.
 */
@Component({
    selector: 'jhi-theme-switch',
    templateUrl: './theme-switch.component.html',
    styleUrls: ['theme-switch.component.scss'],
    imports: [FormsModule, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective, TumAetUiToggleSwitchComponent, FontAwesomeModule, TranslateDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ThemeSwitchComponent {
    private readonly themeService = inject(ThemeService);

    protected readonly faSync = faSync;

    /** Below the toggle: centred, or with its right edge on the toggle's where the toggle sits near the window edge. */
    popoverPlacement = input.required<ThemeSwitchPopoverPlacement>();

    isDarkTheme = computed(() => this.themeService.currentTheme() === Theme.DARK);
    isSyncedWithOS = computed(() => this.themeService.userPreference() === undefined);

    /**
     * Changes the theme to the currently not active theme.
     */
    toggleTheme() {
        this.themeService.applyThemePreference(this.isDarkTheme() ? Theme.LIGHT : Theme.DARK);
    }

    /**
     * Toggles the synced with OS state:
     * - if it's currently synced, we explicitly store the current theme as preference
     * - if it's currently not synced, we remove the preference to apply the system theme
     */
    toggleSynced() {
        this.themeService.applyThemePreference(this.isSyncedWithOS() ? this.themeService.currentTheme() : undefined);
    }
}
