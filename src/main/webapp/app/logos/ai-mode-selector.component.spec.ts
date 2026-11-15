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
        expect(choice).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
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
