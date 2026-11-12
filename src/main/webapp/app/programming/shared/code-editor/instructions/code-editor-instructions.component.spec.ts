import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CodeEditorInstructionsComponent } from './code-editor-instructions.component';

describe('CodeEditorInstructionsComponent keyboard controls', () => {
    let fixture: ComponentFixture<CodeEditorInstructionsComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(CodeEditorInstructionsComponent);
    });

    it.each([false, true])('only handles collapse keys when enabled (disabled: %s)', (disabled) => {
        fixture.componentRef.setInput('disableCollapse', disabled);
        fixture.detectChanges();
        const header = fixture.nativeElement.querySelector('[role="button"]') as HTMLElement;
        const toggle = vi.spyOn(fixture.componentInstance.onToggleCollapse, 'emit');
        expect(header.getAttribute('aria-disabled')).toBe(String(disabled));
        expect(header.getAttribute('aria-expanded')).toBe('true');
        const down = new KeyboardEvent('keydown', { key: ' ', repeat: true, bubbles: true, cancelable: true });
        header.dispatchEvent(down);
        expect(down.defaultPrevented).toBe(!disabled);
        expect(toggle).not.toHaveBeenCalled();
        header.dispatchEvent(new KeyboardEvent('keyup', { key: ' ', bubbles: true }));
        fixture.detectChanges();
        expect(toggle).toHaveBeenCalledTimes(disabled ? 0 : 1);
        if (disabled) {
            expect(header.getAttribute('aria-expanded')).toBe('true');
        } else {
            // Collapsed, the header gives way to the rail, which opens the instructions again
            const rail = fixture.nativeElement.querySelector('[data-testid="collapsed-instructions"]') as HTMLElement;
            expect(rail.getAttribute('aria-expanded')).toBe('false');
            rail.click();
            fixture.detectChanges();
            expect(toggle).toHaveBeenCalledTimes(2);
            expect(fixture.nativeElement.querySelector('[data-testid="collapsed-instructions"]')).toBeNull();
        }
    });
});
