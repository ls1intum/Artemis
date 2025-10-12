import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyRingsComponent } from 'app/atlas/shared/competency-rings/competency-rings.component';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('CompetencyRings', () => {
    let fixture: ComponentFixture<CompetencyRingsComponent>;
    let component: CompetencyRingsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule), CompetencyRingsComponent],
            declarations: [MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CompetencyRingsComponent);
                component = fixture.componentInstance;

                fixture.componentRef.setInput('progress', 110);
                fixture.componentRef.setInput('mastery', -10);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should calculate percentage values', () => {
        fixture.detectChanges();

        expect(component.progressPercentage).toBe(100);
        expect(component.masteryPercentage).toBe(0);
    });

    it('should restrict number to percentage range', () => {
        expect(component.percentageRange(110)).toBe(100);
        expect(component.percentageRange(50)).toBe(50);
        expect(component.percentageRange(-10)).toBe(0);
    });

    it('should visualize using progress bars', () => {
        fixture.detectChanges();

        const masteryRing = fixture.debugElement.query(By.css('.mastery-ring .progressbar'));
        expect(masteryRing).toBeTruthy();
        expect(masteryRing.styles.opacity).toBe('0');

        const progressRing = fixture.debugElement.query(By.css('.progress-ring .progressbar'));
        expect(progressRing).toBeTruthy();
        expect(progressRing.styles.opacity).toBe('1');
        expect(progressRing.nativeElement.getAttribute('stroke-dasharray')).toBe('100, 100');
    });
});
