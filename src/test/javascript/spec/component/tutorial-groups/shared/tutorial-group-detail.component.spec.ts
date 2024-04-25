import { TutorialGroupDetailComponent } from 'app/course/tutorial-groups/shared/tutorial-group-detail/tutorial-group-detail.component';
import { RouterTestingModule } from '@angular/router/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { Component, Input, ViewChild } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { SortService } from 'app/shared/service/sort.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/course/tutorial-groups/shared/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { RemoveSecondsPipe } from 'app/course/tutorial-groups/shared/remove-seconds.pipe';
import { DetailOverviewListComponent } from 'app/detail-overview-list/detail-overview-list.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import dayjs from 'dayjs/esm';
import { TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { ChangeDetectorRef } from '@angular/core';

@Component({ selector: 'jhi-mock-header', template: '<div id="mockHeader"></div>' })
class MockHeaderComponent {
    @Input() tutorialGroup: TutorialGroup;
}

@Component({
    selector: 'jhi-mock-wrapper',
    template: `
        <jhi-tutorial-group-detail [tutorialGroup]="tutorialGroup">
            <ng-template let-tutorialGroup>
                <jhi-mock-header [tutorialGroup]="tutorialGroup" />
            </ng-template>
        </jhi-tutorial-group-detail>
    `,
})
class MockWrapperComponent {
    @Input()
    tutorialGroup: TutorialGroup;

    @ViewChild(TutorialGroupDetailComponent)
    tutorialGroupDetailInstance: TutorialGroupDetailComponent;

    @ViewChild(MockHeaderComponent)
    mockHeaderInstance: MockHeaderComponent;
}

describe('TutorialGroupDetailWrapperTest', () => {
    let fixture: ComponentFixture<MockWrapperComponent>;
    let component: MockWrapperComponent;
    let detailInstance: TutorialGroupDetailComponent;
    let headerInstance: MockHeaderComponent;
    let exampleTutorialGroup: TutorialGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipMocksModule, RouterTestingModule.withRoutes([]), HttpClientTestingModule],
            declarations: [
                TutorialGroupDetailComponent,
                DetailOverviewListComponent,
                MockWrapperComponent,
                MockHeaderComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(RemoveSecondsPipe),
                MockComponent(FaIconComponent),
                MockComponent(TutorialGroupUtilizationIndicatorComponent),
            ],
            providers: [
                MockProvider(ArtemisMarkdownService),
                MockProvider(SortService),
                MockProvider(SessionStorageService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        })
            .overrideTemplate(DetailOverviewListComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MockWrapperComponent);
                component = fixture.componentInstance;
                exampleTutorialGroup = generateExampleTutorialGroup({});
                component.tutorialGroup = exampleTutorialGroup;
                fixture.detectChanges();
                detailInstance = component.tutorialGroupDetailInstance;
                headerInstance = component.mockHeaderInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should pass the tutorialGroup to the header', () => {
        expect(headerInstance.tutorialGroup).toEqual(component.tutorialGroup);
        expect(component.tutorialGroup).toEqual(detailInstance.tutorialGroup);
        const mockHeader = fixture.debugElement.nativeElement.querySelector('#mockHeader');
        expect(mockHeader).toBeTruthy();
    });
});

describe('TutorialGroupDetailComponent', () => {
    let fixture: ComponentFixture<TutorialGroupDetailComponent>;
    let component: TutorialGroupDetailComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipMocksModule, RouterTestingModule.withRoutes([])],
            declarations: [
                TutorialGroupDetailComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockComponent(TutorialGroupUtilizationIndicatorComponent),
                MockPipe(RemoveSecondsPipe),
            ],
            providers: [MockProvider(ArtemisMarkdownService), { provide: TranslateService, useClass: MockTranslateService }, MockProvider(ChangeDetectorRef)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupDetailComponent);
                component = fixture.componentInstance;
                component.tutorialGroup = generateExampleTutorialGroup({});
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it.each([
        [
            {
                tutorialGroupSessions: [
                    { start: dayjs().subtract(1, 'minute'), end: dayjs().subtract(1, 'minute'), attendanceCount: 0, status: TutorialGroupSessionStatus.ACTIVE },
                ],
            } as TutorialGroup,
            0,
        ],
        [
            { tutorialGroupSessions: [{ start: dayjs(), end: dayjs().add(1, 'minute'), attendanceCount: 42, status: TutorialGroupSessionStatus.ACTIVE }] } as TutorialGroup,
            undefined,
        ],
        [
            {
                tutorialGroupSessions: [
                    { start: dayjs().subtract(3, 'hours'), end: dayjs().subtract(1, 'hour'), attendanceCount: 42, status: TutorialGroupSessionStatus.CANCELLED },
                ],
            } as TutorialGroup,
            undefined,
        ],
        [
            {
                tutorialGroupSessions: [
                    { start: dayjs().subtract(4, 'weeks'), end: dayjs().subtract(4, 'weeks'), attendanceCount: 48, status: TutorialGroupSessionStatus.ACTIVE },
                    { start: dayjs().subtract(3, 'weeks'), end: dayjs().subtract(3, 'weeks'), attendanceCount: undefined, status: TutorialGroupSessionStatus.ACTIVE },
                    { start: dayjs().subtract(2, 'weeks'), end: dayjs().subtract(2, 'weeks'), attendanceCount: 35, status: TutorialGroupSessionStatus.ACTIVE },
                    { start: dayjs().subtract(1, 'weeks'), end: dayjs().subtract(1, 'weeks'), attendanceCount: 22, status: TutorialGroupSessionStatus.CANCELLED },
                    { start: dayjs().subtract(1, 'day'), end: dayjs().subtract(1, 'day'), attendanceCount: 13, status: TutorialGroupSessionStatus.ACTIVE },
                    { start: dayjs().add(1, 'weeks'), end: dayjs().add(1, 'weeks'), attendanceCount: 10, status: TutorialGroupSessionStatus.ACTIVE },
                    { start: dayjs().add(2, 'weeks'), end: dayjs().add(2, 'weeks'), attendanceCount: undefined, status: TutorialGroupSessionStatus.ACTIVE },
                ],
            } as TutorialGroup,
            24,
        ],
    ])('should calculate average attendance correctly', (tutorialGroup: TutorialGroup, expectedAttendance: number) => {
        component.tutorialGroup = tutorialGroup;
        component.recalculateAttendanceDetails();
        expect(component.tutorialGroup.averageAttendance).toBe(expectedAttendance);
    });
});
