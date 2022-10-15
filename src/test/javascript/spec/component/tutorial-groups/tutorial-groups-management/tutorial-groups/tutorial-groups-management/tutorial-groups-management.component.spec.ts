import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
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
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortService } from 'app/shared/service/sort.service';
import { TutorialGroupRowButtonsStubComponent } from '../../../stubs/tutorial-group-row-buttons-stub.component';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { simpleTwoLayerActivatedRouteProvider } from '../../../../../helpers/mocks/activated-route/simple-activated-route-providers';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { generateExampleTutorialGroupsConfiguration } from '../../../helpers/tutorialGroupsConfigurationExampleModels';
import { Course } from 'app/entities/course.model';

describe('TutorialGroupsManagementComponent', () => {
    let fixture: ComponentFixture<TutorialGroupsManagementComponent>;
    let component: TutorialGroupsManagementComponent;
    const configuration = generateExampleTutorialGroupsConfiguration();
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true, tutorialGroupsConfiguration: configuration } as Course;

    let tutorialGroupTwo: TutorialGroup;
    let tutorialGroupOne: TutorialGroup;

    let tutorialGroupsService: TutorialGroupsService;
    let getAllOfCourseSpy: jest.SpyInstance;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupsManagementComponent,
                LoadingIndicatorContainerStubComponent,
                TutorialGroupRowButtonsStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
            ],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(AlertService),
                MockProvider(SortService),
                { provide: Router, useValue: router },
                simpleTwoLayerActivatedRouteProvider(new Map(), new Map(), { course }),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupsManagementComponent);
                component = fixture.componentInstance;
                tutorialGroupOne = generateExampleTutorialGroup(1);
                tutorialGroupTwo = generateExampleTutorialGroup(2);

                tutorialGroupsService = TestBed.inject(TutorialGroupsService);
                getAllOfCourseSpy = jest.spyOn(tutorialGroupsService, 'getAllOfCourse').mockReturnValue(
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

    it('should call sort service', () => {
        fixture.detectChanges();
        component.sortingPredicate = 'id';
        component.ascending = false;

        const sortService = TestBed.inject(SortService);
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');

        component.sortRows();
        expect(sortServiceSpy).toHaveBeenCalledWith([tutorialGroupOne, tutorialGroupTwo], 'id', false);
        expect(sortServiceSpy).toHaveBeenCalledOnce();
    });

    it('should navigate to configuration creation page if course has no configuration', () => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        delete course.tutorialGroupsConfiguration;
        fixture.detectChanges();
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'tutorial-groups-management', 'configuration', 'create']);
    });
});
