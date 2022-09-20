import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { By } from '@angular/platform-browser';
import { EditTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { User } from 'app/core/user/user.model';
import { TutorialGroupFormStubComponent } from '../../../stubs/tutorial-group-form-stub.component';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { simpleTwoLayerActivatedRouteProvider } from '../../../../../helpers/mocks/activated-route/simple-activated-route-providers';
import { Language } from 'app/entities/course.model';
import { TutorialGroupSchedule } from 'app/entities/tutorial-group/tutorial-group-schedule.model';
import dayjs from 'dayjs/esm';
import { TutorialGroupFormData } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';

describe('EditTutorialGroupComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupComponent>;
    let component: EditTutorialGroupComponent;
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true };
    let findCourseSpy: jest.SpyInstance;
    let courseService: CourseManagementService;
    let findTutorialGroupSpy: jest.SpyInstance;
    let tutorialGroupService: TutorialGroupsService;

    let exampleTutorialGroup: TutorialGroup;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [EditTutorialGroupComponent, LoadingIndicatorContainerStubComponent, TutorialGroupFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(CourseManagementService),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                simpleTwoLayerActivatedRouteProvider(new Map([['tutorialGroupId', 1]]), new Map([['courseId', 2]])),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditTutorialGroupComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                findCourseSpy = jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

                exampleTutorialGroup = new TutorialGroup();
                exampleTutorialGroup.id = 1;
                exampleTutorialGroup.title = 'Example';
                exampleTutorialGroup.capacity = 10;
                exampleTutorialGroup.campus = 'Example Campus';
                exampleTutorialGroup.language = Language.GERMAN;
                exampleTutorialGroup.additionalInformation = 'Example Information';
                exampleTutorialGroup.isOnline = true;
                exampleTutorialGroup.teachingAssistant = { id: 1, login: 'Example' } as User;
                exampleTutorialGroup.tutorialGroupSchedule = new TutorialGroupSchedule();
                exampleTutorialGroup.tutorialGroupSchedule.id = 1;
                exampleTutorialGroup.tutorialGroupSchedule.dayOfWeek = 1;
                exampleTutorialGroup.tutorialGroupSchedule.startTime = '10:00:00';
                exampleTutorialGroup.tutorialGroupSchedule.endTime = '11:00:00';
                exampleTutorialGroup.tutorialGroupSchedule.repetitionFrequency = 1;
                exampleTutorialGroup.tutorialGroupSchedule.location = 'Example Location';
                exampleTutorialGroup.tutorialGroupSchedule.validFromInclusive = dayjs('2021-01-01');
                exampleTutorialGroup.tutorialGroupSchedule.validToInclusive = dayjs('2021-01-31');

                tutorialGroupService = TestBed.inject(TutorialGroupsService);

                const response: HttpResponse<TutorialGroup> = new HttpResponse({
                    body: exampleTutorialGroup,
                    status: 200,
                });

                findTutorialGroupSpy = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(response));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(findCourseSpy).toHaveBeenCalledOnce();
        expect(findCourseSpy).toHaveBeenCalledWith(2);
        expect(findTutorialGroupSpy).toHaveBeenCalledOnce();
        expect(findTutorialGroupSpy).toHaveBeenCalledWith(2, 1);
    });

    it('should set form data correctly', () => {
        fixture.detectChanges();

        const tutorialGroupFormStubComponent: TutorialGroupFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFormStubComponent)).componentInstance;

        expect(component.tutorialGroup).toEqual(exampleTutorialGroup);
        expect(findTutorialGroupSpy).toHaveBeenCalledWith(2, 1);
        expect(findTutorialGroupSpy).toHaveBeenCalledOnce();

        expect(component.formData).toEqual(entityToFormData(exampleTutorialGroup));
        expect(tutorialGroupFormStubComponent.formData).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        fixture.detectChanges();

        const changedTutorialGroup = {
            ...exampleTutorialGroup,
            title: 'Changed',
        } as TutorialGroup;

        const updateResponse: HttpResponse<TutorialGroup> = new HttpResponse({
            body: changedTutorialGroup,
            status: 200,
        });

        const updatedStub = jest.spyOn(tutorialGroupService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const tutorialGroupForm: TutorialGroupFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupFormStubComponent)).componentInstance;

        const formData = entityToFormData(changedTutorialGroup);

        tutorialGroupForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(2, 1, changedTutorialGroup);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 2, 'tutorial-groups-management']);
    });

    const entityToFormData = (entity: TutorialGroup): TutorialGroupFormData => {
        return {
            title: entity.title,
            capacity: entity.capacity,
            campus: entity.campus,
            language: entity.language,
            additionalInformation: entity.additionalInformation,
            isOnline: entity.isOnline,
            teachingAssistant: entity.teachingAssistant,
            schedule: {
                location: entity.tutorialGroupSchedule?.location,
                dayOfWeek: entity.tutorialGroupSchedule?.dayOfWeek,
                startTime: entity.tutorialGroupSchedule?.startTime,
                endTime: entity.tutorialGroupSchedule?.endTime,
                repetitionFrequency: entity.tutorialGroupSchedule?.repetitionFrequency,
                period: [entity.tutorialGroupSchedule?.validFromInclusive?.toDate()!, entity.tutorialGroupSchedule?.validToInclusive?.toDate()!],
            },
        };
    };
});
