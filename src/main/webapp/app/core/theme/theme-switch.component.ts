import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, viewChild } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule, NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { PlacementArray } from '@ng-bootstrap/ng-bootstrap/util/positioning';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { fromEvent } from 'rxjs';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

/**
 * Displays a sun or a moon in the navbar, depending on the current theme.
 * Additionally, allows to switch themes by clicking it.
 * Shows a popover with additional options.
 */
@Component({
    selector: 'jhi-theme-switch',
    templateUrl: './theme-switch.component.html',
    styleUrls: ['theme-switch.component.scss'],
    imports: [TranslateModule, NgbModule, FontAwesomeModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
})
export class ThemeSwitchComponent implements OnInit {
    protected readonly faSync = faSync;

    private readonly themeService = inject(ThemeService);

    popoverPlacement = input.required<PlacementArray>();
    popover = viewChild.required<NgbPopover>('popover');

    isDarkTheme = computed(() => this.themeService.currentTheme() === Theme.DARK);
    isSyncedWithOS = computed(() => this.themeService.userPreference() === undefined);

    closeTimeout: any;

    ngOnInit() {
        // Workaround as we can't dynamically change the "autoClose" property on popovers
        fromEvent(window, 'click').subscribe((e) => {
            const popoverContentElement = document.getElementById('theme-switch-popover-content');
            if (this.popover().isOpen() && !popoverContentElement?.contains(e.target as Node)) {
                this.closePopover();
            }
        });
    }

    openPopover() {
        this.popover().open();
        clearTimeout(this.closeTimeout);
    }

    closePopover() {
        clearTimeout(this.closeTimeout);
        this.popover().close();
    }

    mouseLeave() {
        clearTimeout(this.closeTimeout);
        this.closeTimeout = setTimeout(() => this.closePopover(), 250);
    }

    /**
     * Changes the theme to the currently not active theme.
     */
    toggleTheme() {
        this.themeService.applyThemePreference(this.isDarkTheme() ? Theme.LIGHT : Theme.DARK);
        setTimeout(() => this.openPopover(), 250);
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
