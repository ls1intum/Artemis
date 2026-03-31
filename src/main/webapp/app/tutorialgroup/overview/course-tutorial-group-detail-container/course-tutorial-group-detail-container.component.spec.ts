import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { CourseTutorialGroupDetailContainerComponent } from './course-tutorial-group-detail-container.component';
import { TutorialGroupDetailAccessLevel, TutorialGroupDetailComponent } from 'app/tutorialgroup/shared/tutorial-group-detail/tutorial-group-detail.component';
import { TutorialGroupCourseAndGroupService } from 'app/tutorialgroup/shared/service/tutorial-group-course-and-group.service';
import { TutorialGroupDetailData } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { CourseTutorialGroupDetailStubComponent } from 'test/helpers/stubs/tutorialgroup/course-tutorial-group-detail-stub.component';
import { MockTutorialGroupCourseAndGroupService } from 'test/helpers/mocks/service/mock-tutorial-group-course-and-group.service';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { By } from '@angular/platform-browser';

describe('CourseTutorialGroupDetailContainerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseTutorialGroupDetailContainerComponent>;
    let component: CourseTutorialGroupDetailContainerComponent;
    let tutorialGroupCourseAndGroupService: MockTutorialGroupCourseAndGroupService;

    beforeEach(async () => {
        tutorialGroupCourseAndGroupService = new MockTutorialGroupCourseAndGroupService();

        await TestBed.configureTestingModule({
            imports: [CourseTutorialGroupDetailContainerComponent, CourseTutorialGroupDetailStubComponent],
            providers: [
                mockedActivatedRoute({ tutorialGroupId: '1' }, {}, {}, {}, { courseId: '2' }),
                { provide: TutorialGroupCourseAndGroupService, useValue: tutorialGroupCourseAndGroupService },
            ],
        })
            .overrideComponent(CourseTutorialGroupDetailContainerComponent, {
                remove: { imports: [TutorialGroupDetailComponent] },
                add: { imports: [CourseTutorialGroupDetailStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupDetailContainerComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    function createTutorialGroupDetailData(): TutorialGroupDetailData {
        return new TutorialGroupDetailData({
            id: 1,
            title: 'TG Tue 13',
            language: 'Polish',
            isOnline: false,
            sessions: [],
            tutorName: 'Grace Hopper',
            tutorLogin: 'grace',
            tutorId: 12,
            tutorImageUrl: undefined,
            capacity: undefined,
            campus: undefined,
            additionalInformation: undefined,
            groupChannelId: undefined,
            tutorChatId: undefined,
        });
    }

    it('should fetch and expose the tutorial group when courseId and tutorialGroupId are available', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        tutorialGroupCourseAndGroupService.fetchTutorialGroup.mockImplementation(() => {
            tutorialGroupCourseAndGroupService.tutorialGroup.set(tutorialGroup);
        });
        tutorialGroupCourseAndGroupService.fetchCourse.mockImplementation(() => {
            tutorialGroupCourseAndGroupService.course.set(new Course());
        });

        fixture.detectChanges();

        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).toHaveBeenCalledOnce();
        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).toHaveBeenCalledWith(2, 1);
        expect(component.tutorialGroup()).toBe(tutorialGroup);

        const detailStub = fixture.debugElement.query(By.directive(CourseTutorialGroupDetailStubComponent)).componentInstance as CourseTutorialGroupDetailStubComponent;
        expect(detailStub.courseId()).toBe(2);
        expect(detailStub.tutorialGroup()).toBe(tutorialGroup);
        expect(detailStub.loggedInUserAccessLevel()).toBe(TutorialGroupDetailAccessLevel.STUDENT);
    });

    it('should fetch the course for the courseId and compute messaging availability from it', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        tutorialGroupCourseAndGroupService.fetchTutorialGroup.mockImplementation(() => {
            tutorialGroupCourseAndGroupService.tutorialGroup.set(tutorialGroup);
        });
        tutorialGroupCourseAndGroupService.fetchCourse.mockImplementation(() => {
            tutorialGroupCourseAndGroupService.course.set(course);
        });

        fixture.detectChanges();

        expect(tutorialGroupCourseAndGroupService.fetchCourse).toHaveBeenCalledOnce();
        expect(tutorialGroupCourseAndGroupService.fetchCourse).toHaveBeenCalledWith(2);
        expect(component.isMessagingEnabled()).toBe(true);

        const detailStub = fixture.debugElement.query(By.directive(CourseTutorialGroupDetailStubComponent)).componentInstance as CourseTutorialGroupDetailStubComponent;
        expect(detailStub.isMessagingEnabled()).toBe(true);
    });

    it('should compute messaging as disabled when the fetched course does not allow messaging', () => {
        const course = new Course();
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        tutorialGroupCourseAndGroupService.fetchTutorialGroup.mockImplementation(() => {
            tutorialGroupCourseAndGroupService.tutorialGroup.set(createTutorialGroupDetailData());
        });
        tutorialGroupCourseAndGroupService.fetchCourse.mockImplementation(() => {
            tutorialGroupCourseAndGroupService.course.set(course);
        });

        fixture.detectChanges();

        expect(component.isMessagingEnabled()).toBe(false);
        const detailStub = fixture.debugElement.query(By.directive(CourseTutorialGroupDetailStubComponent)).componentInstance as CourseTutorialGroupDetailStubComponent;
        expect(detailStub.isMessagingEnabled()).toBe(false);
    });
});
