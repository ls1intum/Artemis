import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { provideHttpClient } from '@angular/common/http';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockInstance, beforeEach, describe, expect, it, vi } from 'vitest';

describe('ButtonComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: ButtonComponent;
    let fixture: ComponentFixture<ButtonComponent>;
    let debugElement: DebugElement;

    let clickSpy: MockInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip), FontAwesomeTestingModule, ButtonComponent, FeatureToggleDirective, TranslatePipeMock, MockDirective(TranslateDirective)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ButtonComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                clickSpy = vi.spyOn(comp.onClick, 'emit');
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
        fixture.componentRef.setInput('title', 'artemisApp.title.test');
        fixture.componentRef.setInput('icon', 'redo');

        fixture.detectChanges();

        const title = getTitle();
        expect(title).not.toBeNull();

        const icon = getIcon();
        expect(icon).not.toBeNull();

        const loadingIcon = getLoading();
        expect(loadingIcon).toBeNull();
    });

    it('should render button without icon and with title', () => {
        fixture.componentRef.setInput('title', 'artemisApp.title.test');

        fixture.detectChanges();

        const title = getTitle();
        expect(title).not.toBeNull();

        const icon = getIcon();
        expect(icon).toBeNull();

        const loadingIcon = getLoading();
        expect(loadingIcon).toBeNull();
    });

    it('should render button with icon and without title', () => {
        fixture.componentRef.setInput('icon', 'redo');

        fixture.detectChanges();

        const title = getTitle();
        expect(title).toBeNull();

        const icon = getIcon();
        expect(icon).not.toBeNull();

        const loadingIcon = getLoading();
        expect(loadingIcon).toBeNull();
    });

    it('should disable complete button if disabled is set', () => {
        fixture.componentRef.setInput('title', 'artemisApp.title.test');
        fixture.componentRef.setInput('icon', 'redo');
        fixture.componentRef.setInput('disabled', true);

        fixture.detectChanges();

        const button = getButton();
        expect(button).not.toBeNull();
        expect(button.disabled).toBeTruthy();
        button.click();

        expect(clickSpy).not.toHaveBeenCalled();
    });

    it('should enable complete button if disabled is set to false', () => {
        fixture.componentRef.setInput('title', 'artemisApp.title.test');
        fixture.componentRef.setInput('icon', 'redo');
        fixture.componentRef.setInput('disabled', false);

        fixture.detectChanges();

        const button = getButton();
        expect(button).not.toBeNull();
        expect(button.disabled).toBeFalsy();
        button.click();

        expect(clickSpy).toHaveBeenCalledOnce();
    });

    it('should show loading indicator when loading is set', () => {
        fixture.componentRef.setInput('title', 'artemisApp.title.test');
        fixture.componentRef.setInput('icon', 'redo');
        fixture.componentRef.setInput('isLoading', true);

        fixture.detectChanges();

        const button = getButton();
        expect(button).not.toBeNull();
        expect(button.disabled).toBeTruthy();
        const loadingIcon = getLoading();
        expect(loadingIcon).not.toBeNull();

        button.click();

        expect(clickSpy).not.toHaveBeenCalled();
    });
});
