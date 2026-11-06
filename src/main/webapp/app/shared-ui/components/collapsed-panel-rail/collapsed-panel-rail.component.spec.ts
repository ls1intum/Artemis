import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { faCommentDots, faFolderOpen, faTerminal } from '@fortawesome/free-solid-svg-icons';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CollapsedPanelRailComponent } from 'app/shared-ui/components/collapsed-panel-rail/collapsed-panel-rail.component';

describe('CollapsedPanelRailComponent', () => {
    let fixture: ComponentFixture<CollapsedPanelRailComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(CollapsedPanelRailComponent);
        fixture.componentRef.setInput('icon', faFolderOpen);
        fixture.componentRef.setInput('labelKey', 'artemisApp.editor.fileBrowser.fileBrowser');
        fixture.detectChanges();
    });

    const button = () => fixture.nativeElement.querySelector('button') as HTMLButtonElement;

    it('should show only the icon, named by the label for assistive technology', () => {
        expect(button().textContent!.trim()).toBe('');
        expect(button().querySelector('fa-icon')).not.toBeNull();
        expect(button().getAttribute('aria-label')).toBe('artemisApp.editor.fileBrowser.fileBrowser');
        expect(button().getAttribute('aria-expanded')).toBe('false');
    });

    it('should ask for the panel to open when the icon is clicked', () => {
        const expand = vi.fn();
        fixture.componentInstance.expand.subscribe(expand);

        button().click();

        expect(expand).toHaveBeenCalledOnce();
    });

    it('should lie flat for a panel that collapses downwards', () => {
        expect(fixture.nativeElement.classList).not.toContain('collapsed-panel-rail--horizontal');

        fixture.componentRef.setInput('orientation', 'horizontal');
        fixture.detectChanges();

        expect(fixture.nativeElement.classList).toContain('collapsed-panel-rail--horizontal');
    });

    it('should show an icon for each part of a panel with several, and tell which one was clicked before opening', () => {
        fixture.componentRef.setInput('items', [
            { id: 'buildOutput', icon: faTerminal, labelKey: 'artemisApp.editor.buildOutput' },
            { id: 'feedback', icon: faCommentDots, labelKey: 'artemisApp.assessment.generalFeedback' },
        ]);
        fixture.componentRef.setInput('testId', 'rail');
        fixture.detectChanges();
        const calls: string[] = [];
        fixture.componentInstance.itemSelected.subscribe((id) => calls.push(`select:${id}`));
        fixture.componentInstance.expand.subscribe(() => calls.push('expand'));

        const buttons = fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>;
        expect(buttons).toHaveLength(2);
        expect(buttons[1].getAttribute('data-testid')).toBe('rail-feedback');

        buttons[1].click();

        expect(calls).toEqual(['select:feedback', 'expand']);
    });
});
