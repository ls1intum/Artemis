import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LectureUnitFullscreenLayoutComponent } from './lecture-unit-fullscreen-layout.component';

describe('LectureUnitFullscreenLayoutComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<LectureUnitFullscreenLayoutComponent>;
    let component: LectureUnitFullscreenLayoutComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureUnitFullscreenLayoutComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureUnitFullscreenLayoutComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('isCollapsed', false);
        fixture.detectChanges();
    });

    afterEach(() => {
        document.body.classList.remove('lecture-combined-view-fullscreen-active');
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('emits backdropClick when overlay is clicked in fullscreen mode', () => {
        const backdropClickSpy = vi.fn();
        component.backdropClick.subscribe(backdropClickSpy);

        fixture.componentRef.setInput('isFullscreen', true);
        fixture.detectChanges();

        const overlay = fixture.debugElement.query(By.css('.fullscreen-overlay'));
        overlay.nativeElement.click();

        expect(backdropClickSpy).toHaveBeenCalledTimes(1);
    });

    it('setBackgroundInert keeps sticky navbar interactive in fullscreen', () => {
        const host = fixture.nativeElement as HTMLElement;
        const wrapper = document.createElement('div');
        const navbar = document.createElement('div');
        navbar.className = 'sticky-top-navbar';
        wrapper.append(navbar, host);
        document.body.appendChild(wrapper);

        try {
            component['setBackgroundInert'](true);

            expect(navbar.hasAttribute('inert')).toBe(false);
            expect(navbar.hasAttribute('aria-hidden')).toBe(false);
        } finally {
            component['setBackgroundInert'](false);
            wrapper.remove();
        }
    });
});
