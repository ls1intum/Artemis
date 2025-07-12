import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SliceNavigatorComponent } from './slice-navigator.component';

describe('SliceNavigatorComponent', () => {
    let component: SliceNavigatorComponent;
    let fixture: ComponentFixture<SliceNavigatorComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SliceNavigatorComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(SliceNavigatorComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
