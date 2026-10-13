import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations/course-conversations.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { EMPTY, of } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { FeatureActivationComponent } from 'app/shared-ui/feature-activation/feature-activation.component';
import { By } from '@angular/platform-browser';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { MockProvider } from 'ng-mocks';
import { DialogService } from 'primeng/dynamicdialog';
import { PostService } from 'app/communication/service/post.service';
import { AnswerPostService } from 'app/communication/service/answer-post.service';
import { ReactionService } from 'app/communication/service/reaction.service';
import { AccountService } from 'app/core/auth/account.service';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { ForwardedMessageService } from 'app/communication/service/forwarded-message.service';
import { SavedPostService } from 'app/communication/service/saved-post.service';
import { provideHttpClient } from '@angular/common/http';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockCourseConversationsService } from 'test/helpers/mocks/service/mock-course-conversations.service';
import { CommunicationService } from 'app/communication/service/communication.service';
import { MockCommunicationService } from 'test/helpers/mocks/service/mock-communication.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

describe('CourseConversationComponent with communication disabled', () => {
    let component: CourseConversationsComponent;
    let fixture: ComponentFixture<CourseConversationsComponent>;
    let courseConversationsService: CourseConversationsService;
    let communicationService: CommunicationService;
    let alertService: AlertService;
    let eventManager: EventManager;
    const courseWithDisabledCommunication = {
        id: 1,
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED,
        isAtLeastInstructor: true,
    };
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseConversationsComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            snapshot: {
                                data: {
                                    course: courseWithDisabledCommunication,
                                },
                            },
                        },
                    },
                },
                {
                    provide: Router,
                    useValue: {
                        url: '/course-management/1/conversations',
                        // Read by CourseTabRefreshService, which listens for the tab being selected again
                        events: EMPTY,
                        currentNavigation: () => null,
                    },
                },
                { provide: CommunicationService, useClass: MockCommunicationService },
                MockProvider(PostService),
                MockProvider(AnswerPostService),
                MockProvider(ReactionService),
                MockProvider(ConversationService),
                MockProvider(ForwardedMessageService),
                MockProvider(SavedPostService),
                MockProvider(AlertService),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: CourseConversationsService, useClass: MockCourseConversationsService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(EventManager),
                MockProvider(DialogService),
            ],
        });

        fixture = TestBed.createComponent(CourseConversationsComponent);
        component = fixture.componentInstance;
        courseConversationsService = TestBed.inject(CourseConversationsService);
        communicationService = fixture.debugElement.injector.get(CommunicationService);
        eventManager = TestBed.inject(EventManager);
        alertService = TestBed.inject(AlertService);
        vi.spyOn(courseConversationsService, 'isServiceSetup$', 'get').mockReturnValue(of(false));
    });
    it('should render feature activation page when instructor + management view', () => {
        fixture.detectChanges();

        const featureActivationComponent = fixture.debugElement.query(By.directive(FeatureActivationComponent));
        expect(featureActivationComponent).toBeTruthy();
    });

    it.each([true, false])('should call service method to enable communication', async (withMessaging: boolean) => {
        const serviceSpy = vi.spyOn(communicationService, 'enable').mockReturnValue(of(undefined));
        const alertSpy = vi.spyOn(alertService, 'error');
        const eventManagerSpy = vi.spyOn(eventManager, 'broadcast').mockImplementation(() => {});
        fixture.detectChanges();
        await component.enableCommunication(withMessaging);
        if (withMessaging) {
            expect(component.course()!.courseInformationSharingConfiguration).toEqual(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        } else {
            expect(component.course()!.courseInformationSharingConfiguration).toEqual(CourseInformationSharingConfiguration.COMMUNICATION_ONLY);
        }
        expect(alertSpy).not.toHaveBeenCalled();
        expect(serviceSpy).toHaveBeenCalledExactlyOnceWith(courseWithDisabledCommunication.id, withMessaging);
        expect(eventManagerSpy).toHaveBeenCalledOnce();
    });

    it('should call alert service on error', async () => {
        vi.spyOn(communicationService, 'enable').mockImplementation(() => {
            throw new Error('Test error');
        });
        const alertSpy = vi.spyOn(alertService, 'error');
        fixture.detectChanges();
        await component.enableCommunication();
        expect(alertSpy).toHaveBeenCalledOnce();
    });
});
