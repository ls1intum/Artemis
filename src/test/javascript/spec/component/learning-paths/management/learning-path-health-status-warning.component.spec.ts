import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { LearningPathHealthStatusWarningComponent } from 'app/course/learning-paths/learning-path-management/learning-path-health-status-warning.component';
import { HealthStatus } from 'app/entities/competency/learning-path-health.model';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';

describe('LearningPathHealthStatusWarningComponent', () => {
    let fixture: ComponentFixture<LearningPathHealthStatusWarningComponent>;
    let comp: LearningPathHealthStatusWarningComponent;
    let getWarningTitleStub: jest.SpyInstance;
    let getWarningBodyStub: jest.SpyInstance;
    let getWarningActionStub: jest.SpyInstance;
    let getWarningHintStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbTooltipMocksModule],
            declarations: [LearningPathHealthStatusWarningComponent, MockPipe(ArtemisTranslatePipe), MockHasAnyAuthorityDirective],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathHealthStatusWarningComponent);
                comp = fixture.componentInstance;
                getWarningTitleStub = jest.spyOn(comp, 'getWarningTitle');
                getWarningBodyStub = jest.spyOn(comp, 'getWarningBody');
                getWarningActionStub = jest.spyOn(comp, 'getWarningAction');
                getWarningHintStub = jest.spyOn(comp, 'getWarningHint');
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(fixture).toBeTruthy();
        expect(comp).toBeTruthy();
    });

    it.each([HealthStatus.MISSING, HealthStatus.NO_COMPETENCIES, HealthStatus.NO_RELATIONS])('should load title', (status: HealthStatus) => {
        comp.status = status;
        fixture.detectChanges();
        expect(getWarningTitleStub).toHaveBeenCalledWith(status);
    });

    it.each([HealthStatus.MISSING, HealthStatus.NO_COMPETENCIES, HealthStatus.NO_RELATIONS])('should load body', (status: HealthStatus) => {
        comp.status = status;
        fixture.detectChanges();
        expect(getWarningBodyStub).toHaveBeenCalledWith(status);
    });

    it.each([HealthStatus.MISSING, HealthStatus.NO_COMPETENCIES, HealthStatus.NO_RELATIONS])('should load action', (status: HealthStatus) => {
        comp.status = status;
        fixture.detectChanges();
        expect(getWarningActionStub).toHaveBeenCalledWith(status);
    });

    it.each([HealthStatus.MISSING, HealthStatus.NO_COMPETENCIES, HealthStatus.NO_RELATIONS])('should load hint', (status: HealthStatus) => {
        comp.status = status;
        fixture.detectChanges();
        expect(getWarningHintStub).toHaveBeenCalledWith(status);
    });
});
