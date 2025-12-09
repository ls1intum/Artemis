import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { of } from 'rxjs';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { EditCourseLtiConfigurationComponent } from 'app/core/course/manage/course-lti-configuration/edit-course-lti-configuration.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { OnlineCourseConfiguration } from 'app/lti/shared/entities/online-course-configuration.model';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { regexValidator } from 'app/shared/form/shortname-validator.directive';
import { LOGIN_PATTERN } from 'app/shared/constants/input.constants';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('Edit Course LTI Configuration Component', () => {
    let comp: EditCourseLtiConfigurationComponent;
    let fixture: ComponentFixture<EditCourseLtiConfigurationComponent>;
    let courseService: CourseManagementService;

    const router = new MockRouter();

    const onlineCourseConfiguration = {
        id: 1,
        userPrefix: 'prefix',
        requireExistingUser: false,
    } as OnlineCourseConfiguration;
    const course = {
        id: 123,
        title: 'Course Title',
        isAtLeastInstructor: true,
        onlineCourseConfiguration,
    } as Course;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NgbNavModule, MockModule(ReactiveFormsModule)],
            declarations: [EditCourseLtiConfigurationComponent, MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe), MockDirective(MockHasAnyAuthorityDirective)],
            providers: [
                MockProvider(CourseManagementService),
                { provide: Router, useValue: router },
                mockedActivatedRoute(
                    {},
                    {},
                    {
                        course,
                    },
                    {},
                ),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(EditCourseLtiConfigurationComponent);
        comp = fixture.componentInstance;
        courseService = TestBed.inject(CourseManagementService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(EditCourseLtiConfigurationComponent).not.toBeNull();
    });

    describe('OnInit', () => {
        it('should load course with online course configuration', () => {
            comp.ngOnInit();

            expect(comp.course).toEqual(course);
            expect(comp.onlineCourseConfiguration).toEqual(course.onlineCourseConfiguration);
            expect(comp.onlineCourseConfigurationForm.get(['id'])?.value).toBe(onlineCourseConfiguration.id);
            expect(comp.onlineCourseConfigurationForm.get(['userPrefix'])?.value).toBe(onlineCourseConfiguration.userPrefix);
            expect(comp.onlineCourseConfigurationForm.get(['requireExistingUser'])?.value).toBe(onlineCourseConfiguration.requireExistingUser);
        });
    });

    it('should save upon form submission and navigate', () => {
        fixture.detectChanges();

        const changedConfiguration = Object.assign({}, onlineCourseConfiguration) as OnlineCourseConfiguration;

        comp.onlineCourseConfigurationForm = new FormGroup({
            id: new FormControl(changedConfiguration.id),
            userPrefix: new FormControl(changedConfiguration.userPrefix, { validators: [regexValidator(LOGIN_PATTERN)] }),
            requireExistingUser: new FormControl(changedConfiguration.requireExistingUser),
        });

        const updateResponse: HttpResponse<OnlineCourseConfiguration> = new HttpResponse({
            body: changedConfiguration,
            status: 200,
        });

        const updatedStub = jest.spyOn(courseService, 'updateOnlineCourseConfiguration').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        expect(comp.isSaving).toBeFalse();

        comp.save();

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(123, changedConfiguration);
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', course.id!.toString(), 'lti-configuration']);
    });
});
