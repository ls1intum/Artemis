import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, input } from '@angular/core';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SortService } from 'app/shared/service/sort.service';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { Router } from '@angular/router';
import { generateExampleTutorialGroupsConfiguration } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { generateExampleTutorialGroupFreePeriod } from 'test/helpers/sample/tutorialgroup/tutorialGroupFreePeriodExampleModel';
import dayjs from 'dayjs/esm';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CreateTutorialGroupFreePeriodComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { TutorialGroupFreePeriodsTableComponent } from './tutorial-group-free-periods-table/tutorial-group-free-periods-table.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { tutorialGroupConfigurationDtoFromEntity } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

@Component({
    selector: 'jhi-tutorial-group-free-periods-table',
    template: '',
})
class MockTutorialGroupFreePeriodsTableComponent {
    course = input.required<Course>();
    tutorialGroupsConfiguration = input.required<TutorialGroupsConfiguration>();
    tutorialGroupFreePeriods = input.required<TutorialGroupFreePeriod[]>();
    labelText = input.required<string>();
    loadAll = input.required<() => void>();
}

describe('TutorialGroupFreePeriodsManagementComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialGroupFreePeriodsManagementComponent>;
    let component: TutorialGroupFreePeriodsManagementComponent;
    let configuration: TutorialGroupsConfiguration;
    const courseId = 1;
    const course = {
        id: courseId,
        timeZone: 'Europe/Berlin',
    } as Course;
    let configurationService: TutorialGroupsConfigurationService;
    let findConfigurationSpy: ReturnType<typeof vi.spyOn>;

    let firstOfJanuaryPeriod: TutorialGroupFreePeriod;
    let secondOfJanuaryPeriod: TutorialGroupFreePeriod;
    let thirdOfJanuaryPeriod: TutorialGroupFreePeriod;

    const router = new MockRouter();
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupFreePeriodsManagementComponent, OwlNativeDateTimeModule],
            providers: [
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(AlertService),
                SortService,
                { provide: Router, useValue: router },
                mockedActivatedRoute({}, {}, { course }, {}),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
            ],
        })
            .overrideComponent(TutorialGroupFreePeriodsManagementComponent, {
                remove: { imports: [TutorialGroupFreePeriodsTableComponent] },
                add: { imports: [MockTutorialGroupFreePeriodsTableComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(TutorialGroupFreePeriodsManagementComponent);
        component = fixture.componentInstance;

        firstOfJanuaryPeriod = generateExampleTutorialGroupFreePeriod({
            id: 1,
            start: dayjs.utc('2021-01-01T00:00:00'),
            end: dayjs.utc('2021-01-01T23:59:59'),
            reason: 'First of January',
        });
        secondOfJanuaryPeriod = generateExampleTutorialGroupFreePeriod({
            id: 2,
            start: dayjs.utc('2021-01-02T00:00:00'),
            end: dayjs.utc('2021-01-02T23:59:59'),
            reason: 'Second of January',
        });
        thirdOfJanuaryPeriod = generateExampleTutorialGroupFreePeriod({
            id: 3,
            start: dayjs.utc('2021-01-03T00:00:00'),
            end: dayjs.utc('2021-01-03T23:59:59'),
            reason: 'Third of January',
        });

        configuration = generateExampleTutorialGroupsConfiguration({});
        configuration.tutorialGroupFreePeriods = [firstOfJanuaryPeriod, secondOfJanuaryPeriod, thirdOfJanuaryPeriod];

        configurationService = TestBed.inject(TutorialGroupsConfigurationService);
        findConfigurationSpy = vi
            .spyOn(configurationService, 'getOneOfCourse')
            .mockReturnValue(of(new HttpResponse({ body: tutorialGroupConfigurationDtoFromEntity(configuration) })));
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should open the create free day dialog when the respective button is clicked', async () => {
        const mockCreateDialog = { open: vi.fn() } as unknown as CreateTutorialGroupFreePeriodComponent;
        vi.spyOn(component, 'createFreePeriodDialog').mockReturnValue(mockCreateDialog);
        const openDialogSpy = vi.spyOn(component, 'openCreateFreePeriodDialog');

        const button = fixture.debugElement.nativeElement.querySelector('#create-tutorial-free-day');
        button.click();

        await fixture.whenStable();
        expect(openDialogSpy).toHaveBeenCalledOnce();
        expect(mockCreateDialog.open).toHaveBeenCalledOnce();
    });

    it('should load all free periods and sort by start date descending', () => {
        expect(component.tutorialGroupFreePeriods).toEqual([thirdOfJanuaryPeriod, secondOfJanuaryPeriod, firstOfJanuaryPeriod]);
        expect(findConfigurationSpy).toHaveBeenCalledOnce();
        expect(findConfigurationSpy).toHaveBeenCalledWith(courseId);
    });

    it('should pass free days to the table component', () => {
        // All three periods are "free days" (start at 00:00, end at 23:59 on the same day)
        expect(component.freeDays).toHaveLength(3);
        expect(component.freePeriods).toHaveLength(0);
        expect(component.freePeriodsWithinDay).toHaveLength(0);
    });
});
