import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/tutorial-groups-management/tutorial-groups-management.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { TutorialGroupRowButtonsStubComponent } from '../../../stubs/tutorial-group-row-buttons-stub.component';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { TutorialGroupsTableStubComponent } from '../../../stubs/tutorial-groups-table-stub.component';
import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { Component, Input } from '@angular/core';

@Component({ selector: 'jhi-tutorial-groups-course-information', template: '' })
class MockTutorialGroupsCourseInformationComponent {
    @Input()
    tutorialGroups: TutorialGroup[] = [];
}

describe('TutorialGroupsManagementComponent', () => {
    let fixture: ComponentFixture<TutorialGroupsManagementComponent>;
    let component: TutorialGroupsManagementComponent;

    let tutorialGroupTwo: TutorialGroup;
    let tutorialGroupOne: TutorialGroup;

    let tutorialGroupsService: TutorialGroupsService;
    let getAllOfCourseSpy: jest.SpyInstance;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupsManagementComponent,
                MockTutorialGroupsCourseInformationComponent,
                LoadingIndicatorContainerStubComponent,
                TutorialGroupsTableStubComponent,
                TutorialGroupRowButtonsStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                mockedActivatedRoute(
                    {},
                    {},
                    {
                        course: {
                            id: 1,
                            isAtLeastInstructor: true,
                        },
                    },
                    {},
                ),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupsManagementComponent);
                component = fixture.componentInstance;
                tutorialGroupOne = generateExampleTutorialGroup({ id: 1 });
                tutorialGroupTwo = generateExampleTutorialGroup({ id: 2 });

                tutorialGroupsService = TestBed.inject(TutorialGroupsService);
                getAllOfCourseSpy = jest.spyOn(tutorialGroupsService, 'getAllForCourse').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [tutorialGroupOne, tutorialGroupTwo],
                            status: 200,
                        }),
                    ),
                );
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(getAllOfCourseSpy).toHaveBeenCalledOnce();
        expect(getAllOfCourseSpy).toHaveBeenCalledWith(1);
    });

    it('should get all tutorial groups for course', () => {
        fixture.detectChanges();
        expect(component.tutorialGroups).toEqual([tutorialGroupOne, tutorialGroupTwo]);
        expect(getAllOfCourseSpy).toHaveBeenCalledOnce();
        expect(getAllOfCourseSpy).toHaveBeenCalledWith(1);
    });

    it('should navigate to tutorial group detail page when tutorial group click callback is called', () => {
        fixture.detectChanges();
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.onTutorialGroupSelected(tutorialGroupOne);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 1, 'tutorial-groups', tutorialGroupOne.id]);
    });
});
