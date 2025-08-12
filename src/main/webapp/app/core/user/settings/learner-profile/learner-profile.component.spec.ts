import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { LearnerProfileComponent } from './learner-profile.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { LearnerProfileApiService } from './learner-profile-api.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';

describe('LearnerProfileComponent', () => {
    let component: LearnerProfileComponent;
    let fixture: ComponentFixture<LearnerProfileComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearnerProfileComponent],
            providers: [
                SessionStorageService,
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(LearnerProfileApiService),
                MockProvider(CourseManagementService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LearnerProfileComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render the component', () => {
        const compiled = fixture.nativeElement;
        expect(compiled).toBeTruthy();
    });
});
