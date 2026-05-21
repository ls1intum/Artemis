import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

// Mock the fullscreen utility before importing the component
vi.mock('app/shared/util/fullscreen.util', () => ({
    enterFullscreen: vi.fn(),
    exitFullscreen: vi.fn(),
    isFullScreen: vi.fn(),
}));

import { FullscreenComponent } from './fullscreen.component';
import * as fullscreenUtil from 'app/shared/util/fullscreen.util';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('FullscreenComponent', () => {
    setupTestBed({ zoneless: true });

    let component: FullscreenComponent;
    let fixture: ComponentFixture<FullscreenComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FullscreenComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(FullscreenComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('toggleFullscreen', () => {
        it('should call exitFullscreen when already in fullscreen', () => {
            vi.mocked(fullscreenUtil.isFullScreen).mockReturnValue(true);

            component.toggleFullscreen();

            expect(fullscreenUtil.exitFullscreen).toHaveBeenCalledOnce();
            expect(fullscreenUtil.enterFullscreen).not.toHaveBeenCalled();
        });

        it('should call enterFullscreen with native element when not in fullscreen', () => {
            vi.mocked(fullscreenUtil.isFullScreen).mockReturnValue(false);

            component.toggleFullscreen();

            expect(fullscreenUtil.enterFullscreen).toHaveBeenCalledOnce();
            expect(fullscreenUtil.enterFullscreen).toHaveBeenCalledWith(fixture.nativeElement);
            expect(fullscreenUtil.exitFullscreen).not.toHaveBeenCalled();
        });
    });

    describe('isFullScreen', () => {
        it('should return true when in fullscreen mode', () => {
            vi.mocked(fullscreenUtil.isFullScreen).mockReturnValue(true);

            expect(component.isFullScreen()).toBe(true);
        });

        it('should return false when not in fullscreen mode', () => {
            vi.mocked(fullscreenUtil.isFullScreen).mockReturnValue(false);

            expect(component.isFullScreen()).toBe(false);
        });
    });

    describe('input bindings', () => {
        it('should have default position of top-right', () => {
            expect(component.position()).toBe('top-right');
        });

        it('should have default mode of extended', () => {
            expect(component.mode()).toBe('extended');
        });

        it('should accept top-left position', () => {
            fixture.componentRef.setInput('position', 'top-left');
            fixture.detectChanges();

            expect(component.position()).toBe('top-left');
        });

        it('should accept bottom-left position', () => {
            fixture.componentRef.setInput('position', 'bottom-left');
            fixture.detectChanges();

            expect(component.position()).toBe('bottom-left');
        });

        it('should accept bottom-right position', () => {
            fixture.componentRef.setInput('position', 'bottom-right');
            fixture.detectChanges();

            expect(component.position()).toBe('bottom-right');
        });

        it('should accept compact mode', () => {
            fixture.componentRef.setInput('mode', 'compact');
            fixture.detectChanges();

            expect(component.mode()).toBe('compact');
        });
    });

    describe('template rendering', () => {
        it('should render button with correct position class', () => {
            fixture.componentRef.setInput('position', 'top-left');
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('button'));
            expect(button.nativeElement.classList.contains('top-left')).toBe(true);
        });

        it('should render button with btn-primary class in extended mode', () => {
            fixture.componentRef.setInput('mode', 'extended');
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('button'));
            expect(button.nativeElement.classList.contains('btn-primary')).toBe(true);
        });

        it('should render button with btn-sm class in compact mode', () => {
            fixture.componentRef.setInput('mode', 'compact');
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('button'));
            expect(button.nativeElement.classList.contains('btn-sm')).toBe(true);
        });

        it('should render fa-icon in compact mode', () => {
            fixture.componentRef.setInput('mode', 'compact');
            fixture.detectChanges();

            const icon = fixture.debugElement.query(By.css('fa-icon'));
            expect(icon).not.toBeNull();
        });

        it('should render span with text in extended mode', () => {
            fixture.componentRef.setInput('mode', 'extended');
            fixture.detectChanges();

            const span = fixture.debugElement.query(By.css('button span'));
            expect(span).not.toBeNull();
        });

        it('should call toggleFullscreen when button is clicked', () => {
            const toggleSpy = vi.spyOn(component, 'toggleFullscreen');

            const button = fixture.debugElement.query(By.css('button'));
            button.nativeElement.click();

            expect(toggleSpy).toHaveBeenCalledOnce();
        });

        it('should have ngbTooltip directive on button', () => {
            const button = fixture.debugElement.query(By.css('button'));
            // NgbTooltip directive is applied via [ngbTooltip] binding in template
            // Check that the directive instance exists on the debug element
            const ngbTooltipDirective = button.injector.get(NgbTooltip, null);
            expect(ngbTooltipDirective).not.toBeNull();
        });
    });

    describe('faCompress icon', () => {
        it('should have faCompress icon defined', () => {
            expect(component.faCompress).toBeDefined();
            expect(component.faCompress.iconName).toBe('compress');
        });
    });
});
