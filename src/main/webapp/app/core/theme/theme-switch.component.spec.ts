import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ThemeSwitchComponent } from 'app/core/theme/theme-switch.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective } from 'ng-mocks';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';

describe('ThemeSwitchComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ThemeSwitchComponent;
    let fixture: ComponentFixture<ThemeSwitchComponent>;
    let themeService: ThemeService;

    let openSpy: ReturnType<typeof vi.spyOn>;
    let closeSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ThemeSwitchComponent, MockDirective(NgbPopover)],
            declarations: [],
            providers: [
                {
                    provide: ThemeService,
                    useClass: MockThemeService,
                },
            ],
        }).compileComponents();

        themeService = TestBed.inject(ThemeService);

        fixture = TestBed.createComponent(ThemeSwitchComponent);
        component = fixture.componentInstance;

        openSpy = vi.spyOn(component.popover(), 'open');
        closeSpy = vi.spyOn(component.popover(), 'close');

        fixture.componentRef.setInput('popoverPlacement', ['bottom']);
    });

    afterEach(() => vi.restoreAllMocks());

    it('theme toggles correctly', async () => {
        vi.useFakeTimers();
        const applyThemePreferenceSpy = vi.spyOn(themeService, 'applyThemePreference');

        component.toggleTheme();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(Theme.DARK);

        expect(component.isDarkTheme()).toBe(true);
        await vi.advanceTimersByTimeAsync(250);
        expect(openSpy).toHaveBeenCalledOnce();
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
        expect(openSpy).toHaveBeenCalledOnce();
        component.closePopover();
        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('closes on mouse leave after 200ms', async () => {
        vi.useFakeTimers();
        component.openPopover();
        expect(openSpy).toHaveBeenCalledOnce();
        component.mouseLeave();
        expect(closeSpy).not.toHaveBeenCalled();
        await vi.advanceTimersByTimeAsync(250);
        expect(closeSpy).toHaveBeenCalledOnce();
        vi.useRealTimers();
    });
});
