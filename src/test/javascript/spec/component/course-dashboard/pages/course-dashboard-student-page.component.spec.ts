import { CourseDashboardStudentPageComponent } from '../../../../../../main/webapp/app/overview/course-dashboard/pages/course-dashboard-student-page/course-dashboard-student-page.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs/internal/observable/of';
import { ProfileService } from '../../../../../../main/webapp/app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from '../../../../../../main/webapp/app/iris/settings/shared/iris-settings.service';
import { PROFILE_IRIS } from '../../../../../../main/webapp/app/app.constants';
import { CoursePerformanceSectionComponent } from '../../../../../../main/webapp/app/overview/course-dashboard/components/course-performance-section/course-performance-section.component';
import { CourseChatbotComponent } from '../../../../../../main/webapp/app/iris/course-chatbot/course-chatbot.component';
import { GeneralCourseInfoSectionComponent } from '../../../../../../main/webapp/app/overview/course-dashboard/components/general-course-info-section/general-course-info-section.component';
import { Component, input } from '@angular/core';

describe('CourseDashboardStudentPageComponent', () => {
    let component: CourseDashboardStudentPageComponent;
    let fixture: ComponentFixture<CourseDashboardStudentPageComponent>;
    let irisSettingsService: IrisSettingsService;

    const courseId = 1;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseDashboardStudentPageComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({
                                courseId: courseId,
                            }),
                        },
                    },
                },
                {
                    provide: ProfileService,
                    useValue: {
                        getProfileInfo: () => {
                            return of({ activeProfiles: [PROFILE_IRIS] });
                        },
                    },
                },
                {
                    provide: IrisSettingsService,
                    useValue: {
                        getCombinedCourseSettings: () => {
                            return of({
                                irisChatSettings: {
                                    enabled: false,
                                },
                            });
                        },
                    },
                },
            ],
        })
            .overrideComponent(CourseDashboardStudentPageComponent, {
                remove: {
                    imports: [CoursePerformanceSectionComponent, CourseChatbotComponent, GeneralCourseInfoSectionComponent],
                },
                add: {
                    imports: [CoursePerformanceSectionStubComponent, GeneralCourseInfoSectionStubComponent, CourseChatbotStubComponent],
                },
            })
            .compileComponents();

        irisSettingsService = TestBed.inject(IrisSettingsService);

        fixture = TestBed.createComponent(CourseDashboardStudentPageComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(component).toBeDefined();
        expect(component.courseId()).toBe(courseId);
    });

    it('should load iris settings if iris is enabled', async () => {
        const irisSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings');

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.irisEnabled()).toBeFalse();
        expect(irisSettingsSpy).toHaveBeenCalledExactlyOnceWith(courseId);
    });
});

// Stub components
@Component({
    selector: 'jhi-general-course-info-section',
    template: '',
    standalone: true,
})
class GeneralCourseInfoSectionStubComponent {
    readonly courseId = input.required<number>();
    readonly irisEnabled = input.required<boolean>();
}

@Component({
    selector: 'jhi-course-performance-section',
    template: '',
    standalone: true,
})
class CoursePerformanceSectionStubComponent {
    readonly courseId = input.required<number>();
}

@Component({
    selector: 'jhi-course-chatbot',
    template: '',
    standalone: true,
})
class CourseChatbotStubComponent {
    readonly courseId = input.required<number>();
}
