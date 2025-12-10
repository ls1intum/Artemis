import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { SortService } from 'app/shared/service/sort.service';
import { LoadingIndicatorContainerStubComponent } from 'test/helpers/stubs/shared/loading-indicator-container-stub.component';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { Router } from '@angular/router';
import { generateExampleTutorialGroupsConfiguration } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { generateExampleTutorialGroupFreePeriod } from 'test/helpers/sample/tutorialgroup/tutorialGroupFreePeriodExampleModel';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CreateTutorialGroupFreePeriodComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { TutorialGroupFreePeriodsTableComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-table/tutorial-group-free-periods-table.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TutorialGroupFreePeriodsManagementComponent', () => {
    let fixture: ComponentFixture<TutorialGroupFreePeriodsManagementComponent>;
    let component: TutorialGroupFreePeriodsManagementComponent;
    let configuration: TutorialGroupsConfiguration;
    const courseId = 1;
    const course = {
        id: courseId,
        timeZone: 'Europe/Berlin',
    } as Course;
    let configurationService: TutorialGroupsConfigurationService;
    let findConfigurationSpy: jest.SpyInstance;

    let firstOfJanuaryPeriod: TutorialGroupFreePeriod;
    let secondOfJanuaryPeriod: TutorialGroupFreePeriod;
    let thirdOfJanuaryPeriod: TutorialGroupFreePeriod;

    const router = new MockRouter();
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FaIconComponent],
            declarations: [
                TutorialGroupFreePeriodsManagementComponent,
                LoadingIndicatorContainerStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockRouterLinkDirective,
                TutorialGroupFreePeriodsTableComponent,
                MockDirective(TranslateDirective),
            ],
            providers: [
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(AlertService),
                MockProvider(NgbModal),
                SortService,
                { provide: Router, useValue: router },
                mockedActivatedRoute({}, {}, { course }, {}),
                provideHttpClient(),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupFreePeriodsManagementComponent);
                component = fixture.componentInstance;
                firstOfJanuaryPeriod = generateExampleTutorialGroupFreePeriod({
                    id: 1,
                    start: dayjs('2021-01-01T00:00:00.000Z'),
                    end: dayjs('2021-01-01T23:59:59.000Z'),
                    reason: 'First of January',
                });
                secondOfJanuaryPeriod = generateExampleTutorialGroupFreePeriod({
                    id: 2,
                    start: dayjs('2021-01-02T00:00:00.000Z'),
                    end: dayjs('2021-01-02T23:59:59.000Z'),
                    reason: 'Second of January',
                });
                thirdOfJanuaryPeriod = generateExampleTutorialGroupFreePeriod({
                    id: 3,
                    start: dayjs('2021-01-03T00:00:00.000Z'),
                    end: dayjs('2021-01-03T23:59:59.000Z'),
                    reason: 'Third of January',
                });

                configuration = generateExampleTutorialGroupsConfiguration({});
                configuration.tutorialGroupFreePeriods = [
                    Object.assign({}, firstOfJanuaryPeriod),
                    Object.assign({}, secondOfJanuaryPeriod),
                    Object.assign({}, thirdOfJanuaryPeriod),
                ];

                configurationService = TestBed.inject(TutorialGroupsConfigurationService);
                findConfigurationSpy = jest.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: configuration })));
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should open the create free day dialog when the respective button is clicked', fakeAsync(() => {
        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = {
            componentInstance: { course: undefined, tutorialGroupConfigurationId: undefined, initialize: () => {} },
            result: of(),
        };
        const modalOpenSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
        const openDialogSpy = jest.spyOn(component, 'openCreateFreePeriodDialog');

        const button = fixture.debugElement.nativeElement.querySelector('#create-tutorial-free-day');
        button.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledWith(CreateTutorialGroupFreePeriodComponent, { backdrop: 'static', scrollable: false, size: 'lg', animation: false });
            expect(mockModalRef.componentInstance.tutorialGroupConfigurationId).toEqual(configuration.id);
            expect(mockModalRef.componentInstance.course).toEqual(course);
        });
    }));

    it('should load all free periods and sort by start date ascending', () => {
        expect(component.tutorialGroupFreePeriods).toEqual([thirdOfJanuaryPeriod, secondOfJanuaryPeriod, firstOfJanuaryPeriod]);
        expect(findConfigurationSpy).toHaveBeenCalledOnce();
        expect(findConfigurationSpy).toHaveBeenCalledWith(courseId);
    });

    it('should display three rows for three free periods', () => {
        const rowButtons = fixture.debugElement.queryAll(By.css('jhi-tutorial-group-free-period-row-buttons'));
        expect(rowButtons).toHaveLength(3);
        expect(rowButtons[0].componentInstance.tutorialFreePeriod()).toEqual(thirdOfJanuaryPeriod);
        expect(rowButtons[1].componentInstance.tutorialFreePeriod()).toEqual(secondOfJanuaryPeriod);
        expect(rowButtons[2].componentInstance.tutorialFreePeriod()).toEqual(firstOfJanuaryPeriod);
    });
});
