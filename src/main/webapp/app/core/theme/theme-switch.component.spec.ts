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
        fixture.destroy();
        vi.restoreAllMocks();
    });

    const toggle = (): HTMLElement => fixture.nativeElement.querySelector('.toggle-popover-wrapper');
    const popoverContent = () => document.getElementById('theme-switch-popover-content');

    it('theme toggles correctly', () => {
        const applyThemePreferenceSpy = vi.spyOn(themeService, 'applyThemePreference');

        toggle().click();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(Theme.DARK);
        expect(component.isDarkTheme()).toBe(true);
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

    it('shows the popover while the toggle is hovered and keeps it open on a click on the toggle', () => {
        expect(popoverContent()).toBeNull();

        toggle().dispatchEvent(new Event('pointerenter'));
        fixture.detectChanges();
        expect(popoverContent()).not.toBeNull();
        expect(toggle().getAttribute('aria-expanded')).toBe('true');

        toggle().click();
        fixture.detectChanges();
        expect(toggle().getAttribute('aria-expanded')).toBe('true');
    });

    it('switches the sync with the operating system from the popover', async () => {
        const applyThemePreferenceSpy = vi.spyOn(themeService, 'applyThemePreference');
        toggle().dispatchEvent(new Event('focus'));
        fixture.detectChanges();
        await fixture.whenStable();

        popoverContent()!.querySelector<HTMLInputElement>('input')!.click();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(Theme.LIGHT);
    });
});
