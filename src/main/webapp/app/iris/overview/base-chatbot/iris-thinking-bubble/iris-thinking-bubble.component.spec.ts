import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisThinkingBubbleComponent } from './iris-thinking-bubble.component';
import { By } from '@angular/platform-browser';

describe('IrisThinkingBubbleComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisThinkingBubbleComponent;
    let fixture: ComponentFixture<IrisThinkingBubbleComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IrisThinkingBubbleComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisThinkingBubbleComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('message', 'Reviewing lecture materials...');
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render the message text', () => {
        const textElement = fixture.debugElement.query(By.css('.thinking-text'));
        expect(textElement.nativeElement.textContent).toContain('Reviewing lecture materials...');
    });

    it('should render three animated dots', () => {
        const dots = fixture.debugElement.queryAll(By.css('.dot'));
        expect(dots).toHaveLength(3);
    });

    it('should have role="status" for accessibility', () => {
        const bubble = fixture.debugElement.query(By.css('.thinking-bubble'));
        expect(bubble.nativeElement.getAttribute('role')).toBe('status');
    });

    it('should set aria-label to the message', () => {
        const bubble = fixture.debugElement.query(By.css('.thinking-bubble'));
        expect(bubble.nativeElement.getAttribute('aria-label')).toBe('Reviewing lecture materials...');
    });

    it('should update when message input changes', async () => {
        fixture.componentRef.setInput('message', 'Crafting questions...');
        await fixture.whenStable();
        fixture.detectChanges();
        const textElement = fixture.debugElement.query(By.css('.thinking-text'));
        expect(textElement.nativeElement.textContent).toContain('Crafting questions...');
    });
});
