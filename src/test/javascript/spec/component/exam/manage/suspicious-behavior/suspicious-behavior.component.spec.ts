import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SuspiciousBehaviorComponent } from 'app/exam/manage/suspicious-behavior/suspicious-behavior.component';

describe('SuspiciousBehaviorComponent', () => {
    let component: SuspiciousBehaviorComponent;
    let fixture: ComponentFixture<SuspiciousBehaviorComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [SuspiciousBehaviorComponent],
        });
        fixture = TestBed.createComponent(SuspiciousBehaviorComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
