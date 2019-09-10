import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisAssessmentSharedModule, AssessmentComplaintAlertComponent, AssessmentHeaderComponent, AssessmentLayoutComponent } from 'app/assessment-shared';
import { ArtemisSharedModule, JhiAlertComponent } from 'app/shared';
import { ArtemisTestModule } from '../../test.module';
import { ComplaintsForTutorComponent } from 'app/complaints-for-tutor';
import { JhiLanguageHelper } from 'app/core';
import { Complaint } from 'app/entities/complaint';

describe('AssessmentLayoutComponent', () => {
    let component: AssessmentLayoutComponent;
    let fixture: ComponentFixture<AssessmentLayoutComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, ArtemisAssessmentSharedModule],
            declarations: [],
            providers: [JhiAlertService, JhiLanguageHelper],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(AssessmentLayoutComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should include jhi-alert', () => {
        const jhiAlertComponent = fixture.debugElement.query(By.directive(JhiAlertComponent));
        expect(jhiAlertComponent).toBeTruthy();
    });

    it('should include jhi-assessment-header', () => {
        const assessmentHeaderComponent = fixture.debugElement.query(By.directive(AssessmentHeaderComponent));
        expect(assessmentHeaderComponent).toBeTruthy();
    });

    it('should include jhi-assessment-complaint-alert', () => {
        const assessmentComplaintAlertComponent = fixture.debugElement.query(By.directive(AssessmentComplaintAlertComponent));
        expect(assessmentComplaintAlertComponent).toBeTruthy();
    });

    it('should include jhi-complaints-for-tutor-form', () => {
        let complaintsForTutorComponent = fixture.debugElement.query(By.directive(ComplaintsForTutorComponent));
        expect(complaintsForTutorComponent).toBeFalsy();

        component.complaint = new Complaint();
        fixture.detectChanges();
        complaintsForTutorComponent = fixture.debugElement.query(By.directive(ComplaintsForTutorComponent));
        expect(complaintsForTutorComponent).toBeTruthy();
    });

    it('should show or hide a back button', () => {
        component.showBackButton = true;
        fixture.detectChanges();
        let backButton = fixture.debugElement.query(By.css('fa-icon.back-button'));
        expect(backButton).toBeTruthy();

        component.showBackButton = false;
        fixture.detectChanges();
        backButton = fixture.debugElement.query(By.css('fa-icon.back-button'));
        expect(backButton).toBeFalsy();
    });
});
