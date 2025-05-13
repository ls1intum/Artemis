import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CopyIconButtonComponent } from 'app/shared/components/copy-icon-button/copy-icon-button.component';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

describe('JhiCopyIconButtonComponent', () => {
    let component: CopyIconButtonComponent;
    let fixture: ComponentFixture<CopyIconButtonComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ClipboardModule, MockDirective(NgbCollapse), MockDirective(NgbTooltip)],
            declarations: [CopyIconButtonComponent, MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();

        fixture = TestBed.createComponent(CopyIconButtonComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('copyText', 'text');
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CopyIconButtonComponent).not.toBeNull();
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
        jest.useFakeTimers();

        fixture.detectChanges();

        const copyButton = fixture.debugElement.nativeElement.querySelector('#copyButton');
        copyButton.click();

        expect(component.wasCopied()).toBeTrue();

        jest.advanceTimersByTime(3000);
        expect(component.wasCopied()).toBeFalse();
    });
});
