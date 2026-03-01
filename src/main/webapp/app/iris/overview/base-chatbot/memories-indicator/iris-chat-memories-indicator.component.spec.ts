import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { IrisChatMemoriesIndicatorComponent } from 'app/iris/overview/base-chatbot/memories-indicator/iris-chat-memories-indicator.component';
import { MemirisMemory } from 'app/iris/shared/entities/memiris.model';

describe('IrisChatMemoriesIndicatorComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisChatMemoriesIndicatorComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IrisChatMemoriesIndicatorComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisChatMemoriesIndicatorComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('does not render when there are no memories', () => {
        fixture.componentRef.setInput('accessedMemories', []);
        fixture.componentRef.setInput('createdMemories', []);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="memories-indicator-button"]')).toBeNull();
    });

    it('renders compact label and tooltip when there are memories', () => {
        fixture.componentRef.setInput('accessedMemories', [new MemirisMemory('1', 'A', 'A content', [], [], false, false)]);
        fixture.componentRef.setInput('createdMemories', [
            new MemirisMemory('2', 'B', 'B content', [], [], false, false),
            new MemirisMemory('3', 'C', 'C content', [], [], false, false),
        ]);
        fixture.detectChanges();

        const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="memories-indicator-button"]');
        expect(button).toBeTruthy();
        expect(button.textContent).toContain('1 used');
        expect(button.textContent).toContain('2 created');
        expect(button.getAttribute('aria-label')).toBe('1 memory used, 2 created');
    });

    it('hides zero values in compact label and tooltip', () => {
        fixture.componentRef.setInput('accessedMemories', []);
        fixture.componentRef.setInput('createdMemories', [new MemirisMemory('1', 'A', 'A content', [], [], false, false)]);
        fixture.detectChanges();

        const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="memories-indicator-button"]');
        expect(button.textContent).toContain('1 created');
        expect(button.textContent).not.toContain('0 used');
        expect(button.getAttribute('aria-label')).toBe('1 created');
    });

    it('opens popover with details on click', () => {
        fixture.componentRef.setInput('accessedMemories', [new MemirisMemory('1', 'Used 1', 'Used content', [], [], false, false)]);
        fixture.componentRef.setInput('createdMemories', [new MemirisMemory('2', 'Created 1', 'Created content', [], [], false, false)]);
        fixture.detectChanges();

        const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="memories-indicator-button"]');
        button.click();
        fixture.detectChanges();

        expect(document.querySelector('.iris-memories-popover')).toBeTruthy();
        expect(document.querySelector('[data-testid="memories-used-section"]')).toBeTruthy();
        expect(document.querySelector('[data-testid="memories-created-section"]')).toBeTruthy();
        expect(document.body.textContent).toContain('Used 1');
        expect(document.body.textContent).toContain('Created 1');
    });
});
