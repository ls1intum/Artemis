import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { DebugElement } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockProvider, MockDirective } from 'ng-mocks';
import { empty } from 'rxjs';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('ButtonComponent', () => {
    let comp: ButtonComponent;
    let fixture: ComponentFixture<ButtonComponent>;
    let debugElement: DebugElement;

    let clickSpy: jest.SpyInstance;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule],
            declarations: [ButtonComponent, FeatureToggleDirective, TranslatePipeMock, MockDirective(NgbTooltip), MockDirective(TranslateDirective)],
            providers: [
                MockProvider(JhiLanguageHelper, { language: empty() }),
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ButtonComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                clickSpy = jest.spyOn(comp.onClick, 'emit');
            });
    });

    const getButton = () => {
        const button = debugElement.query(By.css(`.jhi-btn`));
        return button ? button.nativeElement : null;
    };

    const getTitle = () => {
        const title = debugElement.query(By.css(`.jhi-btn > .jhi-btn__title`));
        return title ? title.nativeElement : null;
    };

    const getIcon = () => {
        const icon = debugElement.query(By.css('.jhi-btn > .jhi-btn__icon'));
        return icon ? icon.nativeElement : null;
    };

    const getLoading = () => {
        const loadingIcon = debugElement.query(By.css('.jhi-btn > .jhi-btn__loading'));
        return loadingIcon ? loadingIcon.nativeElement : null;
    };

    it('should render button with icon and title', () => {
        comp.title = 'artemisApp.title.test';
        comp.icon = 'redo';

        fixture.detectChanges();

        const title = getTitle();
        expect(title).not.toBe(null);

        const icon = getIcon();
        expect(icon).not.toBe(null);

        const loadingIcon = getLoading();
        expect(loadingIcon).toBe(null);
    });

    it('should render button without icon and with title', () => {
        comp.title = 'artemisApp.title.test';

        fixture.detectChanges();

        const title = getTitle();
        expect(title).not.toBe(null);

        const icon = getIcon();
        expect(icon).toBe(null);

        const loadingIcon = getLoading();
        expect(loadingIcon).toBe(null);
    });

    it('should render button with icon and without title', () => {
        comp.icon = 'redo';

        fixture.detectChanges();

        const title = getTitle();
        expect(title).toBe(null);

        const icon = getIcon();
        expect(icon).not.toBe(null);

        const loadingIcon = getLoading();
        expect(loadingIcon).toBe(null);
    });

    it('should disable complete button if disabled is set', async () => {
        comp.title = 'artemisApp.title.test';
        comp.icon = 'redo';
        comp.disabled = true;

        fixture.detectChanges();

        const button = getButton();
        expect(button).not.toBe(null);
        expect(button.disabled).toBe(true);
        button.click();
        await fixture.whenStable();

        expect(clickSpy).toHaveBeenCalledTimes(0);
    });

    it('should enable complete button if disabled is set to false', async () => {
        comp.title = 'artemisApp.title.test';
        comp.icon = 'redo';
        comp.disabled = false;

        fixture.detectChanges();

        const button = getButton();
        expect(button).not.toBe(null);
        expect(button.disabled).toBe(false);
        button.click();
        await fixture.whenStable();

        expect(clickSpy).toHaveBeenCalledTimes(1);
    });

    it('should show loading indicator when loading is set', async () => {
        comp.title = 'artemisApp.title.test';
        comp.icon = 'redo';
        comp.isLoading = true;

        fixture.detectChanges();

        const button = getButton();
        expect(button).not.toBe(null);
        expect(button.disabled).toBe(true);
        const loadingIcon = getLoading();
        expect(loadingIcon).not.toBe(null);

        button.click();
        await fixture.whenStable();

        expect(clickSpy).toHaveBeenCalledTimes(0);
    });
});
