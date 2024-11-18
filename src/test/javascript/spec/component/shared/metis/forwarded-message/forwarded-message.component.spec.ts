import { ComponentFixture, TestBed } from '@angular/core/testing';
import { input, runInInjectionContext } from '@angular/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ForwardedMessageComponent } from '../../../../../../../main/webapp/app/shared/metis/forwarded-message/forwarded-message.component';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { Posting } from '../../../../../../../main/webapp/app/entities/metis/posting.model';
import { ArtemisDatePipe } from '../../../../../../../main/webapp/app/shared/pipes/artemis-date.pipe';
import { ProfilePictureComponent } from '../../../../../../../main/webapp/app/shared/profile-picture/profile-picture.component';

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
            declarations: [ForwardedMessageComponent, TranslatePipeMock, MockPipe(ArtemisDatePipe), FaIconComponent, MockComponent(ProfilePictureComponent)],
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
            expect(component.updateSourceName).toHaveBeenCalledWith(mockPost);
            expect(component.getTodayFlag).toHaveBeenCalled();
            expect(component.postingIsOfToday).toBeFalse(); // MockPost is not created today
        });
    });

    it('should set sourceName correctly for a channel post', () => {
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
            expect(component.sourceName).toBe('#general');
        });
    });

    it('should set sourceName correctly for a one-to-one chat post', () => {
        const oneToOnePost = { ...mockPost, conversation: { type: 'oneToOneChat' } } as Post;
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.originalPostDetails = input<Posting>(oneToOnePost);
            component.ngOnChanges({
                originalPostDetails: {
                    currentValue: oneToOnePost,
                    previousValue: undefined,
                    firstChange: true,
                    isFirstChange: () => true,
                },
            });
            expect(component.sourceName).toBe('a direct message');
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
});
