import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AttachmentUnitFormComponent } from './attachment-unit-form.component';

describe('AttachmentUnitFormComponent', () => {
    let component: AttachmentUnitFormComponent;
    let fixture: ComponentFixture<AttachmentUnitFormComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [AttachmentUnitFormComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(AttachmentUnitFormComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
