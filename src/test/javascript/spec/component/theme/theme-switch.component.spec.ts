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

    it('theme toggles correctly', fakeAsync(async () => {
        const applyThemeSpy = jest.spyOn(themeService, 'applyTheme');

        component.ngOnInit();
        component.toggleTheme();

        expect(applyThemeSpy).toHaveBeenCalledWith(Theme.DARK);
        expect(component.animate()).toBeFalse();
        expect(component.openPopupAfterNextChange()).toBeTrue();

        tick();
        await fixture.whenStable();

        expectSwitchToDark();

        flush();
    }));

    it('os sync toggles correctly', fakeAsync(() => {
        const applyThemeSpy = jest.spyOn(themeService, 'applyTheme');

        component.ngOnInit();
        component.toggleSynced();

        expect(applyThemeSpy).toHaveBeenCalledWith(Theme.LIGHT);
        expect(component.isSyncedWithOS()).toBeFalse();
        component.toggleSynced();

        expect(applyThemeSpy).toHaveBeenCalledWith(undefined);
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

    function expectSwitchToDark() {
        expect(component.isDarkTheme()).toBeTrue();
        expect(component.animate()).toBeTrue();
        expect(component.openPopupAfterNextChange()).toBeFalse();

        tick(250);

        expect(component.popover().open).toHaveBeenCalledOnce();
    }
});
