import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PdfPreviewComponent } from './pdf-preview.component';

describe('PdfPreviewComponent', () => {
    let component: PdfPreviewComponent;
    let fixture: ComponentFixture<PdfPreviewComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PdfPreviewComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfPreviewComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
