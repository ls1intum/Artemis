import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { CourseConversationsCodeOfConductComponent } from 'app/overview/course-conversations/code-of-conduct/course-conversations-code-of-conduct.component';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockCourseManagementService } from '../../../helpers/mocks/service/mock-course-management.service';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

describe('Course Conversations Code Of Conduct Component', () => {
    let component: CourseConversationsCodeOfConductComponent;
    let fixture: ComponentFixture<CourseConversationsCodeOfConductComponent>;

    let courseManagementService: CourseManagementService;
    let courseManagementServiceStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseConversationsCodeOfConductComponent, MockPipe(ArtemisTranslatePipe), MockPipe(HtmlForMarkdownPipe)],
            providers: [MockProvider(AlertService), { provide: CourseManagementService, useClass: MockCourseManagementService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseConversationsCodeOfConductComponent);
                component = fixture.componentInstance;

                component.course = { id: 1 };

                courseManagementService = TestBed.inject(CourseManagementService);
                courseManagementServiceStub = jest
                    .spyOn(courseManagementService, 'searchUsers')
                    .mockReturnValue(of({ body: [{ firstName: '', lastName: '', email: '' }] }) as Observable<HttpResponse<UserPublicInfoDTO[]>>);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CourseConversationsCodeOfConductComponent).not.toBeNull();
    });

    it('should display responsible contacts', () => {
        const getUserLabelSpy = jest.spyOn(component, 'getUserLabel');

        fixture.detectChanges();

        expect(getUserLabelSpy).toHaveBeenCalledTimes(2);
        expect(courseManagementServiceStub).toHaveBeenCalledOnce();
    });
});
