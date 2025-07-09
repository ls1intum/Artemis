import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MemirisGraphSettingsComponent } from './memiris-graph-settings.component';

describe('MemirisGraphFiltersComponent', () => {
    let component: MemirisGraphSettingsComponent;
    let fixture: ComponentFixture<MemirisGraphSettingsComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MemirisGraphSettingsComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(MemirisGraphSettingsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
