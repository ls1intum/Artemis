import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ConversationHeaderComponent } from 'app/overview/course-conversations/layout/conversation-header/conversation-header.component';
import { Location } from '@angular/common';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ChannelIconComponent } from 'app/overview/course-conversations/other/channel-icon/channel-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../helpers/conversationExampleModels';
import { BehaviorSubject, EMPTY } from 'rxjs';
import { ConversationAddUsersDialogComponent } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import { defaultFirstLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { ChannelDTO, ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { CourseLectureDetailsComponent } from 'app/overview/course-lectures/course-lecture-details.component';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/exam-detail.component';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideRouter } from '@angular/router';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { input, runInInjectionContext, SimpleChanges } from '@angular/core';
import { MockSyncStorage } from '../../../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';

const examples: ConversationDTO[] = [
    generateOneToOneChatDTO({}),
    generateExampleGroupChatDTO({}),
    generateExampleChannelDTO({} as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.EXERCISE, subTypeReferenceId: 1 } as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.LECTURE, subTypeReferenceId: 1 } as ChannelDTO),
    generateExampleChannelDTO({ subType: ChannelSubType.EXAM, subTypeReferenceId: 1 } as ChannelDTO),
];
examples.forEach((activeConversation) => {
    describe('ConversationHeaderComponent with' + +(activeConversation instanceof ChannelDTO ? activeConversation.subType + ' ' : '') + activeConversation.type, () => {
        let component: ConversationHeaderComponent;
        let fixture: ComponentFixture<ConversationHeaderComponent>;
        let metisConversationService: MetisConversationService;
        let location: Location;
        const course = { id: 1 } as any;
        const canAddUsers = jest.fn();

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                declarations: [
                    ConversationHeaderComponent,
                    MockComponent(ChannelIconComponent),
                    MockComponent(ProfilePictureComponent),
                    MockComponent(FaIconComponent),
                    MockPipe(ArtemisTranslatePipe),
                ],
                imports: [],
                providers: [
                    provideRouter([
                        { path: 'courses/:courseId/lectures/:lectureId', component: CourseLectureDetailsComponent },
                        { path: 'courses/:courseId/exercises/:exerciseId', component: CourseExerciseDetailsComponent },
                        { path: 'courses/:courseId/exams/:examId', component: ExamDetailComponent },
                    ]),
                    MockProvider(NgbModal),
                    MockProvider(MetisConversationService),
                    MockProvider(ConversationService),
                    { provide: MetisService, useClass: MockMetisService },
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                ],
            }).compileComponents();
        }));

        beforeEach(() => {
            canAddUsers.mockReturnValue(true);
            metisConversationService = TestBed.inject(MetisConversationService);
            Object.defineProperty(metisConversationService, 'course', { get: () => course });
            Object.defineProperty(metisConversationService, 'activeConversation$', { get: () => new BehaviorSubject(activeConversation).asObservable() });
            Object.defineProperty(metisConversationService, 'forceRefresh', { value: () => EMPTY });

            location = TestBed.inject(Location);

            fixture = TestBed.createComponent(ConversationHeaderComponent);
            component = fixture.componentInstance;
            component.canAddUsers = canAddUsers;
            fixture.detectChanges();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(component.activeConversation).toEqual(activeConversation);
        });

        it('should open the add users dialog', fakeAsync(() => {
            canAddUsers.mockReturnValue(false);
            fixture.detectChanges();
            expect(fixture.debugElement.nativeElement.querySelector('.addUsers')).toBeFalsy();

            canAddUsers.mockReturnValue(true);
            fixture.detectChanges();
            const addUsersButton = fixture.debugElement.nativeElement.querySelector('.addUsers');
            expect(addUsersButton).toBeTruthy();

            const modalService = TestBed.inject(NgbModal);
            const mockModalRef = {
                componentInstance: { course: undefined, activeConversation, initialize: () => {} },
                result: Promise.resolve(),
            };
            const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
            fixture.detectChanges();
            addUsersButton.click();
            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(ConversationAddUsersDialogComponent, defaultFirstLayerDialogOptions);
                expect(mockModalRef.componentInstance.course).toEqual(course);
                expect(mockModalRef.componentInstance.activeConversation).toEqual(activeConversation);
            });
        }));

        it('should open dialog details dialog with members tab', fakeAsync(() => {
            detailDialogTest('members', ConversationDetailTabs.MEMBERS);
        }));

        it('should open dialog details dialog with info tab', fakeAsync(() => {
            detailDialogTest('info', ConversationDetailTabs.INFO);
        }));

        it('should toggle search when search button is pressed', fakeAsync(() => {
            const searchButton = fixture.debugElement.nativeElement.querySelector('.search');
            expect(searchButton).toBeTruthy();

            const toggleSearchSpy = jest.spyOn(component, 'toggleSearchBar');
            fixture.detectChanges();
            searchButton.click();

            fixture.whenStable().then(() => {
                expect(toggleSearchSpy).toHaveBeenCalledOnce();
            });
        }));

        it('should set otherUser to the non-requesting user in a one-to-one conversation', () => {
            const oneToOneChat = generateOneToOneChatDTO({});
            oneToOneChat.members = [
                { id: 1, isRequestingUser: true },
                { id: 2, isRequestingUser: false },
            ];

            component.activeConversation = oneToOneChat;
            component.getOtherUser();

            expect(component.otherUser).toEqual(oneToOneChat.members[1]);
        });

        it('should toggle pinned messages visibility', fakeAsync(() => {
            const togglePinnedMessagesSpy = jest.spyOn(component, 'togglePinnedMessages');

            expect(component.showPinnedMessages).toBe(false);

            component.togglePinnedMessages();
            expect(togglePinnedMessagesSpy).toHaveBeenCalled();
            expect(component.showPinnedMessages).toBe(true);

            component.togglePinnedMessages();
            expect(component.showPinnedMessages).toBe(false);
        }));

        it('should emit togglePinnedMessage event', fakeAsync(() => {
            const togglePinnedMessageSpy = jest.spyOn(component.togglePinnedMessage, 'emit');

            component.togglePinnedMessages();
            expect(togglePinnedMessageSpy).toHaveBeenCalled();
        }));

        it('should set showPinnedMessages to false if pinnedMessageCount changes to 0 while it is currently showing pinned messages', () => {
            component.showPinnedMessages = true;
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.pinnedMessageCount = input<number>(3);

                const changes: SimpleChanges = {
                    pinnedMessageCount: {
                        currentValue: 0,
                        previousValue: 3,
                        firstChange: false,
                        isFirstChange: () => false,
                    },
                };

                component.ngOnChanges(changes);
                expect(component.showPinnedMessages).toBeFalse();
            });
        });

        it('should not change showPinnedMessages if pinnedMessageCount changes but is not 0', () => {
            component.showPinnedMessages = true;
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.pinnedMessageCount = input<number>(3);

                const changes: SimpleChanges = {
                    pinnedMessageCount: {
                        currentValue: 5,
                        previousValue: 3,
                        firstChange: false,
                        isFirstChange: () => false,
                    },
                };

                component.ngOnChanges(changes);
                expect(component.showPinnedMessages).toBeTrue();
            });
        });

        it('should correctly handle first change of pinnedMessageCount', () => {
            component.showPinnedMessages = false;
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.pinnedMessageCount = input<number>(2);

                const changes: SimpleChanges = {
                    pinnedMessageCount: {
                        currentValue: 2,
                        previousValue: undefined,
                        firstChange: true,
                        isFirstChange: () => true,
                    },
                };

                component.ngOnChanges(changes);
                expect(component.showPinnedMessages).toBeFalse();
            });
        });

        if (activeConversation instanceof ChannelDTO && activeConversation.subType !== ChannelSubType.GENERAL) {
            it(
                'should navigate to ' + activeConversation.subType,
                fakeAsync(() => {
                    const button = fixture.debugElement.nativeElement.querySelector('.sub-type-reference');
                    button.click();
                    tick();

                    // Assert that the router has navigated to the correct link
                    expect(location.path()).toBe('/courses/1/' + activeConversation.subType + 's/1');
                }),
            );
        }

        function detailDialogTest(className: string, tab: ConversationDetailTabs) {
            const detailButton = fixture.debugElement.nativeElement.querySelector('.' + className);
            expect(detailButton).toBeTruthy();

            const modalService = TestBed.inject(NgbModal);
            const mockModalRef = {
                componentInstance: {
                    course: undefined,
                    activeConversation,
                    selectedTab: undefined,
                    initialize: () => {},
                },
                result: Promise.resolve(),
            };
            const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
            fixture.detectChanges();
            detailButton.click();
            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(ConversationDetailDialogComponent, defaultFirstLayerDialogOptions);
                expect(mockModalRef.componentInstance.course).toEqual(course);
                expect(mockModalRef.componentInstance.activeConversation).toEqual(activeConversation);
                expect(mockModalRef.componentInstance.selectedTab).toEqual(tab);
            });
        }
    });
});
