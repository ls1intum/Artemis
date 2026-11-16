import { TestBed } from '@angular/core/testing';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AiModeSelectorComponent } from './ai-mode-selector.component';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

describe('AiModeSelectorComponent', () => {
    beforeEach(() => {
        TestBed.configureTestingModule({ imports: [AiModeSelectorComponent] }).overrideComponent(AiModeSelectorComponent, {
            remove: { imports: [TranslateDirective] },
            add: { imports: [MockDirective(TranslateDirective)] },
        });
    });

    it('shows the current mode and emits a different radio choice', () => {
        const fixture = TestBed.createComponent(AiModeSelectorComponent);
        fixture.componentRef.setInput('currentSelection', LLMSelectionDecision.CLOUD_AI);
        fixture.detectChanges();
        const radios: NodeListOf<HTMLInputElement> = fixture.nativeElement.querySelectorAll('input[type="radio"]');
        expect(radios).toHaveLength(2);
        expect(radios[0].checked).toBe(true);
        expect(radios[1].checked).toBe(false);
        const choice = vi.spyOn(fixture.componentInstance.choice, 'emit');
        radios[1].click();
        expect(choice).toHaveBeenCalledExactlyOnceWith(LLMSelectionDecision.NO_AI);
    });

    it.each([LLMSelectionDecision.CLOUD_AI, LLMSelectionDecision.LOCAL_AI, LLMSelectionDecision.NO_AI])(
        'emits exactly one choice when the already-selected %s option is activated',
        (decision) => {
            const fixture = TestBed.createComponent(AiModeSelectorComponent);
            fixture.componentRef.setInput('isOnPremiseEnabled', true);
            fixture.componentRef.setInput('currentSelection', decision);
            fixture.detectChanges();
            const radio: HTMLInputElement = fixture.nativeElement.querySelector('input:checked');
            const choice = vi.spyOn(fixture.componentInstance.choice, 'emit');

            radio.click();

            expect(choice).toHaveBeenCalledExactlyOnceWith(decision);
        },
    );

    it('prevents changing the selection while disabled and allows it again afterward', () => {
        const fixture = TestBed.createComponent(AiModeSelectorComponent);
        fixture.componentRef.setInput('isOnPremiseEnabled', true);
        fixture.componentRef.setInput('currentSelection', LLMSelectionDecision.CLOUD_AI);
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        const radios: NodeListOf<HTMLInputElement> = fixture.nativeElement.querySelectorAll('input[type="radio"]');
        const choice = vi.spyOn(fixture.componentInstance.choice, 'emit');

        for (const radio of radios) {
            expect(radio.disabled).toBe(true);
            radio.click();
        }
        expect(choice).not.toHaveBeenCalled();
        expect(radios[0].checked).toBe(true);

        fixture.componentRef.setInput('disabled', false);
        fixture.detectChanges();
        radios[1].click();
        expect(choice).toHaveBeenCalledExactlyOnceWith(LLMSelectionDecision.LOCAL_AI);
    });

    it('includes local AI only when enabled and uses a separate radio group per instance', () => {
        const first = TestBed.createComponent(AiModeSelectorComponent);
        const second = TestBed.createComponent(AiModeSelectorComponent);
        first.componentRef.setInput('isOnPremiseEnabled', true);
        first.componentRef.setInput('currentSelection', LLMSelectionDecision.LOCAL_AI);
        first.detectChanges();
        second.detectChanges();
        const radios: NodeListOf<HTMLInputElement> = first.nativeElement.querySelectorAll('input');
        expect(radios).toHaveLength(3);
        expect(radios[1].checked).toBe(true);
        expect(radios[0].name).not.toBe(second.nativeElement.querySelector('input').name);
    });
});
