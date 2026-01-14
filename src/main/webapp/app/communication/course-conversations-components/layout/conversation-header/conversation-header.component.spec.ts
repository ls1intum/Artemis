import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ConversationHeaderComponent } from 'app/communication/course-conversations-components/layout/conversation-header/conversation-header.component';
import { Location } from '@angular/common';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { By } from '@angular/platform-browser';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { BehaviorSubject, EMPTY, Subject, of } from 'rxjs';
import { defaultFirstLayerDialogOptions } from 'app/communication/course-conversations-components/other/conversation.util';
import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { CourseLectureDetailsComponent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { CourseExerciseDetailsComponent } from 'app/core/course/overview/exercise-details/course-exercise-details.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/detail/exam-detail.component';
import { MetisService } from 'app/communication/service/metis.service';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideRouter } from '@angular/router';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { SimpleChanges } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { ConversationAddUsersDialogComponent } from 'app/communication/course-conversations-components/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';

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
                imports: [MockComponent(ChannelIconComponent), MockComponent(ProfilePictureComponent), MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe)],
                providers: [
                    provideRouter([
                        { path: 'courses/:courseId/lectures/:lectureId', component: CourseLectureDetailsComponent },
                        { path: 'courses/:courseId/exercises/:exerciseId', component: CourseExerciseDetailsComponent },
                        { path: 'courses/:courseId/exams/:examId', component: ExamDetailComponent },
                    ]),
                    MockProvider(NgbModal),
                    MockProvider(ConversationService),
                    { provide: MetisService, useClass: MockMetisService },
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: MetisConversationService, useClass: MockMetisConversationService },
                    LocalStorageService,
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
            fixture.changeDetectorRef.detectChanges();
            expect(fixture.debugElement.nativeElement.querySelector('.addUsers')).toBeFalsy();

            canAddUsers.mockReturnValue(true);
            fixture.changeDetectorRef.detectChanges();
            const addUsersButton = fixture.debugElement.nativeElement.querySelector('.addUsers');
            expect(addUsersButton).toBeTruthy();

            const modalService = TestBed.inject(NgbModal);
            const mockModalRef = {
                componentInstance: { course: undefined, activeConversation, initialize: () => {} },
                result: Promise.resolve(),
            };
            const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
            fixture.changeDetectorRef.detectChanges();
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

            expect(component.showPinnedMessages).toBeFalse();

            component.togglePinnedMessages();
            expect(togglePinnedMessagesSpy).toHaveBeenCalled();
            expect(component.showPinnedMessages).toBeTrue();

            component.togglePinnedMessages();
            expect(component.showPinnedMessages).toBeFalse();
        }));

        it('should emit togglePinnedMessage event', fakeAsync(() => {
            const togglePinnedMessageSpy = jest.spyOn(component.togglePinnedMessage, 'emit');

            component.togglePinnedMessages();
            expect(togglePinnedMessageSpy).toHaveBeenCalled();
        }));

        it('should set showPinnedMessages to false if pinnedMessageCount changes to 0 while it is currently showing pinned messages', () => {
            component.showPinnedMessages = true;
            fixture.componentRef.setInput('pinnedMessageCount', 3);
            fixture.changeDetectorRef.detectChanges();

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

        it('should not change showPinnedMessages if pinnedMessageCount changes but is not 0', () => {
            component.showPinnedMessages = true;
            fixture.componentRef.setInput('pinnedMessageCount', 3);
            fixture.changeDetectorRef.detectChanges();

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

        it('should correctly handle first change of pinnedMessageCount', () => {
            component.showPinnedMessages = false;
            fixture.componentRef.setInput('pinnedMessageCount', 2);
            fixture.changeDetectorRef.detectChanges();

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

        if (activeConversation instanceof ChannelDTO && activeConversation.subType !== ChannelSubType.GENERAL) {
            it(
                'should navigate to ' + activeConversation.subType,
                fakeAsync(() => {
                    const button = fixture.debugElement.query(By.css('#subTypeReferenceRouterLink')).nativeElement;
                    button.click();
                    tick();
                    discardPeriodicTasks();
                    fixture.whenStable().then(() => {
                        // Assert that the router has navigated to the correct link
                        expect(location.path()).toBe('/courses/1/' + activeConversation.subType + 's/1');
                    });
                }),
            );
        }

        it('should dismiss modal and call createOneToOneChatWithId when userNameClicked is emitted', fakeAsync(() => {
            const fakeUserNameClicked$ = new Subject<number>();
            const fakeClosed$ = new Subject<void>();

            const fakeModalRef: NgbModalRef = {
                componentInstance: {
                    course: undefined,
                    activeConversation: undefined,
                    selectedTab: undefined,
                    initialize: jest.fn(),
                    userNameClicked: fakeUserNameClicked$,
                },
                dismiss: jest.fn(),
                closed: fakeClosed$,
                result: Promise.resolve(),
            } as any;

            const modalService = TestBed.inject(NgbModal);
            jest.spyOn(modalService, 'open').mockReturnValue(fakeModalRef);

            const metisConversationService = TestBed.inject(MetisConversationService);
            const createChatSpy = jest.spyOn(metisConversationService, 'createOneToOneChatWithId').mockReturnValue(of(new HttpResponse({ status: 200 })) as any);

            const event = new MouseEvent('click');
            component.openConversationDetailDialog(event, ConversationDetailTabs.INFO);

            const testUserId = 42;
            fakeUserNameClicked$.next(testUserId);
            tick();

            expect(fakeModalRef.dismiss).toHaveBeenCalled();
            expect(createChatSpy).toHaveBeenCalledWith(testUserId);
        }));

        it('should not subscribe to userNameClicked if the modal instance does not have that property', fakeAsync(() => {
            const fakeModalRef: NgbModalRef = {
                componentInstance: {
                    course: undefined,
                    activeConversation: undefined,
                    selectedTab: undefined,
                    initialize: jest.fn(),
                },
                dismiss: jest.fn(),
                result: Promise.resolve(),
            } as any;

            const modalService = TestBed.inject(NgbModal);
            const metisConversationService = TestBed.inject(MetisConversationService);
            const createChatSpy = jest.spyOn(metisConversationService, 'createOneToOneChatWithId');

            jest.spyOn(modalService, 'open').mockReturnValue(fakeModalRef);
            const event = new MouseEvent('click');
            component.openConversationDetailDialog(event, ConversationDetailTabs.INFO);
            tick();

            expect(createChatSpy).not.toHaveBeenCalled();
        }));

        it('should always open info tab when conversation is one-to-one chat', fakeAsync(() => {
            const oneToOneChat = generateOneToOneChatDTO({});
            component.activeConversation = oneToOneChat;

            const fakeModalRef: NgbModalRef = {
                componentInstance: {
                    course: undefined,
                    activeConversation: undefined,
                    selectedTab: null,
                    initialize: jest.fn(),
                },
                result: Promise.resolve(),
                dismiss: jest.fn(),
            } as any;

            const modalService = TestBed.inject(NgbModal);
            jest.spyOn(modalService, 'open').mockReturnValue(fakeModalRef);

            const event = new MouseEvent('click');
            component.openConversationDetailDialog(event, ConversationDetailTabs.MEMBERS);
            tick();

            expect(fakeModalRef.componentInstance.selectedTab).toEqual(ConversationDetailTabs.INFO);
        }));

        it('should emit onUpdateSidebar when conversation detail dialog is closed', fakeAsync(() => {
            const modalService = TestBed.inject(NgbModal);
            const fakeModalRef: NgbModalRef = {
                componentInstance: {
                    course: undefined,
                    activeConversation: undefined,
                    selectedTab: undefined,
                    initialize: jest.fn(),
                },
                dismiss: jest.fn(),
                result: Promise.resolve(),
            } as any;

            jest.spyOn(modalService, 'open').mockReturnValue(fakeModalRef);

            const onUpdateSidebarSpy = jest.spyOn(component.onUpdateSidebar, 'emit');

            const event = new MouseEvent('click');
            component.openConversationDetailDialog(event, ConversationDetailTabs.INFO);
            tick();

            expect(onUpdateSidebarSpy).toHaveBeenCalledOnce();
        }));

        it('should emit collapseSearch when toggleSearchBar is called', () => {
            const collapseSearchSpy = jest.spyOn(component.onSearchClick, 'emit');
            component.toggleSearchBar();

            expect(collapseSearchSpy).toHaveBeenCalledOnce();
        });

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
            fixture.changeDetectorRef.detectChanges();
            detailButton.click();
            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(ConversationDetailDialogComponent, defaultFirstLayerDialogOptions);
                expect(mockModalRef.componentInstance.course).toEqual(course);
                expect(mockModalRef.componentInstance.activeConversation).toEqual(activeConversation);

                const expectedTab = component.getAsOneToOneChat(activeConversation) ? ConversationDetailTabs.INFO : tab;
                expect(mockModalRef.componentInstance.selectedTab).toEqual(expectedTab);
            });
        }
    });
});
