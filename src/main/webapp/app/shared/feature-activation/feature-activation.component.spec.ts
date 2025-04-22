import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeatureActivationComponent } from 'app/shared/feature-activation/feature-activation.component';

describe('FeatureEnablePageComponent', () => {
    let component: FeatureActivationComponent;
    let fixture: ComponentFixture<FeatureActivationComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeatureActivationComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(FeatureActivationComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
