import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FeatureEnablePageComponent } from './feature-enable-page.component';

describe('FeatureEnablePageComponent', () => {
    let component: FeatureEnablePageComponent;
    let fixture: ComponentFixture<FeatureEnablePageComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeatureEnablePageComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(FeatureEnablePageComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
