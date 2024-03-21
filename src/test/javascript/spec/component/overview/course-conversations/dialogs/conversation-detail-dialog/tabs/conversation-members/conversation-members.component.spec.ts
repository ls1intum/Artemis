import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConversationMembersComponent } from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/tabs/conversation-members/conversation-members.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Course } from 'app/entities/course.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ConversationMemberSearchFilter, ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { AlertService } from 'app/core/util/alert.service';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../../../helpers/conversationExampleModels';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ConversationAddUsersDialogComponent } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import { defaultSecondLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { NgbPaginationMocksModule } from '../../../../../../../helpers/mocks/directive/ngbPaginationMocks.module';

// eslint-disable-next-line @angular-eslint/component-selector
@Component({ selector: '[jhi-conversation-member-row]', template: '' })
class ConversationMemberRowStubComponent {
    @Input()
    activeConversation: ConversationDTO;

    @Input()
    course: Course;

    @Output()
    changePerformed = new EventEmitter<void>();

    @Input()
    conversationMember: ConversationUserDTO;
}
const examples: ConversationDTO[] = [generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({})];

examples.forEach((activeConversation) => {
    describe('ConversationMembersComponent with ' + activeConversation.type, () => {
        let component: ConversationMembersComponent;
        let fixture: ComponentFixture<ConversationMembersComponent>;
        let searchMembersOfConversationSpy: jest.SpyInstance;
        const course = { id: 1 } as Course;
        const canAddUsersToConversation = jest.fn();

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [FormsModule, ReactiveFormsModule, NgbPaginationMocksModule],
                declarations: [
                    ConversationMembersComponent,
                    ConversationMemberRowStubComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(FaIconComponent),
                    MockComponent(ItemCountComponent),
                ],
                providers: [MockProvider(ConversationService), MockProvider(AlertService), MockProvider(NgbModal)],
            }).compileComponents();
        }));

        beforeEach(() => {
            const conversationService = TestBed.inject(ConversationService);
            searchMembersOfConversationSpy = jest.spyOn(conversationService, 'searchMembersOfConversation');

            let headers = new HttpHeaders();
            headers = headers.append('X-Total-Count', '2');
            searchMembersOfConversationSpy.mockReturnValue(
                of(
                    new HttpResponse({
                        headers,
                        body: [{ id: 1, name: 'user1', login: 'user1' } as ConversationUserDTO, { id: 2, name: 'user2', login: 'user2' } as ConversationUserDTO],
                    }),
                ),
            );
            canAddUsersToConversation.mockReturnValue(true);

            fixture = TestBed.createComponent(ConversationMembersComponent);
            component = fixture.componentInstance;
            component.activeConversation = activeConversation;
            component.course = course;
            component.canAddUsersToConversation = canAddUsersToConversation;
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should call searchMembersOfConversation on init with empty search term (searches all)', fakeAsync(() => {
            fixture.detectChanges();
            tick(301);
            expectSearchPerformed('');
        }));

        it('should call searchMembersOfConversation on search term change', fakeAsync(() => {
            fixture.detectChanges();
            tick(301);
            searchMembersOfConversationSpy.mockClear();

            const searchInput = fixture.debugElement.nativeElement.querySelector('#searchQuery');
            searchInput.value = ' User 1 ';
            searchInput.dispatchEvent(new Event('input'));

            tick(301);
            expectSearchPerformed('user 1');
        }));

        it('should call searchMembersOfConversation when filter is changed', fakeAsync(() => {
            fixture.detectChanges();
            tick(301);
            searchMembersOfConversationSpy.mockClear();

            const searchFilter = fixture.debugElement.nativeElement.querySelector('#searchFilter');
            searchFilter.value = ConversationMemberSearchFilter.INSTRUCTOR;
            searchFilter.dispatchEvent(new Event('change'));
            tick(301);
            expect(component.selectedFilter).toEqual('' + ConversationMemberSearchFilter.INSTRUCTOR);
            expectSearchPerformed('', ConversationMemberSearchFilter.INSTRUCTOR);
        }));

        it('should perform search on transition to page 2', fakeAsync(() => {
            fixture.detectChanges();
            tick(301);
            searchMembersOfConversationSpy.mockClear();

            component.transition();
            tick(301);
            expectSearchPerformed('', ConversationMemberSearchFilter.ALL);
        }));

        it('should open add users dialog when button is pressed', fakeAsync(() => {
            fixture.detectChanges();
            tick(301);
            searchMembersOfConversationSpy.mockClear();

            canAddUsersToConversation.mockReturnValue(false);
            fixture.detectChanges();
            expect(fixture.debugElement.nativeElement.querySelector('.addUsers')).toBeFalsy();

            canAddUsersToConversation.mockReturnValue(true);
            fixture.detectChanges();
            const addUsersButton = fixture.debugElement.nativeElement.querySelector('.addUsers');
            expect(addUsersButton).toBeTruthy();

            const modalService = TestBed.inject(NgbModal);
            const mockModalRef = {
                componentInstance: { course: undefined, activeConversation: undefined, initialize: () => {} },
                result: Promise.resolve(),
            };
            const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
            fixture.detectChanges();

            addUsersButton.click();
            tick(301);
            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(ConversationAddUsersDialogComponent, defaultSecondLayerDialogOptions);
                expect(mockModalRef.componentInstance.course).toEqual(course);
                expect(mockModalRef.componentInstance.activeConversation).toEqual(activeConversation);
            });
        }));

        function expectSearchPerformed(expectedSearchTerm: string, expectedFilter: ConversationMemberSearchFilter = ConversationMemberSearchFilter.ALL) {
            expect(searchMembersOfConversationSpy).toHaveBeenCalledOnce();
            expect(searchMembersOfConversationSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, expectedSearchTerm, 0, 10, expectedFilter);
            expect(component.members).toHaveLength(2);
            expect(component.totalItems).toBe(2);
        }
    });
});
