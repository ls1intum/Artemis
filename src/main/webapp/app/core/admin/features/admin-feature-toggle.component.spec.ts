import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminFeatureToggleComponent } from 'app/core/admin/features/admin-feature-toggle.component';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';

describe('AdminFeatureToggleComponentTest', () => {
    let fixture: ComponentFixture<AdminFeatureToggleComponent>;
    let comp: AdminFeatureToggleComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: FeatureToggleService, useClass: MockFeatureToggleService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AdminFeatureToggleComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('constructor should not load toggles', () => {
        expect(comp.availableToggles).toHaveLength(0);
    });

    it('onInit test if features mapped successfully', () => {
        expect(comp.availableToggles).toHaveLength(0);
        comp.ngOnInit();
        expect(comp.availableToggles).toHaveLength(11);
    });

    it('onFeatureToggle test if feature disabled on toggle', () => {
        const event = {
            isTrusted: true,
        };

        comp.ngOnInit();
        comp.onFeatureToggle(event, comp.availableToggles[0]);

        expect(comp.availableToggles[0].isActive).toBeFalse();
    });
});
