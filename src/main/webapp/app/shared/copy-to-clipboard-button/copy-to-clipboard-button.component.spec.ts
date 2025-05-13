import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CopyToClipboardButtonComponent } from './copy-to-clipboard-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CopyToClipboardButtonComponent', () => {
    let component: CopyToClipboardButtonComponent;
    let fixture: ComponentFixture<CopyToClipboardButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [MockPipe(ArtemisTranslatePipe), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CopyToClipboardButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('valueToCopyToClipboard', 'This is the text that shall be copied to the clipboard!');
    });

    it('should set wasCopied to true and back to false after 3 seconds on successful copy', () => {
        jest.useFakeTimers();
        component.onCopyFinished(true);
        expect(component.wasCopied).toBeTruthy();
        jest.advanceTimersByTime(3000);
        expect(component.wasCopied).toBeFalsy();
        jest.useRealTimers();
    });

    it('should not change wasCopied if copy is unsuccessful', () => {
        component.onCopyFinished(false);

        // Verify that wasCopied remains false
        expect(component.wasCopied).toBeFalsy();
    });
});
