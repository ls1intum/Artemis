import { Component, OnInit, ViewChild } from '@angular/core';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { fromEvent } from 'rxjs';

/**
 * Displays a sun or a moon in the navbar, depending on the current theme.
 * Additionally, allows to switch themes by clicking it.
 * Shows a popover with hints while this feature is marked as experimental.
 */
@Component({
    selector: 'jhi-theme-switch',
    templateUrl: './theme-switch.component.html',
    styleUrls: ['theme-switch.component.scss'],
})
export class ThemeSwitchComponent implements OnInit {
    @ViewChild('popover') popover: NgbPopover;

    isDark = false;
    isByAutoDetection = false;
    animate = true;
    openPopupAfterNextChange = false;

    constructor(private themeService: ThemeService) {}

    ngOnInit() {
        // Listen to theme changes to change our own state accordingly
        this.themeService.getCurrentThemeObservable().subscribe((theme) => {
            this.isDark = theme === Theme.DARK;
            this.isByAutoDetection = false;
            this.animate = true;
            if (this.openPopupAfterNextChange) {
                this.openPopupAfterNextChange = false;
                setTimeout(() => this.openPopover(), 250);
            }
        });

        // Show popover if the theme was set based on OS settings
        setTimeout(() => {
            if (this.themeService.isAutoDetected) {
                this.isByAutoDetection = true;
                this.openPopover();
            }
        }, 1200);

        // Workaround as we can't dynamically change the "autoClose" property on popovers
        fromEvent(window, 'click').subscribe(() => {
            if (!this.isByAutoDetection && this.popover.isOpen()) {
                this.popover.close();
            }
        });
    }

    /**
     * Open the popover if this is not a cypress test
     * @private
     */
    private openPopover() {
        if (!window['Cypress']) {
            this.popover?.open();
        }
    }

    /**
     * Changes the theme to the currently not active theme.
     */
    toggle() {
        this.animate = false;
        this.openPopupAfterNextChange = true;
        setTimeout(() => this.themeService.applyTheme(this.isDark ? Theme.LIGHT : Theme.DARK));
    }

    /**
     * Enables dark mode, but fades out the popover before that (made for the "Apply now" button)
     */
    enableNow() {
        this.popover.close();
        this.openPopupAfterNextChange = true;
        // Wait until the popover has closed to prevent weird visual jumping issues
        setTimeout(() => this.themeService.applyTheme(Theme.DARK), 200);
    }

    /**
     * Called if the "No, thanks" or "Got it" link in the popover is clicked.
     * We store the current theme in that case as the user showed that they don't want to go to dark mode, or,
     * if the dark mode was enabled automatically, understood that they can disable it any time.
     */
    manualClose() {
        this.popover.close();
        // Apply the inherited mode explicitly to store the preference in local storage in case of light mode.
        // Doesn't hurt in dark mode, either
        setTimeout(() => {
            this.themeService.applyTheme(this.isDark ? Theme.DARK : Theme.LIGHT);
            this.isByAutoDetection = false;
        }, 200);
    }
}
