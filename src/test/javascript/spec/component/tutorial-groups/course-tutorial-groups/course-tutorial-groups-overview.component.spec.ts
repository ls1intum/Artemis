import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTutorialGroupsOverviewComponent } from 'app/overview/course-tutorial-groups/course-tutorial-groups-overview/course-tutorial-groups-overview.component';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { TutorialGroupsTableStubComponent } from '../stubs/tutorial-groups-table-stub.component';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';

describe('CourseTutorialGroupsOverviewComponent', () => {
    let fixture: ComponentFixture<CourseTutorialGroupsOverviewComponent>;
    let component: CourseTutorialGroupsOverviewComponent;

    let tutorialGroupTwo: TutorialGroup;
    let tutorialGroupOne: TutorialGroup;
    const course = { id: 1 } as Course;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [CourseTutorialGroupsOverviewComponent, TutorialGroupsTableStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(TutorialGroupsService), MockProvider(AlertService), { provide: Router, useValue: router }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseTutorialGroupsOverviewComponent);
                component = fixture.componentInstance;
                tutorialGroupOne = generateExampleTutorialGroup({ id: 1, numberOfRegisteredUsers: 5 });
                tutorialGroupTwo = generateExampleTutorialGroup({ id: 2, numberOfRegisteredUsers: 10 });
                component.tutorialGroups = [tutorialGroupOne, tutorialGroupTwo];
                component.course = course;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should pass the tutorial group and course id to the table', () => {
        const tableComponentInstance = fixture.debugElement.query(By.directive(TutorialGroupsTableStubComponent)).componentInstance;
        expect(tableComponentInstance).not.toBeNull();
        expect(tableComponentInstance.tutorialGroups).toEqual([tutorialGroupOne, tutorialGroupTwo]);
        expect(tableComponentInstance.course).toEqual(course);
    });

    it('should navigate to tutorial group detail page when tutorial group click callback is called', () => {
        fixture.detectChanges();
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.onTutorialGroupSelected(tutorialGroupOne);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/courses', 1, 'tutorial-groups', 1]);
    });
});
