import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisCitationNavigationComponent } from './iris-citation-navigation.component';

describe('IrisCitationNavigationComponent', () => {
    let fixture: ComponentFixture<IrisCitationNavigationComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(IrisCitationNavigationComponent);
        fixture.componentRef.setInput('summaryCount', 3);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
    });

    const button = (direction: 'previous' | 'next'): HTMLButtonElement => fixture.nativeElement.querySelector(`button[aria-label="artemisApp.iris.citation.${direction}"]`);

    it('names both native buttons and announces the current summary', () => {
        expect(button('previous')).not.toBeNull();
        expect(button('next')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[aria-live="polite"]').textContent).toBe('1 / 3');
    });

    it('wraps in both directions and emits the selected index', () => {
        const selected = vi.spyOn(fixture.componentInstance.indexChanged, 'emit');
        button('previous').click();
        fixture.detectChanges();
        expect(selected).toHaveBeenLastCalledWith(2);
        expect(fixture.nativeElement.textContent).toContain('3 / 3');

        button('next').click();
        fixture.detectChanges();
        expect(selected).toHaveBeenLastCalledWith(0);
        expect(fixture.nativeElement.textContent).toContain('1 / 3');
    });

    it('keeps keyboard focus on the control after changing summaries', () => {
        const next = button('next');
        next.focus();
        next.click();
        fixture.detectChanges();
        expect(document.activeElement).toBe(next);
        expect(fixture.nativeElement.textContent).toContain('2 / 3');
    });

    it('keeps navigation clicks from reaching the surrounding message', () => {
        const surroundingClick = vi.fn();
        fixture.nativeElement.addEventListener('click', surroundingClick);
        button('next').click();
        expect(surroundingClick).not.toHaveBeenCalled();
    });

    it('shows the package tooltip on keyboard focus', async () => {
        button('next').focus();
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(document.querySelector('[role="tooltip"]')?.textContent).toContain('artemisApp.iris.citation.next');
        });
    });
});
