import { GeneralCourseInfoSectionComponent } from '../../../../../../main/webapp/app/overview/course-dashboard/components/general-course-info-section/general-course-info-section.component';

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseDashboardService } from '../../../../../../main/webapp/app/overview/course-dashboard/course-dashboard.service';
import { AlertService } from '../../../../../../main/webapp/app/core/util/alert.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { TranslateService } from '@ngx-translate/core';

describe('GeneralCourseInfoSectionComponent', () => {
    let component: GeneralCourseInfoSectionComponent;
    let fixture: ComponentFixture<GeneralCourseInfoSectionComponent>;

    let courseDashboardService: CourseDashboardService;
    let alertService: AlertService;

    let getGeneralCourseInformationSpy: jest.SpyInstance;

    const courseId = 1;
    const irisEnabled = false;

    const generalInfo = '<p>General course information</p>';

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [GeneralCourseInfoSectionComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                {
                    provide: CourseDashboardService,
                    useValue: {
                        getGeneralCourseInformation: () => jest.fn(),
                    },
                },
            ],
        }).compileComponents();

        courseDashboardService = TestBed.inject(CourseDashboardService);
        alertService = TestBed.inject(AlertService);

        getGeneralCourseInformationSpy = jest.spyOn(courseDashboardService, 'getGeneralCourseInformation').mockResolvedValue(generalInfo);

        fixture = TestBed.createComponent(GeneralCourseInfoSectionComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('irisEnabled', irisEnabled);
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should load general course information', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getGeneralCourseInformationSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.generalCourseInformation()).toBe(generalInfo);
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error message if loading general course information fails', async () => {
        const error = new Error('Failed to load general course information');
        getGeneralCourseInformationSpy.mockRejectedValueOnce(error);
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(getGeneralCourseInformationSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });
});
