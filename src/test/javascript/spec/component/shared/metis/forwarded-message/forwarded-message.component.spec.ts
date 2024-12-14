import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ElementRef, input, runInInjectionContext } from '@angular/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ForwardedMessageComponent } from 'app/shared/metis/forwarded-message/forwarded-message.component';
import { Posting } from 'app/entities/metis/posting.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

describe('ForwardedMessageComponent', () => {
    let component: ForwardedMessageComponent;
    let fixture: ComponentFixture<ForwardedMessageComponent>;

    const mockPost: Post = {
        id: 1,
        creationDate: dayjs().subtract(1, 'day'),
        conversation: { type: 'channel', name: 'general' } as any,
    } as Post;

    const mockAnswerPost: AnswerPost = {
        id: 2,
        creationDate: dayjs(),
        post: mockPost,
    } as AnswerPost;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ForwardedMessageComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), FaIconComponent, MockComponent(ProfilePictureComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(ForwardedMessageComponent);
        component = fixture.componentInstance;
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should call updateSourceName and update todayFlag when originalPostDetails changes', () => {
        jest.spyOn(component, 'updateSourceName');
        jest.spyOn(component, 'getTodayFlag');

        runInInjectionContext(fixture.debugElement.injector, () => {
            component.originalPostDetails = input<Posting>(mockPost);

            component.ngOnChanges({
                originalPostDetails: {
                    currentValue: mockPost,
                    previousValue: undefined,
                    firstChange: true,
                    isFirstChange: () => true,
                },
            });
            expect(component.updateSourceName).toHaveBeenCalled();
            expect(component.getTodayFlag).toHaveBeenCalled();
            expect(component.postingIsOfToday).toBeFalse();
        });
    });

    it('should set sourceName correctly for a channel post', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.originalPostDetails = input<Posting>(mockPost);
            component.ngOnInit();
            component.ngOnChanges({
                originalPostDetails: {
                    currentValue: mockPost,
                    previousValue: undefined,
                    firstChange: true,
                    isFirstChange: () => true,
                },
            });
            expect(component.sourceName).toBe('#general |');
        });
    });

    it('should set sourceName correctly for a one-to-one chat post', () => {
        const oneToOnePost = { ...mockPost, conversation: { type: 'oneToOneChat' } } as Post;
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.originalPostDetails = input<Posting>(oneToOnePost);
            component.ngOnInit();
            component.ngOnChanges({
                originalPostDetails: {
                    currentValue: oneToOnePost,
                    previousValue: undefined,
                    firstChange: true,
                    isFirstChange: () => true,
                },
            });
            expect(component.sourceName).toBe('a direct message ');
        });
    });

    it('should set todayFlag to "artemisApp.metis.today" if post is created today', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.originalPostDetails = input<Posting>(mockAnswerPost);
            component.ngOnChanges({
                originalPostDetails: {
                    currentValue: mockAnswerPost,
                    previousValue: undefined,
                    firstChange: true,
                    isFirstChange: () => true,
                },
            });
            expect(component.postingIsOfToday).toBeTrue();
            expect(component.todayFlag).toBe('artemisApp.metis.today');
        });
    });

    it('should set todayFlag to undefined if post is not created today', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.originalPostDetails = input<Posting>(mockPost);
            component.ngOnChanges({
                originalPostDetails: {
                    currentValue: mockPost,
                    previousValue: undefined,
                    firstChange: true,
                    isFirstChange: () => true,
                },
            });
            expect(component.postingIsOfToday).toBeFalse();
            expect(component.todayFlag).toBeUndefined();
        });
    });

    it('should set isContentLong to true if content overflows', () => {
        component.messageContent = {
            nativeElement: {
                scrollHeight: 200,
                clientHeight: 100,
            },
        } as ElementRef;

        component.checkIfContentOverflows();

        expect(component.isContentLong).toBeTrue();
    });

    it('should truncate content if it exceeds maxLines', () => {
        const longContent = 'Line1\nLine2\nLine3\nLine4\nLine5\nLine6';
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.originalPostDetails = input<Posting>({ content: longContent } as Posting);
            component.isContentLong = true;
            component.maxLines = 5;

            const displayedContent = component.displayedForwardedContent();

            expect(displayedContent).toContain('Line1\nLine2\nLine3\nLine4\nLine5...');
        });
    });

    it('should display full content when showFullForwardedMessage is true', () => {
        const content = 'Full message content';
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.originalPostDetails = input<Posting>({ content } as Posting);
            component.showFullForwardedMessage = true;

            const displayedContent = component.displayedForwardedContent();

            expect(displayedContent).toBe(content);
        });
    });

    it('should call checkIfContentOverflows in ngAfterViewInit', fakeAsync(() => {
        jest.spyOn(component, 'checkIfContentOverflows');
        component.ngAfterViewInit();
        tick();

        expect(component.checkIfContentOverflows).toHaveBeenCalled();
    }));

    it('should set sourceName to "a group message |" for group conversation', () => {
        const groupPost = { ...mockPost, conversation: { type: 'groupChat' } } as Post;
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.originalPostDetails = input<Posting>(groupPost);
            component.ngOnChanges({
                originalPostDetails: {
                    currentValue: groupPost,
                    previousValue: undefined,
                    firstChange: true,
                    isFirstChange: () => true,
                },
            });
            expect(component.sourceName).toBe('a group message ');
        });
    });

    it('should emit onNavigateToPost event when onTriggerNavigateToPost is called', () => {
        const spy = jest.spyOn(component.onNavigateToPost, 'emit');
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.originalPostDetails = input<Posting>(mockPost);

            component.onTriggerNavigateToPost();

            expect(spy).toHaveBeenCalledWith(mockPost);
        });
    });
});
