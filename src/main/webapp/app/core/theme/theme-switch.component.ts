import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { CdkConnectedOverlay, CdkOverlayOrigin, ConnectedPosition } from '@angular/cdk/overlay';
import { TumAetUiToggleSwitchComponent } from '@tumaet/ui-angular';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { fromEvent } from 'rxjs';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export type ThemeSwitchPopoverPlacement = 'bottom' | 'bottom-right';

/** Gap between the toggle and its popover. */
const POPOVER_OFFSET_PX = 8;

/**
 * Displays a sun or a moon in the navbar, depending on the current theme.
 * Additionally, allows to switch themes by clicking it.
 * Shows a popover with additional options. The popover opens on hover and stays open while the pointer is over it,
 * which the TUM AET UI popover (opened by click, behind a backdrop) does not offer, so it is a plain CDK overlay.
 */
@Component({
    selector: 'jhi-theme-switch',
    templateUrl: './theme-switch.component.html',
    styleUrls: ['theme-switch.component.scss'],
    imports: [CdkConnectedOverlay, CdkOverlayOrigin, FormsModule, TumAetUiToggleSwitchComponent, FontAwesomeModule, TranslateDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ThemeSwitchComponent implements OnInit {
    private readonly themeService = inject(ThemeService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faSync = faSync;

    popoverPlacement = input.required<ThemeSwitchPopoverPlacement>();
    readonly isPopoverOpen = signal(false);
    /** Below the toggle: centred, or with its right edge on the toggle's where the toggle sits near the window edge. */
    protected readonly positions = computed<ConnectedPosition[]>(() => {
        const horizontal = this.popoverPlacement() === 'bottom-right' ? 'end' : 'center';
        return [{ originX: horizontal, originY: 'bottom', overlayX: horizontal, overlayY: 'top', offsetY: POPOVER_OFFSET_PX }];
    });

    isDarkTheme = computed(() => this.themeService.currentTheme() === Theme.DARK);
    isSyncedWithOS = computed(() => this.themeService.userPreference() === undefined);

    closeTimeout: ReturnType<typeof setTimeout> | undefined;

    ngOnInit() {
        // A click on the toggle keeps the popover (it reopens after the theme changed); any other click outside closes it.
        fromEvent(window, 'click')
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((e) => {
                const popoverContentElement = document.getElementById('theme-switch-popover-content');
                if (this.isPopoverOpen() && !popoverContentElement?.contains(e.target as Node)) {
                    this.closePopover();
                }
            });
    }

    openPopover() {
        this.isPopoverOpen.set(true);
        clearTimeout(this.closeTimeout);
    }

    closePopover() {
        clearTimeout(this.closeTimeout);
        this.isPopoverOpen.set(false);
    }

    mouseLeave() {
        clearTimeout(this.closeTimeout);
        this.closeTimeout = setTimeout(() => this.closePopover(), 250);
    }

    /**
     * The popover is rendered in an overlay at the end of the document, so Tab would not reach it from the toggle.
     * Moves the focus into the open popover instead.
     */
    focusPopover(event: Event) {
        const control = document.querySelector<HTMLElement>('#theme-switch-popover-content input');
        if (this.isPopoverOpen() && control) {
            event.preventDefault();
            control.focus();
        }
    }

    /**
     * Closes the popover and hands the focus back to the toggle. Tab then continues from the toggle to the next
     * control of the navbar; Shift+Tab stays on the toggle.
     */
    leavePopover(event: Event, toggle: HTMLElement) {
        this.closePopover();
        toggle.focus();
        if ((event as KeyboardEvent).shiftKey) {
            event.preventDefault();
        }
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
