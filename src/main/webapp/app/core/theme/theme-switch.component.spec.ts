import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeSwitchComponent } from 'app/core/theme/theme-switch.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ThemeSwitchComponent', () => {
    let component: ThemeSwitchComponent;
    let fixture: ComponentFixture<ThemeSwitchComponent>;
    let themeService: ThemeService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ThemeSwitchComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ThemeService,
                    useClass: MockThemeService,
                },
            ],
        }).compileComponents();

        themeService = TestBed.inject(ThemeService);

        fixture = TestBed.createComponent(ThemeSwitchComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('popoverPlacement', 'bottom');
        fixture.detectChanges();
    });

    afterEach(() => {
        component.closePopover();
        fixture.detectChanges();
        vi.restoreAllMocks();
    });

    it('theme toggles correctly', async () => {
        vi.useFakeTimers();
        const applyThemePreferenceSpy = vi.spyOn(themeService, 'applyThemePreference');

        component.toggleTheme();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(Theme.DARK);

        expect(component.isDarkTheme()).toBe(true);
        await vi.advanceTimersByTimeAsync(250);
        expect(component.isPopoverOpen()).toBe(true);
        vi.useRealTimers();
    });

    it('os sync toggles correctly', () => {
        const applyThemePreferenceSpy = vi.spyOn(themeService, 'applyThemePreference');

        component.toggleSynced();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(Theme.LIGHT);
        expect(component.isSyncedWithOS()).toBe(false);
        component.toggleSynced();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(undefined);
        expect(component.isSyncedWithOS()).toBe(true);
    });

    it('opens and closes the popover', () => {
        component.openPopover();
        fixture.detectChanges();
        expect(document.getElementById('theme-switch-popover-content')).not.toBeNull();
        component.closePopover();
        fixture.detectChanges();
        expect(document.getElementById('theme-switch-popover-content')).toBeNull();
    });

    it('closes on a click outside the popover', () => {
        component.openPopover();
        fixture.detectChanges();
        document.getElementById('theme-switch-popover-content')!.click();
        expect(component.isPopoverOpen()).toBe(true);
        document.body.click();
        expect(component.isPopoverOpen()).toBe(false);
    });

    it('moves the focus into the open popover on Tab and back to the toggle when leaving it', () => {
        const toggle: HTMLElement = fixture.nativeElement.querySelector('.toggle-popover-wrapper');
        const closedEvent = new KeyboardEvent('keydown', { key: 'Tab', cancelable: true });
        component.focusPopover(closedEvent);
        expect(closedEvent.defaultPrevented).toBe(false);

        component.openPopover();
        fixture.detectChanges();
        const openEvent = new KeyboardEvent('keydown', { key: 'Tab', cancelable: true });
        component.focusPopover(openEvent);
        expect(openEvent.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(document.querySelector('#theme-switch-popover-content input'));

        const backEvent = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, cancelable: true });
        component.leavePopover(backEvent, toggle);
        expect(backEvent.defaultPrevented).toBe(true);
        expect(component.isPopoverOpen()).toBe(false);
        expect(document.activeElement).toBe(toggle);
    });

    it('closes on mouse leave after 250ms', async () => {
        vi.useFakeTimers();
        component.openPopover();
        component.mouseLeave();
        expect(component.isPopoverOpen()).toBe(true);
        await vi.advanceTimersByTimeAsync(250);
        expect(component.isPopoverOpen()).toBe(false);
        vi.useRealTimers();
    });
});
