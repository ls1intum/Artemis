import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumUiButtonDirective } from '@tumaet/ui-angular';

vi.mock('app/foundation/util/fullscreen.util', () => ({
    enterFullscreen: vi.fn(),
    exitFullscreen: vi.fn(),
    isFullScreen: vi.fn(),
}));

import { FullscreenComponent } from './fullscreen.component';
import * as fullscreenUtil from 'app/foundation/util/fullscreen.util';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('FullscreenComponent', () => {
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

    describe('input bindings', () => {
        it('should show the fullscreen button by default and allow callers to hide it', () => {
            expect(fixture.debugElement.query(By.css('button'))).not.toBeNull();

            fixture.componentRef.setInput('showButton', false);
            fixture.detectChanges();

            expect(fixture.debugElement.query(By.css('button'))).toBeNull();
        });
    });

    describe('template rendering', () => {
        it('should render button with correct position class', () => {
            fixture.componentRef.setInput('position', 'top-left');
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('button'));
            expect(button.nativeElement.classList.contains('top-left')).toBe(true);
        });

        it('should use the primary TUM UI button treatment in extended mode', () => {
            fixture.componentRef.setInput('mode', 'extended');
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('button'));
            const buttonDirective = button.injector.get(TumUiButtonDirective);
            expect(buttonDirective.severity()).toBe('primary');
            expect(buttonDirective.variant()).toBe('solid');
        });

        it('should use the compact secondary TUM UI button treatment in compact mode', () => {
            fixture.componentRef.setInput('mode', 'compact');
            fixture.detectChanges();

            const button = fixture.debugElement.query(By.css('button'));
            const buttonDirective = button.injector.get(TumUiButtonDirective);
            expect(buttonDirective.severity()).toBe('secondary');
            expect(buttonDirective.variant()).toBe('text');
            expect(buttonDirective.size()).toBe('small');
            expect(button.nativeElement.getAttribute('aria-label')).toBeTruthy();
        });

        it('should call toggleFullscreen when button is clicked', () => {
            const toggleSpy = vi.spyOn(component, 'toggleFullscreen');

            const button = fixture.debugElement.query(By.css('button'));
            button.nativeElement.click();

            expect(toggleSpy).toHaveBeenCalledOnce();
        });
    });
});
