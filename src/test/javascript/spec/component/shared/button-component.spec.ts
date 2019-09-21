import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateModule } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core';
import { DebugElement, SimpleChange, SimpleChanges } from '@angular/core';
import { SinonStub, stub, SinonSpy, spy } from 'sinon';
import { of, Subject } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { MockParticipationWebsocketService, MockSyncStorage } from '../../mocks';
import { ParticipationWebsocketService } from 'app/entities/participation';
import { Exercise } from 'app/entities/exercise';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming-submission/programming-submission.service';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { ProgrammmingExerciseInstructorSubmissionStateComponent } from 'app/entities/programming-exercise/actions/programmming-exercise-instructor-submission-state.component';
import { triggerChanges } from '../../utils/general.utils';
import { ButtonComponent } from 'app/shared/components';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

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
            providers: [JhiLanguageHelper],
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
