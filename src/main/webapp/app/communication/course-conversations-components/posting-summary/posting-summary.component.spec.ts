import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PostingSummaryComponent } from 'app/communication/course-conversations-components/posting-summary/posting-summary.component';
import { Posting, PostingType, SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import dayjs from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('PostingSummaryComponent', () => {
    let component: PostingSummaryComponent;
    let fixture: ComponentFixture<PostingSummaryComponent>;

    const mockPost: Posting = {
        id: 1,
        author: {
            id: 1,
            name: 'Test Author',
            internal: false,
        },
        content: 'Test Content',
        postingType: PostingType.POST,
        creationDate: dayjs(),
        conversation: {
            id: 1,
            title: 'Test Conversation',
            type: ConversationType.CHANNEL,
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent],
            declarations: [
                PostingSummaryComponent,
                MockPipe(ArtemisDatePipe),
                MockComponent(ProfilePictureComponent),
                MockComponent(PostingContentComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PostingSummaryComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('Input handling', () => {
        it('should handle post input', () => {
            fixture.componentRef.setInput('post', mockPost);
            fixture.detectChanges();

            // Assert
            expect(component['isAnswerPost']).toBeFalsy();
            expect(component['postingIsOfToday']).toBeTruthy();
        });

        it('should handle undefined post', () => {
            fixture.componentRef.setInput('post', undefined);
            fixture.detectChanges();

            expect(component['isAnswerPost']).toBeFalsy();
        });
    });

    describe('PostingType detection', () => {
        it('should detect answer post', () => {
            const answerPost = Object.assign({}, mockPost, { postingType: PostingType.ANSWER.valueOf() });

            fixture.componentRef.setInput('post', answerPost);
            fixture.detectChanges();

            expect(component['isAnswerPost']).toBeTruthy();
        });

        it('should detect non-answer post', () => {
            fixture.componentRef.setInput('post', mockPost);
            fixture.detectChanges();

            expect(component['isAnswerPost']).toBeFalsy();
        });
    });

    describe('Date handling', () => {
        it('should detect post from today', () => {
            const todayPost = Object.assign({}, mockPost, { creationDate: dayjs() });

            fixture.componentRef.setInput('post', todayPost);
            fixture.detectChanges();

            expect(component['postingIsOfToday']).toBeTruthy();
        });

        it('should detect post not from today', () => {
            const yesterdayPost = Object.assign({}, mockPost, { creationDate: dayjs().subtract(1, 'day') });

            fixture.componentRef.setInput('post', yesterdayPost);
            fixture.detectChanges();

            expect(component['postingIsOfToday']).toBeFalsy();
        });
    });

    describe('Event emissions', () => {
        it('should emit status change', () => {
            const emitSpy = jest.spyOn(component.onChangeSavedPostStatus, 'emit');
            const newStatus = SavedPostStatus.ARCHIVED;

            component['onStatusChangeClick'](newStatus);

            expect(emitSpy).toHaveBeenCalledWith(newStatus);
        });

        it('should emit navigation event with post', () => {
            const emitSpy = jest.spyOn(component.onNavigateToPost, 'emit');
            fixture.componentRef.setInput('post', mockPost);

            component['onTriggerNavigateToPost']();

            expect(emitSpy).toHaveBeenCalledWith(mockPost);
        });

        it('should not emit navigation event when post is undefined', () => {
            const emitSpy = jest.spyOn(component.onNavigateToPost, 'emit');
            fixture.componentRef.setInput('post', undefined);

            component['onTriggerNavigateToPost']();

            expect(emitSpy).not.toHaveBeenCalled();
        });
    });

    describe('Template rendering', () => {
        beforeEach(fakeAsync(() => {
            fixture.componentRef.setInput('post', mockPost);
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
        }));

        it('should render author name', () => {
            const authorElement = fixture.nativeElement.querySelector('.posting-summary-author-content');
            expect(authorElement.textContent).toContain(mockPost.author!.name);
        });

        it('should render conversation title', () => {
            const conversationElement = fixture.nativeElement.querySelector('.posting-summary-conversation');
            expect(conversationElement.textContent).toContain(mockPost.conversation!.title);
        });

        it('should show saved post options when enabled', () => {
            fixture.componentRef.setInput('isShowSavedPostOptions', true);
            fixture.detectChanges();

            const optionsElement = fixture.nativeElement.querySelector('.posting-summary-saved-post-options');
            expect(optionsElement).toBeTruthy();
        });

        it('should not show saved post options when disabled', () => {
            fixture.componentRef.setInput('isShowSavedPostOptions', false);
            fixture.detectChanges();

            const optionsElement = fixture.nativeElement.querySelector('.posting-summary-saved-post-options');
            expect(optionsElement).toBeFalsy();
        });
    });
});
