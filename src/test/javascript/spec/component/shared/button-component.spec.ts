import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { DebugElement } from '@angular/core';
import { SinonSpy, spy } from 'sinon';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { ButtonComponent } from 'app/shared/components';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SessionStorageStrategy } from 'app/shared/image/SessionStorageStrategy';
import { MockSyncStorage } from '../../mocks';
import { FeatureToggleService } from 'app/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../mocks/mock-feature-toggle-service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ButtonComponent', () => {
    let comp: ButtonComponent;
    let fixture: ComponentFixture<ButtonComponent>;
    let debugElement: DebugElement;

    let clickSpy: SinonSpy;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedComponentModule],
            providers: [
                JhiLanguageHelper,
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

                clickSpy = spy(comp.onClick, 'emit');
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
        expect(title).to.exist;

        const icon = getIcon();
        expect(icon).to.exist;

        const loadingIcon = getLoading();
        expect(loadingIcon).not.to.exist;
    });

    it('should render button without icon and with title', () => {
        comp.title = 'artemisApp.title.test';

        fixture.detectChanges();

        const title = getTitle();
        expect(title).to.exist;

        const icon = getIcon();
        expect(icon).not.to.exist;

        const loadingIcon = getLoading();
        expect(loadingIcon).not.to.exist;
    });

    it('should render button with icon and without title', () => {
        comp.icon = 'redo';

        fixture.detectChanges();

        const title = getTitle();
        expect(title).not.to.exist;

        const icon = getIcon();
        expect(icon).to.exist;

        const loadingIcon = getLoading();
        expect(loadingIcon).not.to.exist;
    });

    it('should disable complete button if disabled is set', async () => {
        comp.title = 'artemisApp.title.test';
        comp.icon = 'redo';
        comp.disabled = true;

        fixture.detectChanges();

        const button = getButton();
        expect(button).to.exist;
        expect(button.disabled).to.be.true;
        button.click();
        await fixture.whenStable();

        expect(clickSpy).to.not.have.been.called;
    });

    it('should enable complete button if disabled is set to false', async () => {
        comp.title = 'artemisApp.title.test';
        comp.icon = 'redo';
        comp.disabled = false;

        fixture.detectChanges();

        const button = getButton();
        expect(button).to.exist;
        expect(button.disabled).to.be.false;
        button.click();
        await fixture.whenStable();

        expect(clickSpy).to.have.been.called;
    });

    it('should show loading indicator when loading is set', async () => {
        comp.title = 'artemisApp.title.test';
        comp.icon = 'redo';
        comp.isLoading = true;

        fixture.detectChanges();

        const button = getButton();
        expect(button).to.exist;
        expect(button.disabled).to.be.true;
        const loadingIcon = getLoading();
        expect(loadingIcon).to.exist;

        button.click();
        await fixture.whenStable();

        expect(clickSpy).to.not.have.been.called;
    });
});
