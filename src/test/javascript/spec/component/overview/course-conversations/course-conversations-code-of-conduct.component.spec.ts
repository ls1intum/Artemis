import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { CourseConversationsCodeOfConductComponent } from 'app/overview/course-conversations/code-of-conduct/course-conversations-code-of-conduct.component';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AlertService } from 'app/core/util/alert.service';

describe('Course Conversations Code Of Conduct Component', () => {
    let fixture: ComponentFixture<CourseConversationsCodeOfConductComponent>;
    let component: CourseConversationsCodeOfConductComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseConversationsCodeOfConductComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockProvider(AlertService),
                MockProvider(CourseManagementService),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseConversationsCodeOfConductComponent);
                component = fixture.componentInstance;
                component.course = { id: 1 };
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });
});
