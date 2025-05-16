import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SegmentedToggleComponent } from './segmented-toggle.component';

describe('SegmentedToggleComponent', () => {
    let component: SegmentedToggleComponent;
    let fixture: ComponentFixture<SegmentedToggleComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SegmentedToggleComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(SegmentedToggleComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
