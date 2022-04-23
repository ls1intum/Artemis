import { Component, OnInit, ViewChild } from '@angular/core';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { fromEvent } from 'rxjs';

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
        this.themeService.getCurrentThemeObservable().subscribe((theme) => {
            this.isDark = theme === Theme.DARK;
            this.isByAutoDetection = false;
            this.animate = true;
            if (this.openPopupAfterNextChange) {
                this.openPopupAfterNextChange = false;
                setTimeout(() => this.popover.open(), 250);
            }
        });

        // Show popover if the theme was set based on OS settings
        setTimeout(() => {
            if (this.themeService.isAutoDetected) {
                this.isByAutoDetection = true;
                this.popover.open();
            }
        }, 1200);

        // Workaround as we can't dynamically change the "autoClose" property on popovers
        fromEvent(window, 'click').subscribe(() => {
            if (!this.isByAutoDetection && this.popover.isOpen()) {
                this.popover.close();
            }
        });
    }

    toggle() {
        this.animate = false;
        this.openPopupAfterNextChange = true;
        setTimeout(() => this.themeService.applyTheme(this.isDark ? Theme.LIGHT : Theme.DARK));
    }

    enableNow() {
        this.popover.close();
        this.openPopupAfterNextChange = true;
        // Wait until the popover has closed to prevent weird visual jumping issues
        setTimeout(() => this.themeService.applyTheme(Theme.DARK), 200);
    }

    manualClose() {
        this.popover.close();
        // The user does not want to switch modes. Reapply the inherited mode explicitly to store the preference in local storage.
        setTimeout(() => {
            this.themeService.applyTheme(this.isDark ? Theme.DARK : Theme.LIGHT);
            this.isByAutoDetection = false;
        }, 200);
    }
}
