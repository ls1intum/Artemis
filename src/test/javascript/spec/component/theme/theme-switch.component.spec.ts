import { ArtemisTestModule } from '../../test.module';
import { ThemeSwitchComponent } from 'app/core/theme/theme-switch.component';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { MockDirective } from 'ng-mocks';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';

describe('ThemeSwitchComponent', () => {
    let component: ThemeSwitchComponent;
    let fixture: ComponentFixture<ThemeSwitchComponent>;
    let themeService: ThemeService;
    let applyThemeSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ThemeSwitchComponent, MockDirective(NgbPopover)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ThemeSwitchComponent);
                themeService = TestBed.inject(ThemeService);
                applyThemeSpy = jest.spyOn(themeService, 'applyTheme');
                component = fixture.componentInstance;
                // @ts-ignore
                component.popover = { open: jest.fn(), close: jest.fn() };
            });
    });

    afterEach(() => jest.restoreAllMocks());

    it('oninit: subscribe, open popover after 1200ms', fakeAsync(() => {
        const subscribeSpy = jest.spyOn(themeService, 'getCurrentThemeObservable');
        themeService.isAutoDetected = true;

        component.ngOnInit();
        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(component.popover.open).not.toHaveBeenCalled();

        tick(1200);

        expect(component.popover.open).toHaveBeenCalled();
        expect(component.isByAutoDetection).toBeTrue();
    }));

    it('toggles correctly', fakeAsync(() => {
        component.ngOnInit();
        component.toggle();
        expect(component.animate).toBeFalse();
        expect(component.openPopupAfterNextChange).toBeTrue();

        tick();

        expectSwitchToDark();

        flush();
    }));

    it('enableNow button works correctly', fakeAsync(() => {
        component.ngOnInit();
        component.enableNow();
        expect(component.popover.close).toHaveBeenCalledOnce();
        expect(component.openPopupAfterNextChange).toBeTrue();

        tick(200);

        expectSwitchToDark();

        flush();
    }));

    function expectSwitchToDark() {
        expect(applyThemeSpy).toHaveBeenCalledOnce();
        expect(applyThemeSpy).toHaveBeenCalledWith(Theme.DARK);
        expect(component.isDark).toBeTrue();
        expect(component.animate).toBeTrue();
        expect(component.isByAutoDetection).toBeFalse();
        expect(component.openPopupAfterNextChange).toBeFalse();

        tick(250);

        expect(component.popover.open).toHaveBeenCalled();
    }

    it('manualClose works correctly', fakeAsync(() => {
        component.manualClose();

        expect(component.popover.close).toHaveBeenCalledOnce();

        tick(200);

        expect(applyThemeSpy).toHaveBeenCalledWith(Theme.LIGHT);
        expect(component.isByAutoDetection).toBeFalse();
    }));
});
