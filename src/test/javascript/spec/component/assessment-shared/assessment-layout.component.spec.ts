import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ArtemisTestModule } from '../../test.module';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { AssessmentHeaderComponent } from 'app/assessment/assessment-header/assessment-header.component';
import { AssessmentComplaintAlertComponent } from 'app/assessment/assessment-complaint-alert/assessment-complaint-alert.component';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { Complaint } from 'app/entities/complaint.model';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { RouterTestingModule } from '@angular/router/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { NgbAlert, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('AssessmentLayoutComponent', () => {
    let component: AssessmentLayoutComponent;
    let fixture: ComponentFixture<AssessmentLayoutComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [
                AssessmentLayoutComponent,
                AssessmentHeaderComponent,
                MockComponent(ComplaintsForTutorComponent),
                MockComponent(AssessmentComplaintAlertComponent),
                MockComponent(AlertComponent),
                MockComponent(NgbAlert),
                MockComponent(AssessmentWarningComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(NgbTooltip),
            ],
            providers: [JhiLanguageHelper, { provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentLayoutComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should include jhi-alert', () => {
        const jhiAlertComponent = fixture.debugElement.query(By.directive(AlertComponent));
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
});
