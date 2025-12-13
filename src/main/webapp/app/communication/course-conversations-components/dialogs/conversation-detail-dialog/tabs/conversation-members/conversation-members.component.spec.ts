import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import '@angular/localize/init';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { signal } from '@angular/core';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ConversationMemberSearchFilter, ConversationService } from 'app/communication/conversations/service/conversation.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import {
    generateExampleChannelDTO,
    generateExampleGroupChatDTO,
    generateOneToOneChatDTO,
} from '../../../../../../../../../test/javascript/spec/helpers/sample/conversationExampleModels';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { defaultSecondLayerDialogOptions } from 'app/communication/course-conversations-components/other/conversation.util';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ConversationMembersComponent } from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/tabs/conversation-members/conversation-members.component';
import { ConversationAddUsersDialogComponent } from 'app/communication/course-conversations-components/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';

const examples: ConversationDTO[] = [generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({} as ChannelDTO)];

examples.forEach((activeConversation) => {
    describe('ConversationMembersComponent with ' + activeConversation.type, () => {
        let component: ConversationMembersComponent;
        let fixture: ComponentFixture<ConversationMembersComponent>;
        let searchMembersOfConversationSpy: jest.SpyInstance;
        const course = { id: 1 } as Course;
        const canAddUsersToConversation = jest.fn();

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [
                    ConversationMembersComponent,
                    FormsModule,
                    ReactiveFormsModule,
                    FaIconComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(ItemCountComponent),
                    MockDirective(TranslateDirective),
                ],
                providers: [
                    MockProvider(ConversationService),
                    MockProvider(AlertService),
                    MockProvider(NgbModal),
                    provideHttpClient(),
                    provideHttpClientTesting(),
                    { provide: TranslateService, useClass: MockTranslateService },
                    SessionStorageService,
                ],
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
            fixture.componentRef.setInput('activeConversationInput', activeConversation);
            fixture.componentRef.setInput('course', course);
            component.activeConversation = signal<ConversationDTO>(activeConversation);
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
            fixture.changeDetectorRef.detectChanges();
            expect(fixture.debugElement.nativeElement.querySelector('.addUsers')).toBeFalsy();

            canAddUsersToConversation.mockReturnValue(true);
            fixture.changeDetectorRef.detectChanges();
            const addUsersButton = fixture.debugElement.nativeElement.querySelector('.addUsers');
            expect(addUsersButton).toBeTruthy();

            const modalService = TestBed.inject(NgbModal);
            const mockModalRef = {
                componentInstance: { course: component.course(), activeConversation: component.activeConversation(), initialize: () => {} },
                result: Promise.resolve(),
            };
            const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
            fixture.changeDetectorRef.detectChanges();

            addUsersButton.click();
            tick(301);
            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(ConversationAddUsersDialogComponent, defaultSecondLayerDialogOptions);
                expect(mockModalRef.componentInstance.course).toEqual(course);
                expect(mockModalRef.componentInstance.activeConversation?.id).toEqual(activeConversation.id);
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
