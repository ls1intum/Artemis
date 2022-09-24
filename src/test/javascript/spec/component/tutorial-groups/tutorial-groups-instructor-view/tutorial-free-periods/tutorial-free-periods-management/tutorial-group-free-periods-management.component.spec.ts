import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { SortService } from 'app/shared/service/sort.service';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { Component, Input } from '@angular/core';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { simpleTwoLayerActivatedRouteProvider } from '../../../../../helpers/mocks/activated-route/simple-activated-route-providers';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { Router } from '@angular/router';
import { generateExampleTutorialGroupsConfiguration } from '../../../helpers/tutorialGroupsConfigurationExampleModels';
import { generateExampleTutorialGroupFreePeriod } from '../../../helpers/tutorialGroupFreePeriodExampleModel';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';

@Component({ selector: 'jhi-tutorial-group-free-period-row-buttons', template: '' })
class TutorialGroupRowButtonsStubComponent {
    @Input() courseId: number;
    @Input() tutorialGroupConfiguration: TutorialGroupsConfiguration;
    @Input() tutorialFreePeriod: TutorialGroupFreePeriod;
}

describe('TutorialGroupFreePeriodsManagementComponent', () => {
    let fixture: ComponentFixture<TutorialGroupFreePeriodsManagementComponent>;
    let component: TutorialGroupFreePeriodsManagementComponent;
    let configuration: TutorialGroupsConfiguration;
    const courseId = 1;
    const tutorialGroupConfigurationId = 1;
    let configurationService: TutorialGroupsConfigurationService;
    let findConfigurationSpy: jest.SpyInstance;

    let firstOfJanuaryPeriod: TutorialGroupFreePeriod;
    let secondOfJanuaryPeriod: TutorialGroupFreePeriod;
    let thirdOfJanuaryPeriod: TutorialGroupFreePeriod;

    const router = new MockRouter();
    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupFreePeriodsManagementComponent,
                LoadingIndicatorContainerStubComponent,
                TutorialGroupRowButtonsStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(AlertService),
                SortService,
                { provide: Router, useValue: router },
                simpleTwoLayerActivatedRouteProvider(new Map([['tutorialGroupsConfigurationId', tutorialGroupConfigurationId]]), new Map([['courseId', courseId]])),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupFreePeriodsManagementComponent);
                component = fixture.componentInstance;
                firstOfJanuaryPeriod = generateExampleTutorialGroupFreePeriod(1, dayjs('2021-01-01T00:00:00.000Z'), dayjs('2021-01-01T23:59:59.000Z'), 'First of January');
                secondOfJanuaryPeriod = generateExampleTutorialGroupFreePeriod(2, dayjs('2021-01-02T00:00:00.000Z'), dayjs('2021-01-02T23:59:59.000Z'), 'Second of January');
                thirdOfJanuaryPeriod = generateExampleTutorialGroupFreePeriod(3, dayjs('2021-01-03T00:00:00.000Z'), dayjs('2021-01-03T23:59:59.000Z'), 'Third of January');

                configuration = generateExampleTutorialGroupsConfiguration();
                configuration.tutorialGroupFreePeriods = [{ ...firstOfJanuaryPeriod }, { ...secondOfJanuaryPeriod }, { ...thirdOfJanuaryPeriod }];

                configurationService = TestBed.inject(TutorialGroupsConfigurationService);
                findConfigurationSpy = jest.spyOn(configurationService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: configuration })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should navigate to create page', fakeAsync(() => {
        fixture.detectChanges();
        const navigateSpy = jest.spyOn(router, 'navigateByUrl');
        const createButton = fixture.debugElement.nativeElement.querySelector('#create-tutorial-free-day');
        createButton.click();
        fixture.whenStable().then(() => {
            expect(navigateSpy).toHaveBeenCalledWith([
                '/course-management',
                courseId,
                'tutorial-groups-management',
                'configuration',
                configuration.id,
                'tutorial-free-days',
                'create',
            ]);
        });
    }));

    it('should load all free periods and sort by start date ascending', () => {
        fixture.detectChanges();
        expect(component.tutorialGroupFreePeriods).toEqual([firstOfJanuaryPeriod, secondOfJanuaryPeriod, thirdOfJanuaryPeriod]);
        expect(findConfigurationSpy).toHaveBeenCalledOnce();
        expect(findConfigurationSpy).toHaveBeenCalledWith(courseId, tutorialGroupConfigurationId);
    });

    it('should display three rows for three free periods', () => {
        fixture.detectChanges();
        const rowButtons = fixture.debugElement.queryAll(By.directive(TutorialGroupRowButtonsStubComponent));
        expect(rowButtons).toHaveLength(3);
        expect(rowButtons[0].componentInstance.tutorialFreePeriod).toEqual(firstOfJanuaryPeriod);
        expect(rowButtons[1].componentInstance.tutorialFreePeriod).toEqual(secondOfJanuaryPeriod);
        expect(rowButtons[2].componentInstance.tutorialFreePeriod).toEqual(thirdOfJanuaryPeriod);
    });
});
