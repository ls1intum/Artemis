import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyRingsComponent } from 'app/atlas/shared/competency-rings/competency-rings.component';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Component } from '@angular/core';
import { getComponentInstanceFromFixture } from 'test/helpers/utils/general-test.utils';

@Component({
    template: '<jhi-competency-rings [progress]="progress" [mastery]="mastery" [playAnimation]="playAnimation" [hideTooltip]="hideTooltip" [hideProgress]="hideProgress"/>',
    imports: [CompetencyRingsComponent],
})
class WrapperComponent {
    progress?: number;
    mastery?: number;
    playAnimation?: boolean;
    hideTooltip?: boolean;
    hideProgress?: boolean;
}

describe('CompetencyRings', () => {
    let fixture: ComponentFixture<WrapperComponent>;
    let component: WrapperComponent;
    let competencyRingsComponent: CompetencyRingsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [WrapperComponent, MockModule(NgbTooltipModule)],
            declarations: [CompetencyRingsComponent, MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(WrapperComponent);
                component = fixture.componentInstance;
                competencyRingsComponent = getComponentInstanceFromFixture(fixture, CompetencyRingsComponent);

                component.progress = 110;
                component.mastery = -10;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should calculate percentage values', () => {
        fixture.detectChanges();

        expect(competencyRingsComponent.progressPercentage).toBe(100);
        expect(competencyRingsComponent.masteryPercentage).toBe(0);
    });

    it('should restrict number to percentage range', () => {
        expect(competencyRingsComponent.percentageRange(110)).toBe(100);
        expect(competencyRingsComponent.percentageRange(50)).toBe(50);
        expect(competencyRingsComponent.percentageRange(-10)).toBe(0);
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
