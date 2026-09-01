import { vi } from 'vitest';
import { ApplicationRef, DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { ExerciseUpdateNotificationComponent } from 'app/exercise/exercise-update-notification/exercise-update-notification.component';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { FormFooterComponent } from 'app/shared-ui/form/form-footer/form-footer.component';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';

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
        if (vi.isFakeTimers()) {
            vi.runOnlyPendingTimers();
            vi.useRealTimers();
        }
        vi.restoreAllMocks();
    });

    const findSubmitTooltipHost = (): DebugElement => {
        const host = fixture.debugElement.queryAll(By.directive(TumUiTooltipDirective)).find((candidate) => (candidate.nativeElement as HTMLElement).id === 'save-entity');
        if (!host) {
            throw new Error('expected the submit button to carry the tooltip');
        }
        return host;
    };

    const showTooltip = (host: DebugElement): void => {
        (host.nativeElement as HTMLElement).dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(200);
        TestBed.inject(ApplicationRef).tick();
    };

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

    it('should render the save button label from the resolved title', () => {
        fixture.componentRef.setInput('isCreation', true);
        fixture.componentRef.setInput('isImport', false);
        fixture.detectChanges();

        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLElement;
        expect(saveButton.querySelector('span')?.textContent).toBe('entity.action.generate');
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

    it('should render every invalid reason as its own list item', () => {
        vi.useFakeTimers();
        fixture.componentRef.setInput('invalidReasons', [
            { translateKey: 'first.reason', translateValues: {} },
            { translateKey: 'second.reason', translateValues: {} },
        ]);
        fixture.detectChanges();

        showTooltip(findSubmitTooltipHost());

        const items = Array.from(document.querySelectorAll('.tum-ui-tooltip-bubble li'));
        expect(items.map((item) => item.textContent?.trim())).toEqual(['first.reason', 'second.reason']);
    });

    // aria-disabled keeps pointer events, so the blocked button itself can host the tooltip.
    it('should attach the tooltip to the submit button itself', () => {
        fixture.componentRef.setInput('invalidReasons', [{ translateKey: 'test.key', translateValues: {} }]);
        fixture.detectChanges();

        expect((findSubmitTooltipHost().nativeElement as HTMLElement).tagName).toBe('BUTTON');
    });

    it('should not open the submit tooltip when the form is valid', () => {
        vi.useFakeTimers();
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.detectChanges();

        showTooltip(findSubmitTooltipHost());

        expect(document.querySelector('.tum-ui-tooltip-bubble')).toBeNull();
    });

    it('should mark the save button aria-disabled but keep it focusable when there are invalid reasons', () => {
        fixture.componentRef.setInput('invalidReasons', [{ translateKey: 'test.key', translateValues: {} }]);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.detectChanges();

        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.getAttribute('aria-disabled')).toBe('true');
        // a natively disabled button leaves the tab order, so keyboard users could never reach the reasons
        expect(saveButton.disabled).toBeFalsy();
        expect(saveButton.getAttribute('aria-describedby')).toBe('form-footer-invalid-reasons');
        expect(document.getElementById('form-footer-invalid-reasons')).not.toBeNull();
    });

    it('should not emit save when the blocked button is clicked', () => {
        fixture.componentRef.setInput('invalidReasons', [{ translateKey: 'test.key', translateValues: {} }]);
        fixture.detectChanges();
        const saveSpy = vi.fn();
        fixture.componentInstance.save.subscribe(saveSpy);

        (fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement).click();

        expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should emit save when the button is not blocked', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.componentRef.setInput('isSaving', false);
        fixture.componentRef.setInput('isGeneratingWithAi', false);
        fixture.detectChanges();
        const saveSpy = vi.fn();
        fixture.componentInstance.save.subscribe(saveSpy);

        (fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement).click();

        expect(saveSpy).toHaveBeenCalledOnce();
    });

    it('should enable save button when form is valid', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.componentRef.setInput('isSaving', false);
        fixture.componentRef.setInput('isGeneratingWithAi', false);
        fixture.detectChanges();
        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.getAttribute('aria-disabled')).toBe('false');
        expect(saveButton.getAttribute('aria-describedby')).toBeNull();
    });

    it('should disable save button when saving is in progress', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.componentRef.setInput('isSaving', true);
        fixture.detectChanges();
        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.getAttribute('aria-disabled')).toBe('true');
    });

    it('should disable save button while generating with AI', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.componentRef.setInput('isGeneratingWithAi', true);
        fixture.detectChanges();
        const saveButton = fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.getAttribute('aria-disabled')).toBe('true');
    });
});
