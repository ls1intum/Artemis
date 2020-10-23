import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AttachmentUnitComponent } from './attachment-unit.component';

describe('AttachmentUnitComponent', () => {
    let component: AttachmentUnitComponent;
    let fixture: ComponentFixture<AttachmentUnitComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [AttachmentUnitComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(AttachmentUnitComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
