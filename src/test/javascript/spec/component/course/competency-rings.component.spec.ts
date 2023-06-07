import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyRingsComponent } from 'app/course/competencies/competency-rings/competency-rings.component';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('CompetencyRings', () => {
    let fixture: ComponentFixture<CompetencyRingsComponent>;
    let component: CompetencyRingsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgbTooltipModule)],
            declarations: [CompetencyRingsComponent, MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyRingsComponent);
                component = fixture.componentInstance;

                component.progress = 110;
                component.confidence = 50;
                component.mastery = -10;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should calculate percentage values', () => {
        fixture.detectChanges();

        expect(component.progressPercentage).toBe(100);
        expect(component.confidencePercentage).toBe(50);
        expect(component.masteryPercentage).toBe(0);
    });

    it('should restrict number to percentage range', () => {
        expect(component.percentageRange(110)).toBe(100);
        expect(component.percentageRange(50)).toBe(50);
        expect(component.percentageRange(-10)).toBe(0);
    });

    it('should visualize using progress bars', () => {
        fixture.detectChanges();

        const masteryRing = fixture.debugElement.query(By.css('.ring1 .progressbar'));
        expect(masteryRing).toBeTruthy();
        expect(masteryRing.styles.opacity).toBe('0');

        const confidenceRing = fixture.debugElement.query(By.css('.ring2 .progressbar'));
        expect(confidenceRing).toBeTruthy();
        expect(confidenceRing.styles.opacity).toBe('1');
        expect(confidenceRing.nativeElement.getAttribute('stroke-dasharray')).toBe('50, 100');

        const progressRing = fixture.debugElement.query(By.css('.ring3 .progressbar'));
        expect(progressRing).toBeTruthy();
        expect(progressRing.styles.opacity).toBe('1');
        expect(progressRing.nativeElement.getAttribute('stroke-dasharray')).toBe('100, 100');
    });
});
