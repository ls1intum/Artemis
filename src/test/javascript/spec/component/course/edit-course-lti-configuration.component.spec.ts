import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { of } from 'rxjs';
import { NgbNavModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { EditCourseLtiConfigurationComponent } from 'app/course/manage/course-lti-configuration/edit-course-lti-configuration.component';
import { Course } from 'app/entities/course.model';
import { OnlineCourseConfiguration } from 'app/entities/online-course-configuration.model';
import { mockedActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTestModule } from '../../test.module';
import { regexValidator } from 'app/shared/form/shortname-validator.directive';
import { LOGIN_PATTERN } from 'app/shared/constants/input.constants';

describe('Edit Course LTI Configuration Component', () => {
    let comp: EditCourseLtiConfigurationComponent;
    let fixture: ComponentFixture<EditCourseLtiConfigurationComponent>;
    let courseService: CourseManagementService;

    const router = new MockRouter();

    const onlineCourseConfiguration = {
        id: 1,
        ltiKey: 'key',
        ltiSecret: 'secret',
        userPrefix: 'prefix',
        registrationId: 'regId',
        clientId: 'clientId',
        jwkSetUri: 'jwkUri',
        authorizationUri: 'authUri',
        tokenUri: 'tokenUri',
    } as OnlineCourseConfiguration;
    const course = {
        id: 123,
        title: 'Course Title',
        isAtLeastInstructor: true,
        onlineCourseConfiguration,
    } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbNavModule, MockModule(ReactiveFormsModule)],
            declarations: [
                EditCourseLtiConfigurationComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockComponent(FaIconComponent),
            ],
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
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditCourseLtiConfigurationComponent);
                comp = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
            });
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
            expect(comp.onlineCourseConfigurationForm.get(['ltiKey'])?.value).toBe(onlineCourseConfiguration.ltiKey);
            expect(comp.onlineCourseConfigurationForm.get(['ltiSecret'])?.value).toBe(onlineCourseConfiguration.ltiSecret);
            expect(comp.onlineCourseConfigurationForm.get(['userPrefix'])?.value).toBe(onlineCourseConfiguration.userPrefix);
            expect(comp.onlineCourseConfigurationForm.get(['registrationId'])?.value).toBe(onlineCourseConfiguration.registrationId);
            expect(comp.onlineCourseConfigurationForm.get(['clientId'])?.value).toBe(onlineCourseConfiguration.clientId);
            expect(comp.onlineCourseConfigurationForm.get(['authorizationUri'])?.value).toBe(onlineCourseConfiguration.authorizationUri);
            expect(comp.onlineCourseConfigurationForm.get(['tokenUri'])?.value).toBe(onlineCourseConfiguration.tokenUri);
            expect(comp.onlineCourseConfigurationForm.get(['jwkSetUri'])?.value).toBe(onlineCourseConfiguration.jwkSetUri);
        });
    });

    it('should save upon form submission and navigate', () => {
        fixture.detectChanges();

        const changedConfiguration = {
            ...onlineCourseConfiguration,
            ltiKey: 'ChangedKey',
        } as OnlineCourseConfiguration;

        comp.onlineCourseConfigurationForm = new FormGroup({
            id: new FormControl(changedConfiguration.id),
            ltiKey: new FormControl(changedConfiguration.ltiKey),
            ltiSecret: new FormControl(changedConfiguration.ltiSecret),
            userPrefix: new FormControl(changedConfiguration.userPrefix, { validators: [regexValidator(LOGIN_PATTERN)] }),
            registrationId: new FormControl(changedConfiguration.registrationId),
            clientId: new FormControl(changedConfiguration.clientId),
            authorizationUri: new FormControl(changedConfiguration.authorizationUri),
            tokenUri: new FormControl(changedConfiguration.tokenUri),
            jwkSetUri: new FormControl(changedConfiguration.jwkSetUri),
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
