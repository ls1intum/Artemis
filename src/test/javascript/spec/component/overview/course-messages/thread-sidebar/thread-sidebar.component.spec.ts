import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ThreadSidebarComponent } from 'app/overview/course-messages/thread-sidebar/thread-sidebar.component';
import { MessageReplyInlineInputComponent } from 'app/shared/metis/message/message-reply-inline-input/message-reply-inline-input.component';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { directMessageUser1 } from '../../../../helpers/sample/metis-sample-data';
import { getElement } from '../../../../helpers/utils/general.utils';

describe('ThreadSidebarComponent', () => {
    let fixture: ComponentFixture<ThreadSidebarComponent>;
    let component: ThreadSidebarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(FormsModule)],
            declarations: [
                ThreadSidebarComponent,
                MockComponent(PostComponent),
                MockComponent(MessageReplyInlineInputComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockDirective(NgbTooltip),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ThreadSidebarComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize correctly on load', () => {
        expect(component.post).toBeUndefined();
        expect(component.createdAnswerPost).toBeUndefined();
    });

    it('should select displayed message correctly, prepare new answer and display the message in thread', () => {
        component.activePost = directMessageUser1;

        expect(component.post).toBe(directMessageUser1);
        expect(component.createdAnswerPost).not.toBeNull();
        expect(component.createdAnswerPost.post).toBe(directMessageUser1);
        expect(component.createdAnswerPost.content).toBeEmpty();

        fixture.detectChanges();

        const postElement = getElement(fixture.debugElement, 'jhi-post');
        const inlineInputForCreatingNewAnswer = getElement(fixture.debugElement, 'jhi-message-reply-inline-input');

        expect(postElement).not.toBeNull();
        expect(inlineInputForCreatingNewAnswer).not.toBeNull();
    });
});
