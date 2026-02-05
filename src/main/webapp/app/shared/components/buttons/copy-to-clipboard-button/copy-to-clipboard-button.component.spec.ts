import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CopyToClipboardButtonComponent } from 'app/shared/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';

describe('JhiCopyIconButtonComponent', () => {
    setupTestBed({ zoneless: true });
    let component: CopyToClipboardButtonComponent;
    let fixture: ComponentFixture<CopyToClipboardButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ClipboardModule, MockDirective(NgbCollapse), MockDirective(NgbTooltip), CopyToClipboardButtonComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(TranslateService)],
        }).compileComponents();

        fixture = TestBed.createComponent(CopyToClipboardButtonComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('copyText', 'text');
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should not be hidden with text', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const copyButton = fixture.debugElement.nativeElement.querySelector('#copyButton');
        expect(copyButton).toBeTruthy();
    });

    it('should be hidden if text is empty', async () => {
        fixture.componentRef.setInput('copyText', '');
        fixture.detectChanges();
        await fixture.whenStable();

        const copyButton = fixture.debugElement.nativeElement.querySelector('#copyButton');
        expect(copyButton).toBeNull();
    });

    it('should show it was copied on click', async () => {
        vi.useFakeTimers();
        fixture.detectChanges();
        const copyButton = fixture.debugElement.nativeElement.querySelector('#copyButton');
        copyButton.click();

        expect(component.wasCopied()).toBe(true);
        vi.advanceTimersByTime(3000);
        fixture.detectChanges();
        expect(component.wasCopied()).toBe(false);
        vi.useRealTimers();
    });
});
