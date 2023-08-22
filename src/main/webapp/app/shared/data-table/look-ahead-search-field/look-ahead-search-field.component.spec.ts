import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LookAheadSearchFieldComponent } from './look-ahead-search-field.component';

describe('LookAheadSearchFieldComponent', () => {
    let component: LookAheadSearchFieldComponent;
    let fixture: ComponentFixture<LookAheadSearchFieldComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [LookAheadSearchFieldComponent],
        });
        fixture = TestBed.createComponent(LookAheadSearchFieldComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
