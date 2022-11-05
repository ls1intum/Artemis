import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CopyIconButtonComponent } from 'app/shared/components/copy-icon-button/copy-icon-button.component';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { By } from '@angular/platform-browser';

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

    it('should initialize', fakeAsync(() => {
        component.copyText = 'text';
        fixture.detectChanges();
        expect(CopyIconButtonComponent).not.toBeNull();
    }));

    it('should not be hidden with text', fakeAsync(() => {
        component.copyText = 'text';
        expect(fixture.debugElement.query(By.css('.btn btn-sm'))).toBeTruthy();
    }));

    it('should be hidden if text is empty', fakeAsync(() => {
        component.copyText = '';
        expect(fixture.debugElement.query(By.css('.btn btn-sm'))).toBeNull();
    }));

    it('should show it was copied on click', fakeAsync(() => {
        component.copyText = 'text';

        fixture.debugElement.query(By.css('.btn btn-sm')).nativeElement.click();
        tick();
        expect(component.wasCopied).toBeTrue();
    }));
});
