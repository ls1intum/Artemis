import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { IrisChatMemoriesIndicatorComponent } from 'app/iris/overview/base-chatbot/memories-indicator/iris-chat-memories-indicator.component';
import { MemirisMemory } from 'app/iris/shared/entities/memiris.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('IrisChatMemoriesIndicatorComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisChatMemoriesIndicatorComponent>;
    let component: IrisChatMemoriesIndicatorComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IrisChatMemoriesIndicatorComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisChatMemoriesIndicatorComponent);
        component = fixture.componentInstance;
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
        expect(component.usedCount()).toBe(1);
        expect(component.createdCount()).toBe(2);
        expect(component.compactLabel()).toBeTruthy();
        expect(component.tooltipText()).toBeTruthy();
    });

    it('hides zero values in compact label and tooltip', () => {
        fixture.componentRef.setInput('accessedMemories', []);
        fixture.componentRef.setInput('createdMemories', [new MemirisMemory('1', 'A', 'A content', [], [], false, false)]);
        fixture.detectChanges();

        const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="memories-indicator-button"]');
        expect(button).toBeTruthy();
        expect(component.usedCount()).toBe(0);
        expect(component.createdCount()).toBe(1);
        // compact label should not mention "used" when usedCount is 0
        expect(component.compactLabel()).not.toContain('used');
        expect(component.tooltipText()).not.toContain('used');
    });

    it('opens popover with details on click', () => {
        fixture.componentRef.setInput('accessedMemories', [new MemirisMemory('1', 'Used 1', 'Used content', [], [], false, false)]);
        fixture.componentRef.setInput('createdMemories', [new MemirisMemory('2', 'Created 1', 'Created content', [], [], false, false)]);
        fixture.detectChanges();

        const button: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="memories-indicator-button"]');
        button.click();
        fixture.detectChanges();

        expect(document.querySelector('.memories-popover')).toBeTruthy();
        expect(document.querySelector('[data-testid="memories-used-section"]')).toBeTruthy();
        expect(document.querySelector('[data-testid="memories-created-section"]')).toBeTruthy();
        expect(document.body.textContent).toContain('Used 1');
        expect(document.body.textContent).toContain('Created 1');
    });
});
