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
                        updateGeneralCourseInformation: () => jest.fn(),
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

    it('should load general course information', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getGeneralCourseInformationSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.generalCourseInformation()).toBe(generalInfo);
    });

    it('should toggleEditMode', () => {
        component.toggleEditMode();
        expect(component.isEditMode()).toBeTrue();
        component.toggleEditMode();
        expect(component.isEditMode()).toBeFalse();
    });

    it('should update general information', async () => {
        component.updateGeneralInformation('New general course information');
        expect(component.generalCourseInformation()).toBe('New general course information');
    });

    it('should save general information', async () => {
        component.generalCourseInformation.set('New general course information');
        const updateGeneralCourseInformationSpy = jest.spyOn(courseDashboardService, 'updateGeneralCourseInformation').mockResolvedValue();

        await component.saveGeneralInformation();

        expect(updateGeneralCourseInformationSpy).toHaveBeenCalledExactlyOnceWith(courseId, 'New general course information');
    });

    it('should set isLoading correctly on updating general information', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        await component.saveGeneralInformation();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should toggle edit mode after updating general information', async () => {
        component.isEditMode.set(true);

        await component.saveGeneralInformation();

        expect(component.isEditMode()).toBeFalse();
    });

    it('should show error message if updating general course information fails', async () => {
        jest.spyOn(courseDashboardService, 'updateGeneralCourseInformation').mockRejectedValueOnce({});
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');
        component.generalCourseInformation.set('New general course information');

        await component.saveGeneralInformation();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should set isLoading correctly on loading general information', async () => {
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
