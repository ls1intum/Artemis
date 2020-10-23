import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CreateAttachmentUnitComponent } from './create-attachment-unit.component';

describe('CreateAttachmentUnitComponent', () => {
    let component: CreateAttachmentUnitComponent;
    let fixture: ComponentFixture<CreateAttachmentUnitComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CreateAttachmentUnitComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(CreateAttachmentUnitComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
