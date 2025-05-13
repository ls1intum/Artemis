import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CopyToClipboardButtonComponent } from './copy-to-clipboard-button.component';

describe('CopyPasteButtonComponent', () => {
    let component: CopyToClipboardButtonComponent;
    let fixture: ComponentFixture<CopyToClipboardButtonComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CopyToClipboardButtonComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CopyToClipboardButtonComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
