import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { Location } from '@angular/common';
import { RouterTestingModule } from '@angular/router/testing';
import { ConversationSidebarEntryComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/conversation-sidebar-section/conversation-sidebar-entry/conversation-sidebar-entry.component';
import { NgbDropdownMocksModule } from '../../../../../../../helpers/mocks/directive/ngbDropdownMocks.module';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../../../helpers/conversationExampleModels';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ChannelIconComponent } from 'app/overview/course-conversations/other/channel-icon/channel-icon.component';
import { GroupChatIconComponent } from 'app/overview/course-conversations/other/group-chat-icon/group-chat-icon.component';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { defaultFirstLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { isOneToOneChatDto } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { ChannelDTO, ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../../../helpers/mocks/service/mock-metis-service.service';
import { CourseLectureDetailsComponent } from 'app/overview/course-lectures/course-lecture-details.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/exam-detail.component';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { NotificationService } from 'app/shared/notification/notification.service';

const examples: ConversationDto[] = [
    generateOneToOneChatDTO({}),
    generateExampleGroupChatDTO({}),
    generateExampleChannelDTO({}),
    generateExampleChannelDTO({ subType: ChannelSubType.EXERCISE, subTypeReferenceId: 1 }),
    generateExampleChannelDTO({ subType: ChannelSubType.LECTURE, subTypeReferenceId: 1 }),
    generateExampleChannelDTO({ subType: ChannelSubType.EXAM, subTypeReferenceId: 1 }),
];

examples.forEach((conversation) => {
    describe('ConversationSidebarEntryComponent with ' + (conversation instanceof ChannelDTO ? conversation.subType + ' ' : '') + conversation.type, () => {
        let component: ConversationSidebarEntryComponent;
        let fixture: ComponentFixture<ConversationSidebarEntryComponent>;
        let conversationService: ConversationService;
        let updateIsFavoriteSpy: jest.SpyInstance;
        let updateIsHiddenSpy: jest.SpyInstance;
        let updateIsMutedSpy: jest.SpyInstance;
        let location: Location;
        const course = { id: 1 } as any;
        const activeConversation = generateExampleGroupChatDTO({ id: 99 });

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [
                    NgbDropdownMocksModule,
                    RouterTestingModule.withRoutes([
                        { path: 'courses/:courseId/lectures/:lectureId', component: CourseLectureDetailsComponent },
                        { path: 'courses/:courseId/exercises/:exerciseId', component: CourseExerciseDetailsComponent },
                        { path: 'courses/:courseId/exams/:examId', component: ExamDetailComponent },
                    ]),
                ],
                declarations: [
                    ConversationSidebarEntryComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(FaIconComponent),
                    MockComponent(ChannelIconComponent),
                    MockComponent(GroupChatIconComponent),
                ],
                providers: [
                    MockProvider(ConversationService),
                    MockProvider(AlertService),
                    MockProvider(NotificationService),
                    MockProvider(NgbModal),
                    { provide: MetisService, useClass: MockMetisService },
                ],
            }).compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ConversationSidebarEntryComponent);
            conversationService = TestBed.inject(ConversationService);
            updateIsFavoriteSpy = jest.spyOn(conversationService, 'updateIsFavorite').mockReturnValue(of(new HttpResponse<void>()));
            updateIsHiddenSpy = jest.spyOn(conversationService, 'updateIsHidden').mockReturnValue(of(new HttpResponse<void>()));
            updateIsMutedSpy = jest.spyOn(conversationService, 'updateIsMuted').mockReturnValue(of(new HttpResponse<void>()));

            location = TestBed.inject(Location);

            component = fixture.componentInstance;
            component.conversation = conversation;
            component.activeConversation = activeConversation;
            component.course = course;
            fixture.detectChanges();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should call updateIsFavorite when button is clicked', fakeAsync(() => {
            const conversationFavoriteStatusChangeSpy = jest.spyOn(component.conversationIsFavoriteDidChange, 'emit');
            const button = fixture.debugElement.nativeElement.querySelector('.favorite');
            button.click();
            tick(501);
            expect(updateIsFavoriteSpy).toHaveBeenCalledOnce();
            expect(updateIsFavoriteSpy).toHaveBeenCalledWith(course.id, conversation.id, true);
            expect(conversationFavoriteStatusChangeSpy).toHaveBeenCalledOnce();
        }));

        it('should call updateIsHidden when button is clicked', fakeAsync(() => {
            const conversationIsHiddenDidChangeSpy = jest.spyOn(component.conversationIsHiddenDidChange, 'emit');
            const button = fixture.debugElement.nativeElement.querySelector('.hide');
            button.click();
            tick(501);
            expect(updateIsHiddenSpy).toHaveBeenCalledOnce();
            expect(updateIsHiddenSpy).toHaveBeenCalledWith(course.id, conversation.id, true);
            expect(conversationIsHiddenDidChangeSpy).toHaveBeenCalledOnce();
        }));

        it('should call updateIsMuted when button is clicked', fakeAsync(() => {
            const conversationIsMutedDidChangeSpy = jest.spyOn(component.conversationIsMutedDidChange, 'emit');
            const button = fixture.debugElement.nativeElement.querySelector('.mute');
            button.click();
            tick(501);
            expect(updateIsMutedSpy).toHaveBeenCalledOnce();
            expect(updateIsMutedSpy).toHaveBeenCalledWith(course.id, conversation.id, true);
            expect(conversationIsMutedDidChangeSpy).toHaveBeenCalledOnce();
        }));

        it('should open conversation detail with setting tab if setting button is clicked', fakeAsync(() => {
            if (isOneToOneChatDto(conversation)) {
                const button = fixture.debugElement.nativeElement.querySelector('.setting');
                expect(button).toBeFalsy(); // should not be present for one-to-one chats
            } else {
                const button = fixture.debugElement.nativeElement.querySelector('.setting');
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
                button.click();
                fixture.whenStable().then(() => {
                    expect(openDialogSpy).toHaveBeenCalledOnce();
                    expect(openDialogSpy).toHaveBeenCalledWith(ConversationDetailDialogComponent, defaultFirstLayerDialogOptions);
                    expect(mockModalRef.componentInstance.course).toEqual(course);
                    expect(mockModalRef.componentInstance.activeConversation).toEqual(conversation);
                    expect(mockModalRef.componentInstance.selectedTab).toEqual(ConversationDetailTabs.SETTINGS);
                });
            }
        }));

        if (conversation instanceof ChannelDTO && conversation.subType !== ChannelSubType.GENERAL) {
            it(
                'should navigate to ' + conversation.subType,
                fakeAsync(() => {
                    const button = fixture.debugElement.nativeElement.querySelector('.sub-type-reference');
                    button.click();
                    tick();

                    // Assert that the router has navigated to the correct link
                    expect(location.path()).toBe('/courses/1/' + conversation.subType + 's/1');
                }),
            );
        }
    });
});
