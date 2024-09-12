import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { PlacementArray } from '@ng-bootstrap/ng-bootstrap/util/positioning';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { Subscription, delay, filter, fromEvent, tap, timer } from 'rxjs';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { toObservable } from '@angular/core/rxjs-interop';

/**
 * Displays a sun or a moon in the navbar, depending on the current theme.
 * Additionally, allows to switch themes by clicking it.
 * Shows a popover with additional options.
 */
@Component({
    selector: 'jhi-theme-switch',
    templateUrl: './theme-switch.component.html',
    styleUrls: ['theme-switch.component.scss'],
    imports: [TranslateModule, CommonModule, ArtemisSharedModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
})
export class ThemeSwitchComponent implements OnInit, OnDestroy {
    protected readonly faSync = faSync;

    private readonly themeService = inject(ThemeService);

    popoverPlacement = input.required<PlacementArray>();
    popover = viewChild.required<NgbPopover>('popover');

    isDarkTheme = computed(() => this.themeService.currentTheme() === Theme.DARK);
    isSyncedWithOS = computed(() => this.themeService.preference() === undefined);

    animate = signal(true);
    openPopupAfterNextChange = signal(false);

    private closeTimerSubscription: Subscription | undefined;
    private reopenPopupSubscription = toObservable(this.themeService.currentTheme)
        .pipe(
            tap(() => this.animate.set(true)),
            filter(() => this.openPopupAfterNextChange()),
            tap(() => this.openPopupAfterNextChange.set(false)),
            delay(250),
        )
        .subscribe(() => this.openPopover());

    ngOnInit() {
        // Workaround as we can't dynamically change the "autoClose" property on popovers
        fromEvent(window, 'click').subscribe((e) => {
            const popoverContentElement = document.getElementById('theme-switch-popover-content');
            if (this.popover().isOpen() && !popoverContentElement?.contains(e.target as Node)) {
                this.closePopover();
            }
        });
    }

    ngOnDestroy() {
        this.reopenPopupSubscription.unsubscribe();
        this.closeTimerSubscription?.unsubscribe();
    }

    openPopover() {
        this.popover().open();
        this.closeTimerSubscription?.unsubscribe();
    }

    closePopover() {
        this.popover().close();
        this.closeTimerSubscription?.unsubscribe();
    }

    mouseLeave() {
        this.closeTimerSubscription?.unsubscribe();
        this.closeTimerSubscription = timer(250).subscribe(() => this.closePopover());
    }

    /**
     * Changes the theme to the currently not active theme.
     */
    toggleTheme() {
        this.animate.set(false);
        this.openPopupAfterNextChange.set(true);
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
