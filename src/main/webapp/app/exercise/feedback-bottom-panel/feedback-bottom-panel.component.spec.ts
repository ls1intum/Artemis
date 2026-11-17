import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FeedbackBottomPanelComponent } from 'app/exercise/feedback-bottom-panel/feedback-bottom-panel.component';

@Component({
    imports: [FeedbackBottomPanelComponent],
    template: `
        <jhi-feedback-bottom-panel>
            <span panelHeader data-testid="own-header">Tabs</span>
            <p data-testid="body">Content</p>
        </jhi-feedback-bottom-panel>
    `,
})
class HeaderHostComponent {}

describe('FeedbackBottomPanelComponent', () => {
    let fixture: ComponentFixture<FeedbackBottomPanelComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackBottomPanelComponent, HeaderHostComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackBottomPanelComponent);
        fixture.detectChanges();
    });

    const panel = (): HTMLElement => fixture.nativeElement.querySelector('[data-testid="feedback-bottom-panel"]');
    const content = (): HTMLElement => fixture.nativeElement.querySelector('.feedback-bottom-panel__content');
    const toggle = (): HTMLButtonElement => fixture.nativeElement.querySelector('[data-testid="feedback-bottom-panel-toggle"]');

    it('should show the title, and the count only when there is one', () => {
        expect(fixture.nativeElement.querySelector('.feedback-bottom-panel__heading').textContent).toContain('artemisApp.assessment.generalFeedback');
        expect(fixture.nativeElement.querySelector('[data-testid="feedback-bottom-panel-count"]')).toBeNull();

        fixture.componentRef.setInput('count', 3);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="feedback-bottom-panel-count"]').textContent.trim()).toBe('3');

        // A count of zero is a count, not a missing one
        fixture.componentRef.setInput('count', 0);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="feedback-bottom-panel-count"]').textContent.trim()).toBe('0');
    });

    it('should let a page put its own header in place of the title', () => {
        const hostFixture = TestBed.createComponent(HeaderHostComponent);
        hostFixture.detectChanges();

        expect(hostFixture.nativeElement.querySelector('[data-testid="own-header"]')).not.toBeNull();
        expect(hostFixture.nativeElement.querySelector('.feedback-bottom-panel__heading')).toBeNull();
        expect(hostFixture.nativeElement.querySelector('[data-testid="body"]').textContent).toBe('Content');
    });

    it('should hide the content, not remove it, while collapsed', () => {
        expect(content().hidden).toBe(false);
        expect(toggle().getAttribute('aria-expanded')).toBe('true');

        fixture.componentRef.setInput('expanded', false);
        fixture.detectChanges();

        expect(content().hidden).toBe(true);
        expect(toggle().getAttribute('aria-expanded')).toBe('false');
        expect(panel().classList).toContain('feedback-bottom-panel--collapsed');
    });

    it('should tell the page when the collapse button is clicked, and leave the state to the page', () => {
        const toggled = vi.fn();
        fixture.componentInstance.toggled.subscribe(toggled);

        toggle().click();

        expect(toggled).toHaveBeenCalledOnce();
        // The page has not passed a new state back, so the panel stays as it was
        expect(fixture.componentInstance.expanded()).toBe(true);
    });

    it('should name the collapse button with the key the page gives', () => {
        expect(toggle().getAttribute('aria-label')).toBe('artemisApp.assessment.toggleGeneralFeedback');

        fixture.componentRef.setInput('toggleLabelKey', 'artemisApp.editor.toggleBuildOutputAndFeedback');
        fixture.detectChanges();

        expect(toggle().getAttribute('aria-label')).toBe('artemisApp.editor.toggleBuildOutputAndFeedback');
    });

    it('should fill the height it is given only when asked to', () => {
        expect(panel().classList).not.toContain('feedback-bottom-panel--fill');
        expect(fixture.nativeElement.classList).not.toContain('fill');

        fixture.componentRef.setInput('fill', true);
        fixture.detectChanges();

        expect(panel().classList).toContain('feedback-bottom-panel--fill');
        expect(fixture.nativeElement.classList).toContain('fill');
    });

    it('should carry the test id the page gives', () => {
        fixture.componentRef.setInput('testId', 'code-editor-bottom-panel');
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="code-editor-bottom-panel"]')).not.toBeNull();
    });
});
