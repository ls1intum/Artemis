import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CopyToClipboardButtonComponent } from 'app/shared/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('JhiCopyIconButtonComponent', () => {
    setupTestBed({ zoneless: true });
    let component: CopyToClipboardButtonComponent;
    let fixture: ComponentFixture<CopyToClipboardButtonComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ClipboardModule, MockDirective(NgbCollapse), MockDirective(NgbTooltip)],
            declarations: [CopyToClipboardButtonComponent, MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(CopyToClipboardButtonComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('copyText', 'text');
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CopyToClipboardButtonComponent).not.toBeNull();
    });

    it('should not be hidden with text', () => {
        fixture.detectChanges();

        const copyButton = fixture.debugElement.nativeElement.querySelector('#copyButton');
        expect(copyButton).toBeTruthy();
    });

    it('should be hidden if text is empty', () => {
        fixture.componentRef.setInput('copyText', '');
        fixture.detectChanges();

        const copyButton = fixture.debugElement.nativeElement.querySelector('#copyButton');
        expect(copyButton).toBeNull();
    });

    it('should show it was copied on click', () => {
        vi.useFakeTimers();

        fixture.detectChanges();

        const copyButton = fixture.debugElement.nativeElement.querySelector('#copyButton');
        copyButton.click();

        expect(component.wasCopied()).toBeTruthy();

        vi.advanceTimersByTime(3000);
        expect(component.wasCopied()).toBeFalsy();
    });
});
