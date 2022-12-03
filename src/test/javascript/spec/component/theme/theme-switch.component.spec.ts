import { ArtemisTestModule } from '../../test.module';
import { ThemeSwitchComponent } from 'app/core/theme/theme-switch.component';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { ThemeService } from 'app/core/theme/theme.service';
import { MockDirective } from 'ng-mocks';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';

describe('ThemeSwitchComponent', () => {
    let component: ThemeSwitchComponent;
    let fixture: ComponentFixture<ThemeSwitchComponent>;
    let themeService: ThemeService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ThemeSwitchComponent, MockDirective(NgbPopover)],
            providers: [{ provide: LocalStorageService, useClass: MockLocalStorageService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ThemeSwitchComponent);
                themeService = TestBed.inject(ThemeService);
                component = fixture.componentInstance;
                // @ts-ignore
                component.popover = { open: jest.fn(), close: jest.fn() };
            });
    });

    afterEach(() => jest.restoreAllMocks());

    it('oninit: subscribe to theme service', fakeAsync(() => {
        const subscribeSpy = jest.spyOn(themeService, 'getCurrentThemeObservable');

        component.ngOnInit();
        expect(subscribeSpy).toHaveBeenCalledOnce();
    }));

    it('theme toggles correctly', fakeAsync(() => {
        component.ngOnInit();
        component.toggleTheme();
        expect(component.animate).toBeFalse();
        expect(component.openPopupAfterNextChange).toBeTrue();

        tick();

        expectSwitchToDark();

        flush();
    }));

    it('os sync toggles correctly', fakeAsync(() => {
        component.ngOnInit();
        component.toggleSynced();

        tick();

        expect(component.isSynced).toBeFalse();
        component.toggleSynced();

        tick();

        expect(component.isSynced).toBeTrue();

        flush();
    }));

    it('opens and closes the popover', fakeAsync(() => {
        component.openPopover();
        expect(component.popover.open).toHaveBeenCalledOnce();
        component.closePopover();
        expect(component.popover.close).toHaveBeenCalledOnce();
    }));

    it('closes on mouse leave after 200ms', fakeAsync(() => {
        component.openPopover();
        expect(component.popover.open).toHaveBeenCalledOnce();
        component.mouseLeave();
        expect(component.popover.close).not.toHaveBeenCalled();
        tick(250);
        expect(component.popover.close).toHaveBeenCalledOnce();
    }));

    function expectSwitchToDark() {
        expect(component.isDark).toBeTrue();
        expect(component.animate).toBeTrue();
        expect(component.openPopupAfterNextChange).toBeFalse();

        tick(250);

        expect(component.popover.open).toHaveBeenCalledOnce();
    }
});
