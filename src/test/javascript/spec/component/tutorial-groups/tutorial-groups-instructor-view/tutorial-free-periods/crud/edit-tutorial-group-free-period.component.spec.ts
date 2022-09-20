import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { simpleOneLayerActivatedRouteProvider } from '../../../../../helpers/mocks/activated-route/simple-activated-route-providers';
import dayjs from 'dayjs/esm';
import { Router } from '@angular/router';
import { EditTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/crud/edit-tutorial-group-free-period/edit-tutorial-group-free-period.component';
import { TutorialGroupFreePeriodFormStubComponent } from '../../../stubs/tutorial-group-free-period-form-stub.component';
import { TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';

describe('EditTutorialGroupFreePeriodComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupFreePeriodComponent>;
    let component: EditTutorialGroupFreePeriodComponent;
    let periodService: TutorialGroupFreePeriodService;
    let findPeriodSpy: jest.SpyInstance;
    let examplePeriod: TutorialGroupFreePeriod;
    const courseId = 1;
    const configurationId = 2;
    const periodId = 3;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [EditTutorialGroupFreePeriodComponent, LoadingIndicatorContainerStubComponent, TutorialGroupFreePeriodFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(TutorialGroupFreePeriodService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                simpleOneLayerActivatedRouteProvider(new Map([['tutorialGroupFreePeriodId', periodId]]), {
                    course: {
                        id: courseId,
                        tutorialGroupsConfiguration: {
                            id: configurationId,
                            timeZone: 'Europe/Berlin',
                        },
                    },
                }),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditTutorialGroupFreePeriodComponent);
                component = fixture.componentInstance;
                periodService = TestBed.inject(TutorialGroupFreePeriodService);

                examplePeriod = new TutorialGroupFreePeriod();
                examplePeriod.id = periodId;
                // we get utc from the server --> will be converted to time zone of configuration
                examplePeriod.start = dayjs.utc('2021-01-01T00:00:00');
                examplePeriod.end = dayjs.utc('2021-01-01T23:59:59');
                examplePeriod.reason = 'Holiday';

                findPeriodSpy = jest.spyOn(periodService, 'getOneOfConfiguration').mockReturnValue(of(new HttpResponse({ body: examplePeriod })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(findPeriodSpy).toHaveBeenCalledOnce();
        expect(findPeriodSpy).toHaveBeenCalledWith(courseId, configurationId, periodId);
    });

    it('should set form data correctly', () => {
        fixture.detectChanges();

        const formStub: TutorialGroupFreePeriodFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormStubComponent)).componentInstance;

        expect(component.freePeriod).toEqual(examplePeriod);
        expect(findPeriodSpy).toHaveBeenCalledOnce();
        expect(findPeriodSpy).toHaveBeenCalledWith(courseId, configurationId, periodId);

        expect(component.formData.reason).toEqual(examplePeriod.reason);
        expect(component.formData.date).toEqual(examplePeriod.start!.tz('Europe/Berlin').toDate());
        expect(formStub.formData).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        fixture.detectChanges();

        const changedPeriod: TutorialGroupFreePeriod = {
            ...examplePeriod,
            reason: 'Changed',
        };

        const updateResponse: HttpResponse<TutorialGroupFreePeriod> = new HttpResponse({
            body: changedPeriod,
            status: 200,
        });

        const updatedStub = jest.spyOn(periodService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const sessionForm: TutorialGroupFreePeriodFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFreePeriodFormStubComponent)).componentInstance;

        const formData = {
            date: dayjs('2021-01-01T00:00:00').tz('Europe/Berlin').toDate(),
            reason: 'Changed',
        };

        sessionForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(courseId, configurationId, periodId, {
            date: formData.date,
            reason: formData.reason,
        });
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', courseId, 'tutorial-groups-management', 'configuration', configurationId, 'tutorial-free-days']);
    });
});
