import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

import { CourseTutorialGroupDetailContainerComponent } from 'app/tutorialgroup/overview/course-tutorial-group-detail-container/course-tutorial-group-detail-container.component';
import { CourseTutorialGroupDetailComponent } from 'app/tutorialgroup/overview/course-tutorial-group-detail/course-tutorial-group-detail.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

import { MockProvider } from 'ng-mocks';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { RawTutorialGroupDetailGroupDTO, TutorialGroupDetailGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

import { CourseTutorialGroupDetailStubComponent } from 'test/helpers/stubs/tutorialgroup/course-tutorial-group-detail-stub.component';

describe('CourseTutorialGroupDetailContainerComponent', () => {
    let fixture: ComponentFixture<CourseTutorialGroupDetailContainerComponent>;
    let component: CourseTutorialGroupDetailContainerComponent;
    let tutorialGroupService: TutorialGroupsService;
    let courseManagementService: CourseManagementService;
    let tutorialGroupOfResponse: TutorialGroupDetailGroupDTO;
    let courseOfResponse: Course;
    let findStub: jest.SpyInstance;
    let findByIdStub: jest.SpyInstance;

    const parentParams = { courseId: 2 };
    const parentRoute = { parent: { params: of(parentParams) } } as any as ActivatedRoute;
    const route = { params: of({ tutorialGroupId: 1 }), parent: parentRoute } as any as ActivatedRoute;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [CourseTutorialGroupDetailContainerComponent, CourseTutorialGroupDetailStubComponent],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        });

        TestBed.overrideComponent(CourseTutorialGroupDetailContainerComponent as any, {
            remove: { imports: [CourseTutorialGroupDetailComponent] as any },
            add: { imports: [CourseTutorialGroupDetailStubComponent] },
        });

        await TestBed.compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupDetailContainerComponent);
        component = fixture.componentInstance;

        tutorialGroupService = TestBed.inject(TutorialGroupsService);
        courseManagementService = TestBed.inject(CourseManagementService);

        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: 3,
        };
        tutorialGroupOfResponse = new TutorialGroupDetailGroupDTO(raw);

        courseOfResponse = { id: 2 } as Course;

        findStub = jest.spyOn(courseManagementService, 'find').mockReturnValue(of({ body: courseOfResponse } as any));
        findByIdStub = jest.spyOn(tutorialGroupService, 'getTutorialGroupDetailGroupDTO').mockReturnValue(of(tutorialGroupOfResponse));
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should load tutorial group', () => {
        fixture.detectChanges();
        expect(component.tutorialGroup).toEqual(tutorialGroupOfResponse);
        expect(component.course).toEqual(courseOfResponse);
        expect(findByIdStub).toHaveBeenCalledWith(2, 1);
        expect(findByIdStub).toHaveBeenCalledOnce();
        expect(findStub).toHaveBeenCalledWith(2);
        expect(findStub).toHaveBeenCalledOnce();
    });
});
