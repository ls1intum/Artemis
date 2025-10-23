import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { ChangeDetectorRef, Component, input, viewChild } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { SortService } from 'app/shared/service/sort.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import dayjs from 'dayjs/esm';
import { TutorialGroupSessionStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { provideHttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { ManagementTutorialGroupDetailComponent } from 'app/tutorialgroup/shared/tutorial-group-detail/management-tutorial-group-detail.component';
import { RemoveSecondsPipe } from 'app/tutorialgroup/shared/pipe/remove-seconds.pipe';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/tutorialgroup/shared/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { IconCardComponent } from 'app/tutorialgroup/shared/icon-card/icon-card.component';

@Component({ selector: 'jhi-mock-header', template: '<div id="mockHeader"></div>' })
class MockHeaderComponent {
    readonly tutorialGroup = input.required<TutorialGroup>();
}

@Component({
    selector: 'jhi-mock-wrapper',
    template: `
        <jhi-management-tutorial-group-detail [tutorialGroup]="tutorialGroup()">
            <ng-template>
                <jhi-mock-header [tutorialGroup]="tutorialGroup()" />
            </ng-template>
        </jhi-management-tutorial-group-detail>
    `,
    imports: [ManagementTutorialGroupDetailComponent, MockHeaderComponent],
})
class MockWrapperComponent {
    readonly tutorialGroup = input.required<TutorialGroup>();

    tutorialGroupDetailInstance = viewChild.required(ManagementTutorialGroupDetailComponent);
    mockHeaderInstance = viewChild.required(MockHeaderComponent);
}

describe('TutorialGroupDetailWrapperTest', () => {
    let fixture: ComponentFixture<MockWrapperComponent>;
    let component: MockWrapperComponent;
    let detailInstance: ManagementTutorialGroupDetailComponent;
    let headerInstance: MockHeaderComponent;
    let exampleTutorialGroup: TutorialGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([]), FaIconComponent],
            declarations: [
                ManagementTutorialGroupDetailComponent,
                MockWrapperComponent,
                MockHeaderComponent,
                MockComponent(IconCardComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(RemoveSecondsPipe),
                MockComponent(TutorialGroupUtilizationIndicatorComponent),
                MockDirective(TranslateDirective),
                MockComponent(ProfilePictureComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(ArtemisMarkdownService),
                MockProvider(SortService),
                MockProvider(SessionStorageService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MockWrapperComponent);
                component = fixture.componentInstance;
                exampleTutorialGroup = generateExampleTutorialGroup({});
                fixture.componentRef.setInput('tutorialGroup', exampleTutorialGroup);
                fixture.detectChanges();
                detailInstance = component.tutorialGroupDetailInstance();
                headerInstance = component.mockHeaderInstance();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should pass the tutorialGroup to the header', () => {
        expect(headerInstance.tutorialGroup()).toEqual(component.tutorialGroup());
        expect(component.tutorialGroup()).toEqual(detailInstance.tutorialGroup());
        const mockHeader = fixture.debugElement.nativeElement.querySelector('#mockHeader');
        expect(mockHeader).toBeTruthy();
    });
});

describe('TutorialGroupDetailComponent', () => {
    let fixture: ComponentFixture<ManagementTutorialGroupDetailComponent>;
    let component: ManagementTutorialGroupDetailComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([]), FaIconComponent],
            declarations: [
                ManagementTutorialGroupDetailComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(IconCardComponent),
                MockComponent(TutorialGroupUtilizationIndicatorComponent),
                MockPipe(RemoveSecondsPipe),
                MockDirective(TranslateDirective),
                MockComponent(ProfilePictureComponent),
            ],
            providers: [MockProvider(ArtemisMarkdownService), { provide: TranslateService, useClass: MockTranslateService }, MockProvider(ChangeDetectorRef)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ManagementTutorialGroupDetailComponent);
                component = fixture.componentInstance;
                fixture.componentRef.setInput('tutorialGroup', generateExampleTutorialGroup({}));
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
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        component.recalculateAttendanceDetails();
        expect(component.tutorialGroup().averageAttendance).toBe(expectedAttendance);
    });
});
