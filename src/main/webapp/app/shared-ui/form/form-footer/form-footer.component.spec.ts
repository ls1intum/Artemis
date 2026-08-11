import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { ExerciseUpdateNotificationComponent } from 'app/exercise/exercise-update-notification/exercise-update-notification.component';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { FormFooterComponent } from 'app/shared-ui/form/form-footer/form-footer.component';
import { Tooltip } from 'primeng/tooltip';

describe('FormFooterComponent', () => {
    let fixture: ComponentFixture<FormFooterComponent>;
    let comp: FormFooterComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(ExerciseUpdateNotificationComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FormFooterComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('update title depending on input signals', () => {
        fixture.componentRef.setInput('isCreation', true);
        fixture.componentRef.setInput('isImport', false);
        expect(comp.saveTitle()).toBe('entity.action.generate');

        fixture.componentRef.setInput('isImport', true);
        expect(comp.saveTitle()).toBe('entity.action.import');

        fixture.componentRef.setInput('isImport', false);
        fixture.componentRef.setInput('isCreation', false);

        expect(comp.saveTitle()).toBe('entity.action.save');
    });

    it('should display saving badge when isSaving is true', () => {
        fixture.componentRef.setInput('isSaving', true);
        fixture.detectChanges();
        const savingBadge = fixture.debugElement.query(By.css('.badge.bg-secondary'));
        expect(savingBadge).toBeTruthy();
    });

    it('should not display the exercise update notification when in creation or import mode', () => {
        fixture.componentRef.setInput('isCreation', true);
        fixture.componentRef.setInput('isImport', false);
        fixture.detectChanges();
        const notificationComponent = fixture.debugElement.query(By.css('jhi-exercise-update-notification'));
        expect(notificationComponent).toBeNull();
    });

    it('should not render an invalid input badge', () => {
        fixture.componentRef.setInput('invalidReasons', [{ translateKey: 'test.key', translateValues: {} }]);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('.badge.bg-danger'))).toBeNull();
    });

    it('should list every invalid reason in the submit tooltip', () => {
        fixture.componentRef.setInput('invalidReasons', [
            { translateKey: 'first.reason', translateValues: {} },
            { translateKey: 'second.reason', translateValues: {} },
        ]);
        fixture.detectChanges();

        expect(comp.invalidReasonsTooltip()).toBe('first.reason\nsecond.reason');
    });

    // A disabled button emits no mouse events, so the tooltip must sit on the wrapper. Attaching it to the button
    // itself renders it unreachable exactly when it has something to say.
    it('should attach the tooltip to the wrapper rather than the disabled button', () => {
        fixture.componentRef.setInput('invalidReasons', [{ translateKey: 'test.key', translateValues: {} }]);
        fixture.detectChanges();

        const tooltipHosts = fixture.debugElement.queryAll(By.directive(Tooltip));
        const submitTooltip = tooltipHosts.find((host) => (host.nativeElement as HTMLElement).classList.contains('submit-tooltip-host'));

        expect(submitTooltip).toBeTruthy();
        expect((submitTooltip!.nativeElement as HTMLElement).tagName).not.toBe('BUTTON');
        expect((submitTooltip!.nativeElement as HTMLElement).querySelector('#save-entity')).toBeTruthy();
    });

    it('should disable the submit tooltip when the form is valid', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.detectChanges();

        const submitTooltip = fixture.debugElement.queryAll(By.directive(Tooltip)).find((host) => (host.nativeElement as HTMLElement).classList.contains('submit-tooltip-host'));

        expect(submitTooltip!.injector.get(Tooltip).disabled).toBeTruthy();
    });

    it('should disable the save button when there are invalid reasons', () => {
        fixture.componentRef.setInput('invalidReasons', [{ translateKey: 'test.key', translateValues: {} }]);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.detectChanges();

        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.disabled).toBeTruthy();
    });

    it('should enable save button when form is valid', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.componentRef.setInput('isSaving', false);
        fixture.componentRef.setInput('isGeneratingWithAi', false);
        fixture.detectChanges();
        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.disabled).toBeFalsy();
    });

    it('should disable save button when saving is in progress', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.componentRef.setInput('isSaving', true);
        fixture.detectChanges();
        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.disabled).toBeTruthy();
    });

    it('should disable save button while generating with AI', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.componentRef.setInput('isGeneratingWithAi', true);
        fixture.detectChanges();
        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.disabled).toBeTruthy();
    });
});
