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
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';

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
        const host = fixture.debugElement.queryAll(By.directive(TumAetUiTooltipDirective)).find((candidate) => (candidate.nativeElement as HTMLElement).id === 'save-entity');
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

        const items = Array.from(document.querySelectorAll('.tumaet-ui-tooltip-bubble li'));
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

        expect(document.querySelector('.tumaet-ui-tooltip-bubble')).toBeNull();
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

    it('should describe the blocked save button only once while its tooltip is open', () => {
        vi.useFakeTimers();
        fixture.componentRef.setInput('invalidReasons', [
            { translateKey: 'reason.one', translateValues: {} },
            { translateKey: 'reason.two', translateValues: {} },
        ]);
        fixture.detectChanges();

        const host = findSubmitTooltipHost();
        const button = host.nativeElement as HTMLElement;
        button.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
        vi.advanceTimersByTime(300);
        TestBed.inject(ApplicationRef).tick();

        // The tooltip renders the same reasons, so letting it describe the host too would announce each one twice.
        expect(document.querySelector('.tumaet-ui-tooltip-bubble')).not.toBeNull();
        expect(button.getAttribute('aria-describedby')).toBe('form-footer-invalid-reasons');
    });

    it('should not emit save when the blocked button is clicked', () => {
        fixture.componentRef.setInput('invalidReasons', [{ translateKey: 'test.key', translateValues: {} }]);
        fixture.detectChanges();
        const saveSpy = vi.fn();
        fixture.componentInstance.save.subscribe(saveSpy);

        (fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement).click();

        expect(saveSpy).not.toHaveBeenCalled();
    });

    describe('when the displayed reasons may be outdated', () => {
        const staleReasons = [{ translateKey: 'test.key', translateValues: {} }];
        const clickSave = () => (fixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement).click();

        it('should emit save when the form is valid on a fresh check', () => {
            fixture.componentRef.setInput('invalidReasons', staleReasons);
            fixture.componentRef.setInput('revalidate', () => []);
            fixture.detectChanges();
            const saveSpy = vi.fn();
            fixture.componentInstance.save.subscribe(saveSpy);

            clickSave();

            expect(saveSpy).toHaveBeenCalledOnce();
        });

        it('should not emit save when the fresh check still finds reasons', () => {
            fixture.componentRef.setInput('invalidReasons', staleReasons);
            fixture.componentRef.setInput('revalidate', () => staleReasons);
            fixture.detectChanges();
            const saveSpy = vi.fn();
            fixture.componentInstance.save.subscribe(saveSpy);

            clickSave();

            expect(saveSpy).not.toHaveBeenCalled();
        });

        it('should not let a fresh check override a save that is already in progress', () => {
            fixture.componentRef.setInput('invalidReasons', staleReasons);
            fixture.componentRef.setInput('revalidate', () => []);
            fixture.componentRef.setInput('isSaving', true);
            fixture.detectChanges();
            const saveSpy = vi.fn();
            fixture.componentInstance.save.subscribe(saveSpy);

            clickSave();

            expect(saveSpy).not.toHaveBeenCalled();
        });

        it('should emit generateWithAi when the form is valid on a fresh check', () => {
            fixture.componentRef.setInput('invalidReasons', staleReasons);
            fixture.componentRef.setInput('revalidate', () => []);
            fixture.detectChanges();
            const generateSpy = vi.fn();
            fixture.componentInstance.generateWithAi.subscribe(generateSpy);

            fixture.componentInstance.onGenerateWithAi();

            expect(generateSpy).toHaveBeenCalledOnce();
        });

        it('should block save when there are reasons and no way to check them again', () => {
            // the text, modeling and file upload update pages pass no revalidate, so their displayed reasons stay authoritative
            fixture.componentRef.setInput('invalidReasons', staleReasons);
            fixture.detectChanges();
            const saveSpy = vi.fn();
            fixture.componentInstance.save.subscribe(saveSpy);

            clickSave();

            expect(saveSpy).not.toHaveBeenCalled();
        });

        it('should not check the form again when no reasons are displayed', () => {
            const revalidate = vi.fn(() => staleReasons);
            fixture.componentRef.setInput('invalidReasons', []);
            fixture.componentRef.setInput('revalidate', revalidate);
            fixture.detectChanges();
            const saveSpy = vi.fn();
            fixture.componentInstance.save.subscribe(saveSpy);

            clickSave();

            expect(revalidate).not.toHaveBeenCalled();
            expect(saveSpy).toHaveBeenCalledOnce();
        });

        it('should check the form again once for every click that the displayed reasons block', () => {
            const revalidate = vi.fn<() => ValidationReason[]>().mockReturnValueOnce(staleReasons).mockReturnValueOnce([]);
            fixture.componentRef.setInput('invalidReasons', staleReasons);
            fixture.componentRef.setInput('revalidate', revalidate);
            fixture.detectChanges();
            const saveSpy = vi.fn();
            fixture.componentInstance.save.subscribe(saveSpy);

            clickSave();
            expect(revalidate).toHaveBeenCalledTimes(1);
            expect(saveSpy).not.toHaveBeenCalled();

            // the answer is read at click time and not remembered from the click before
            clickSave();
            expect(revalidate).toHaveBeenCalledTimes(2);
            expect(saveSpy).toHaveBeenCalledOnce();
        });

        it.each([
            { name: 'the footer is disabled', input: 'isDisabled' },
            { name: 'a save is running', input: 'isSaving' },
            { name: 'the exercise is being generated with AI', input: 'isGeneratingWithAi' },
        ])('should neither emit nor check the form again when $name', ({ input }) => {
            const revalidate = vi.fn(() => []);
            fixture.componentRef.setInput('invalidReasons', staleReasons);
            fixture.componentRef.setInput('revalidate', revalidate);
            fixture.componentRef.setInput(input, true);
            fixture.detectChanges();
            const saveSpy = vi.fn();
            const generateSpy = vi.fn();
            fixture.componentInstance.save.subscribe(saveSpy);
            fixture.componentInstance.generateWithAi.subscribe(generateSpy);

            clickSave();
            fixture.componentInstance.onGenerateWithAi();

            expect(saveSpy).not.toHaveBeenCalled();
            expect(generateSpy).not.toHaveBeenCalled();
            expect(revalidate).not.toHaveBeenCalled();
        });

        it('should not emit generateWithAi when the fresh check still finds reasons', () => {
            fixture.componentRef.setInput('invalidReasons', staleReasons);
            fixture.componentRef.setInput('revalidate', () => staleReasons);
            fixture.detectChanges();
            const generateSpy = vi.fn();
            fixture.componentInstance.generateWithAi.subscribe(generateSpy);

            fixture.componentInstance.onGenerateWithAi();

            expect(generateSpy).not.toHaveBeenCalled();
        });

        it('should emit generateWithAi from a click on its button when the form is valid on a fresh check', () => {
            fixture.componentRef.setInput('invalidReasons', staleReasons);
            fixture.componentRef.setInput('revalidate', () => []);
            fixture.componentRef.setInput('showGenerateWithAi', true);
            fixture.detectChanges();
            const generateSpy = vi.fn();
            fixture.componentInstance.generateWithAi.subscribe(generateSpy);
            const generateButton = fixture.debugElement.query(By.css('#generate-with-ai'));
            expect(generateButton).not.toBeNull();

            (generateButton.nativeElement as HTMLButtonElement).click();

            expect(generateSpy).toHaveBeenCalledOnce();
        });
    });

    it('should emit save when the button is not blocked', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.componentRef.setInput('isSaving', false);
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

    it('offers normal save and cancel without an AI-generation entry point', () => {
        fixture.componentRef.setInput('isCreation', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('#save-entity')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#cancel-save')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#generate-with-ai')).toBeNull();
    });
});
