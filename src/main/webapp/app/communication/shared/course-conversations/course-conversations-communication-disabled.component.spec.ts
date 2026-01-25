import { CourseConversationsComponent } from 'app/communication/shared/course-conversations/course-conversations.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { of } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { FeatureActivationComponent } from 'app/shared/feature-activation/feature-activation.component';
import { By } from '@angular/platform-browser';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockProvider } from 'ng-mocks';
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
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { MetisService } from 'app/communication/service/metis.service';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { AlertService } from 'app/shared/service/alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EventManager } from 'app/shared/service/event-manager.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

describe('CourseConversationComponent with communication disabled', () => {
    let component: CourseConversationsComponent;
    let fixture: ComponentFixture<CourseConversationsComponent>;
    let metisConversationService: MetisConversationService;
    let metisService: MetisService;
    let alertService: AlertService;
    let eventManager: EventManager;
    const courseWithDisabledCommunication = {
        id: 1,
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED,
        isAtLeastInstructor: true,
    };
    beforeEach(() => {
        TestBed.configureTestingModule({
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
                    },
                },
                { provide: MetisService, useClass: MockMetisService },
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
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(EventManager),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseConversationsComponent);
        component = fixture.componentInstance;
        metisConversationService = TestBed.inject(MetisConversationService);
        metisService = fixture.debugElement.injector.get(MetisService);
        eventManager = TestBed.inject(EventManager);
        alertService = TestBed.inject(AlertService);
        jest.spyOn(metisConversationService, 'isServiceSetup$', 'get').mockReturnValue(of(false));
    });
    it('should render feature activation page when instructor + management view', () => {
        fixture.detectChanges();

        const featureActivationComponent = fixture.debugElement.query(By.directive(FeatureActivationComponent));
        expect(featureActivationComponent).toBeTruthy();
    });

    it.each([true, false])('should call service method to enable communication', async (withMessaging: boolean) => {
        const serviceSpy = jest.spyOn(metisService, 'enable').mockReturnValue(of(undefined));
        const alertSpy = jest.spyOn(alertService, 'error');
        const eventManagerSpy = jest.spyOn(eventManager, 'broadcast').mockImplementation(() => {});
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
        jest.spyOn(metisService, 'enable').mockImplementation(() => {
            throw new Error('Test error');
        });
        const alertSpy = jest.spyOn(alertService, 'error');
        fixture.detectChanges();
        await component.enableCommunication();
        expect(alertSpy).toHaveBeenCalledOnce();
    });
});
