import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MemirisGraphFiltersComponent } from './memiris-graph-filters.component';

describe('MemirisGraphFiltersComponent', () => {
    let component: MemirisGraphFiltersComponent;
    let fixture: ComponentFixture<MemirisGraphFiltersComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MemirisGraphFiltersComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(MemirisGraphFiltersComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
