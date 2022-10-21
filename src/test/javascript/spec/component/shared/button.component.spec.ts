import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockDirective } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('ButtonComponent', () => {
    let comp: ButtonComponent;
    let fixture: ComponentFixture<ButtonComponent>;
    let debugElement: DebugElement;

    let clickSpy: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ButtonComponent, FeatureToggleDirective, TranslatePipeMock, MockDirective(NgbTooltip), MockDirective(TranslateDirective)],
            providers: [{ provide: FeatureToggleService, useClass: MockFeatureToggleService }],
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
        expect(title).not.toBeNull();

        const icon = getIcon();
        expect(icon).not.toBeNull();

        const loadingIcon = getLoading();
        expect(loadingIcon).toBeNull();
    });

    it('should render button without icon and with title', () => {
        comp.title = 'artemisApp.title.test';

        fixture.detectChanges();

        const title = getTitle();
        expect(title).not.toBeNull();

        const icon = getIcon();
        expect(icon).toBeNull();

        const loadingIcon = getLoading();
        expect(loadingIcon).toBeNull();
    });

    it('should render button with icon and without title', () => {
        comp.icon = 'redo';

        fixture.detectChanges();

        const title = getTitle();
        expect(title).toBeNull();

        const icon = getIcon();
        expect(icon).not.toBeNull();

        const loadingIcon = getLoading();
        expect(loadingIcon).toBeNull();
    });

    it('should disable complete button if disabled is set', fakeAsync(() => {
        comp.title = 'artemisApp.title.test';
        comp.icon = 'redo';
        comp.disabled = true;

        fixture.detectChanges();

        const button = getButton();
        expect(button).not.toBeNull();
        expect(button.disabled).toBeTrue();
        button.click();
        tick();

        expect(clickSpy).not.toHaveBeenCalled();
    }));

    it('should enable complete button if disabled is set to false', fakeAsync(() => {
        comp.title = 'artemisApp.title.test';
        comp.icon = 'redo';
        comp.disabled = false;

        fixture.detectChanges();

        const button = getButton();
        expect(button).not.toBeNull();
        expect(button.disabled).toBeFalse();
        button.click();
        tick();

        expect(clickSpy).toHaveBeenCalledOnce();
    }));

    it('should show loading indicator when loading is set', fakeAsync(() => {
        comp.title = 'artemisApp.title.test';
        comp.icon = 'redo';
        comp.isLoading = true;

        fixture.detectChanges();

        const button = getButton();
        expect(button).not.toBeNull();
        expect(button.disabled).toBeTrue();
        const loadingIcon = getLoading();
        expect(loadingIcon).not.toBeNull();

        button.click();
        tick();

        expect(clickSpy).not.toHaveBeenCalled();
    }));
});
