import { ArtemisTestModule } from '../../test.module';
import { ThemeSwitchComponent } from 'app/core/theme/theme-switch.component';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective } from 'ng-mocks';

describe('ThemeSwitchComponent', () => {
    let component: ThemeSwitchComponent;
    let fixture: ComponentFixture<ThemeSwitchComponent>;
    let themeService: ThemeService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockDirective(NgbPopover)],
            declarations: [ThemeSwitchComponent],
            providers: [{ provide: LocalStorageService, useClass: MockLocalStorageService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ThemeSwitchComponent);
                themeService = TestBed.inject(ThemeService);
                fixture.componentRef.setInput('popoverPlacement', ['bottom']);
                component = fixture.componentInstance;
                // @ts-ignore
                component.popover = { open: jest.fn(), close: jest.fn() };
            });
    });

    afterEach(() => jest.restoreAllMocks());

    it('theme toggles correctly', fakeAsync(() => {
        const applyThemePreferenceSpy = jest.spyOn(themeService, 'applyThemePreference');

        component.ngOnInit();
        component.toggleTheme();
        TestBed.flushEffects();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(Theme.DARK);

        expect(component.isDarkTheme()).toBeTrue();
        tick(250);
        expect(component.popover().open).toHaveBeenCalledOnce();

        flush();
    }));

    it('os sync toggles correctly', fakeAsync(() => {
        const applyThemePreferenceSpy = jest.spyOn(themeService, 'applyThemePreference');

        component.ngOnInit();
        component.toggleSynced();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(Theme.LIGHT);
        expect(component.isSyncedWithOS()).toBeFalse();
        component.toggleSynced();

        expect(applyThemePreferenceSpy).toHaveBeenCalledWith(undefined);
        expect(component.isSyncedWithOS()).toBeTrue();
    }));

    it('opens and closes the popover', fakeAsync(() => {
        component.openPopover();
        expect(component.popover().open).toHaveBeenCalledOnce();
        component.closePopover();
        expect(component.popover().close).toHaveBeenCalledOnce();
    }));

    it('closes on mouse leave after 200ms', fakeAsync(() => {
        component.openPopover();
        expect(component.popover().open).toHaveBeenCalledOnce();
        component.mouseLeave();
        expect(component.popover().close).not.toHaveBeenCalled();
        tick(250);
        expect(component.popover().close).toHaveBeenCalledOnce();
    }));
});
