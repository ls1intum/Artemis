import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditAttachmentUnitComponent } from './edit-attachment-unit.component';

describe('EditAttachmentUnitComponent', () => {
    let component: EditAttachmentUnitComponent;
    let fixture: ComponentFixture<EditAttachmentUnitComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [EditAttachmentUnitComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(EditAttachmentUnitComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
