import { ArtemisTestModule } from '../../test.module';
import { ThemeSwitchComponent } from 'app/core/theme/theme-switch.component';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
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

    it('oninit: subscribe, open popover after 1200ms', fakeAsync(() => {
        const subscribeSpy = jest.spyOn(themeService, 'getCurrentThemeObservable');

        component.ngOnInit();
        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(component.popover.open).not.toHaveBeenCalled();

        tick(1200);

        expect(component.popover.open).toHaveBeenCalledOnce();
        expect(component.showInitialHints).toBeTrue();

        component.closePopover();

        tick(200);

        expect(component.showInitialHints).toBeFalse();
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
        expect(component.isDark).toBeTrue();
        expect(component.animate).toBeTrue();
        expect(component.openPopupAfterNextChange).toBeFalse();

        tick(250);

        expect(component.popover.open).toHaveBeenCalledOnce();
    }
});
