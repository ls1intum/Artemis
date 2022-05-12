import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { fromEvent } from 'rxjs';
import { LocalStorageService } from 'ngx-webstorage';
import { faSync } from '@fortawesome/free-solid-svg-icons';

export const THEME_SWITCH_HAS_SHOWN_INITIAL_KEY = 'artemisApp.theme.hasShownInitialHint';

/**
 * Displays a sun or a moon in the navbar, depending on the current theme.
 * Additionally, allows to switch themes by clicking it.
 * Shows a popover with additional options.
 */
@Component({
    selector: 'jhi-theme-switch',
    templateUrl: './theme-switch.component.html',
    styleUrls: ['theme-switch.component.scss'],
})
export class ThemeSwitchComponent implements OnInit {
    @ViewChild('popover') popover: NgbPopover;

    @Input() popoverPlacement: string;

    isDark = false;
    isSynced = false;
    animate = true;
    openPopupAfterNextChange = false;
    closeTimeout: any;

    showInitialHints = false;

    // Icons
    faSync = faSync;

    constructor(private themeService: ThemeService, private localStorageService: LocalStorageService) {}

    ngOnInit() {
        // Listen to theme changes to change our own state accordingly
        this.themeService.getCurrentThemeObservable().subscribe((theme) => {
            this.isDark = theme === Theme.DARK;
            this.animate = true;
            if (this.openPopupAfterNextChange) {
                this.openPopupAfterNextChange = false;
                setTimeout(() => this.openPopover(), 250);
            }
        });

        // Listen to preference changes
        this.themeService.getPreferenceObservable().subscribe((themeOrUndefined) => {
            this.isSynced = !themeOrUndefined;
        });

        // Show popover if the theme was set based on OS settings
        setTimeout(() => {
            if (!this.localStorageService.retrieve(THEME_SWITCH_HAS_SHOWN_INITIAL_KEY)) {
                this.showInitialHints = true;
                this.openPopover();
                this.localStorageService.store(THEME_SWITCH_HAS_SHOWN_INITIAL_KEY, true);
            }
        }, 1200);

        // Workaround as we can't dynamically change the "autoClose" property on popovers
        fromEvent(window, 'click').subscribe((e) => {
            const popoverContentElement = document.getElementById('theme-switch-popover-content');
            if (!this.showInitialHints && this.popover.isOpen() && !popoverContentElement?.contains(e.target as Node)) {
                this.closePopover();
            }
        });
    }

    /**
     * Open the popover if this is not a cypress test
     * @private
     */
    openPopover() {
        if (!window['Cypress']) {
            this.popover?.open();
        }
        clearTimeout(this.closeTimeout);
    }

    closePopover() {
        clearTimeout(this.closeTimeout);
        this.popover?.close();
        setTimeout(() => (this.showInitialHints = false), 200);
    }

    mouseLeave() {
        clearTimeout(this.closeTimeout);
        this.closeTimeout = setTimeout(() => this.closePopover(), 250);
    }

    /**
     * Changes the theme to the currently not active theme.
     */
    toggleTheme() {
        this.animate = false;
        this.openPopupAfterNextChange = true;
        setTimeout(() => this.themeService.applyThemeExplicitly(this.isDark ? Theme.LIGHT : Theme.DARK));
    }

    /**
     * Toggles the synced with OS state:
     * - if it's currently synced, we explicitly store the current theme as preference
     * - if it's currently not synced, we remove the preference to apply the system theme
     */
    toggleSynced() {
        this.themeService.applyThemeExplicitly(this.isSynced ? this.themeService.getCurrentTheme() : undefined);
    }

    /**
     * Enables dark mode, but fades out the popover before that (made for the "Apply now" button)
     */
    enableNow() {
        this.closePopover();
        this.openPopupAfterNextChange = true;
        // Wait until the popover has closed to prevent weird visual jumping issues
        setTimeout(() => this.themeService.applyThemeExplicitly(Theme.DARK), 200);
    }
}
