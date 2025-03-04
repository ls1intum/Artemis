import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { By } from '@angular/platform-browser';
import { EditTutorialGroupComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/crud/edit-tutorial-group/edit-tutorial-group.component';
import { generateExampleTutorialGroup, tutorialGroupToTutorialGroupFormData } from '../../../helpers/tutorialGroupExampleModels';
import { mockedActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import '@angular/localize/init';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { ArtemisDatePipe } from '../../../../../../../../main/webapp/app/shared/pipes/artemis-date.pipe';
import { MockResizeObserver } from '../../../../../helpers/mocks/service/mock-resize-observer';
import { TutorialGroupFormComponent } from '../../../../../../../../main/webapp/app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';
import { ThemeService } from 'app/core/theme/theme.service';
import { MockThemeService } from '../../../../../helpers/mocks/service/mock-theme.service';

describe('EditTutorialGroupComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupComponent>;
    let component: EditTutorialGroupComponent;
    let findTutorialGroupSpy: jest.SpyInstance;
    let tutorialGroupService: TutorialGroupsService;
    const course = { id: 2, title: 'Example' };

    let exampleTutorialGroup: TutorialGroup;

    const router = new MockRouter();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [EditTutorialGroupComponent, OwlNativeDateTimeModule],
            providers: [
                MockProvider(ArtemisDatePipe),
                MockProvider(AlertService),
                { provide: Router, useValue: router },
                mockedActivatedRoute(
                    {
                        tutorialGroupId: 1,
                    },
                    {},
                    { course },
                    {},
                ),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ThemeService, useClass: MockThemeService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(EditTutorialGroupComponent);
        component = fixture.componentInstance;
        exampleTutorialGroup = generateExampleTutorialGroup({});

        tutorialGroupService = TestBed.inject(TutorialGroupsService);

        const response: HttpResponse<TutorialGroup> = new HttpResponse({
            body: exampleTutorialGroup,
            status: 200,
        });

        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });

        findTutorialGroupSpy = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(response));
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
        expect(findTutorialGroupSpy).toHaveBeenCalledOnce();
        expect(findTutorialGroupSpy).toHaveBeenCalledWith(2, 1);
    });

    it('should set form data correctly', () => {
        const tutorialGroupFormComponent: TutorialGroupFormComponent = fixture.debugElement.query(By.directive(TutorialGroupFormComponent)).componentInstance;

        expect(component.tutorialGroup).toEqual(exampleTutorialGroup);
        expect(findTutorialGroupSpy).toHaveBeenCalledWith(2, 1);
        expect(findTutorialGroupSpy).toHaveBeenCalledOnce();

        expect(component.formData).toEqual(tutorialGroupToTutorialGroupFormData(exampleTutorialGroup));
        expect(tutorialGroupFormComponent.formData).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        delete exampleTutorialGroup.isUserRegistered;
        delete exampleTutorialGroup.isUserTutor;
        delete exampleTutorialGroup.course;
        delete exampleTutorialGroup.numberOfRegisteredUsers;
        delete exampleTutorialGroup.courseTitle;
        delete exampleTutorialGroup.teachingAssistantName;
        delete exampleTutorialGroup.teachingAssistantId;
        delete exampleTutorialGroup.teachingAssistantImageUrl;

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

        const tutorialGroupForm: TutorialGroupFormComponent = fixture.debugElement.query(By.directive(TutorialGroupFormComponent)).componentInstance;

        const formData = tutorialGroupToTutorialGroupFormData(changedTutorialGroup);

        tutorialGroupForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(2, 1, changedTutorialGroup, undefined, undefined);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 2, 'tutorial-groups']);
    });
});
