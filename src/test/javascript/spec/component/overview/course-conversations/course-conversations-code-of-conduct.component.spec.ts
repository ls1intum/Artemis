import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { CourseConversationsCodeOfConductComponent } from 'app/overview/course-conversations/code-of-conduct/course-conversations-code-of-conduct.component';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { AlertService } from 'app/core/util/alert.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { MockConversationService } from '../../../helpers/mocks/service/mock-conversation.service';

describe('Course Conversations Code Of Conduct Component', () => {
    let fixture: ComponentFixture<CourseConversationsCodeOfConductComponent>;
    let conversationService: ConversationService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseConversationsCodeOfConductComponent, MockPipe(ArtemisTranslatePipe), MockPipe(HtmlForMarkdownPipe)],
            providers: [MockProvider(AlertService), { provide: ConversationService, useClass: MockConversationService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseConversationsCodeOfConductComponent);
                conversationService = TestBed.inject(ConversationService);
                fixture.componentRef.setInput('course', { id: 1 });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        let getResponsibleUsersSpy = jest.spyOn(conversationService, 'getResponsibleUsersForCodeOfConduct');
        fixture.detectChanges();
        expect(getResponsibleUsersSpy).toHaveBeenCalled();
        expect(CourseConversationsCodeOfConductComponent).not.toBeNull();
    });
});
