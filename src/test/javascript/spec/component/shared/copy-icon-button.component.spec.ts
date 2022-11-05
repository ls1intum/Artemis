import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CopyIconButtonComponent } from 'app/shared/components/copy-icon-button/copy-icon-button.component';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { ClipboardModule } from '@angular/cdk/clipboard';

describe('JhiCopyIconButtonComponent', () => {
    let component: CopyIconButtonComponent;
    let fixture: ComponentFixture<CopyIconButtonComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ClipboardModule, NgbPopoverModule],
            declarations: [CopyIconButtonComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [],
        }).compileComponents();

        fixture = TestBed.createComponent(CopyIconButtonComponent);
        component = fixture.componentInstance;
    });

    it('should initialize', () => {
        component.copyText = 'text';
        fixture.detectChanges();
        expect(CopyIconButtonComponent).not.toBeNull();
    });

    it('should not be hidden with text', () => {
        component.copyText = 'text';
        fixture.detectChanges();

        const copyButton = fixture.debugElement.nativeElement.querySelector('#copyButton');
        expect(copyButton).toBeTruthy();
    });

    it('should be hidden if text is empty', () => {
        component.copyText = '';
        fixture.detectChanges();

        const copyButton = fixture.debugElement.nativeElement.querySelector('#copyButton');
        expect(copyButton).toBeNull();
    });

    it('should show it was copied on click', () => {
        component.copyText = 'text';
        fixture.detectChanges();

        const copyButton = fixture.debugElement.nativeElement.querySelector('#copyButton');
        copyButton.click();
        fixture.whenStable().then(() => {
            expect(component.wasCopied).toBeTrue();

            jest.advanceTimersByTime(3000);
            expect(component.wasCopied).toBeFalse();
        });
    });
});
