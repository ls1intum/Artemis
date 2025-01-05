import { ArtemisTestModule } from '../../test.module';
import { ThemeSwitchComponent } from 'app/core/theme/theme-switch.component';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective } from 'ng-mocks';
import { MockThemeService } from '../../helpers/mocks/service/mock-theme.service';

describe('ThemeSwitchComponent', () => {
    let component: ThemeSwitchComponent;
    let fixture: ComponentFixture<ThemeSwitchComponent>;
    let themeService: ThemeService;

    let openSpy: jest.SpyInstance;
    let closeSpy: jest.SpyInstance;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ThemeSwitchComponent, MockDirective(NgbPopover)],
            declarations: [],
            providers: [
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                {
                    provide: ThemeService,
                    useClass: MockThemeService,
                },
            ],
        }).compileComponents();

        themeService = TestBed.inject(ThemeService);

        fixture = TestBed.createComponent(ThemeSwitchComponent);
        component = fixture.componentInstance;

        openSpy = jest.spyOn(component.popover(), 'open');
        closeSpy = jest.spyOn(component.popover(), 'close');

        fixture.componentRef.setInput('popoverPlacement', ['bottom']);
    });

    afterEach(() => jest.restoreAllMocks());

    it('theme toggles correctly', fakeAsync(() => {
        const applyThemePreferenceSpy = jest.spyOn(themeService, 'applyThemePreference');

        component.toggleTheme();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(Theme.DARK);

        expect(component.isDarkTheme()).toBeTrue();
        tick(250);
        expect(openSpy).toHaveBeenCalledOnce();
    }));

    it('os sync toggles correctly', fakeAsync(() => {
        const applyThemePreferenceSpy = jest.spyOn(themeService, 'applyThemePreference');

        component.toggleSynced();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(Theme.LIGHT);
        expect(component.isSyncedWithOS()).toBeFalse();
        component.toggleSynced();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(undefined);
        expect(component.isSyncedWithOS()).toBeTrue();
    }));

    it('opens and closes the popover', fakeAsync(() => {
        component.openPopover();
        expect(openSpy).toHaveBeenCalledOnce();
        component.closePopover();
        expect(closeSpy).toHaveBeenCalledOnce();
    }));

    it('closes on mouse leave after 200ms', fakeAsync(() => {
        component.openPopover();
        expect(openSpy).toHaveBeenCalledOnce();
        component.mouseLeave();
        expect(closeSpy).not.toHaveBeenCalled();
        tick(250);
        expect(closeSpy).toHaveBeenCalledOnce();
    }));
});
