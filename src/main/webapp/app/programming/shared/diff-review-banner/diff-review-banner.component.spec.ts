import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { DiffReviewBannerComponent } from './diff-review-banner.component';

describe('DiffReviewBannerComponent', () => {
    let fixture: ComponentFixture<DiffReviewBannerComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(DiffReviewBannerComponent);
        fixture.detectChanges();
    });

    it('toggles split view and reports the new value to its caller', () => {
        const splitViewChanged = vi.fn();
        fixture.componentInstance.allowSplitView.subscribe(splitViewChanged);
        fixture.componentRef.setInput('allowSplitView', true);
        fixture.detectChanges();
        const splitButton = fixture.nativeElement.querySelector('[data-testid="toggle-split-view"] button');
        expect(splitButton.textContent).toContain('disable');
        splitButton.click();
        fixture.detectChanges();
        expect(fixture.componentInstance.allowSplitView()).toBe(false);
        expect(splitViewChanged).toHaveBeenCalledWith(false);
        expect(splitButton.textContent).toContain('enable');
    });

    it('emits distinct revert and done actions and displays the current line counts', () => {
        const revert = vi.fn();
        const done = vi.fn();
        fixture.componentInstance.revertRequested.subscribe(revert);
        fixture.componentInstance.doneRequested.subscribe(done);
        fixture.componentRef.setInput('addedLineCount', 8);
        fixture.componentRef.setInput('removedLineCount', 2);
        fixture.detectChanges();
        const stats = fixture.debugElement.query(By.directive(GitDiffLineStatComponent)).componentInstance as GitDiffLineStatComponent;
        expect(stats.addedLineCount()).toBe(8);
        expect(stats.removedLineCount()).toBe(2);
        fixture.nativeElement.querySelector('[data-testid="revert-diff"] button').click();
        expect(revert).toHaveBeenCalledOnce();
        expect(done).not.toHaveBeenCalled();
        fixture.nativeElement.querySelector('[data-testid="done-reviewing"] button').click();
        expect(done).toHaveBeenCalledOnce();
    });

    it('blocks revert and done when requested by the caller while retaining split-view control', () => {
        const revert = vi.fn();
        const done = vi.fn();
        fixture.componentInstance.revertRequested.subscribe(revert);
        fixture.componentInstance.doneRequested.subscribe(done);
        fixture.componentRef.setInput('actionsDisabled', true);
        fixture.detectChanges();
        const revertButton = fixture.nativeElement.querySelector('[data-testid="revert-diff"] button') as HTMLButtonElement;
        const doneButton = fixture.nativeElement.querySelector('[data-testid="done-reviewing"] button') as HTMLButtonElement;
        expect(revertButton.disabled).toBe(true);
        expect(doneButton.disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="toggle-split-view"] button').disabled).toBe(false);
        revertButton.click();
        doneButton.click();
        expect(revert).not.toHaveBeenCalled();
        expect(done).not.toHaveBeenCalled();
        fixture.componentRef.setInput('actionsDisabled', false);
        fixture.detectChanges();
        expect(revertButton.disabled).toBe(false);
        expect(doneButton.disabled).toBe(false);
    });
});
