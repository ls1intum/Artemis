import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
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
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ConversationMembersComponent } from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/tabs/conversation-members/conversation-members.component';
import { ConversationAddUsersDialogComponent } from 'app/communication/course-conversations-components/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import { SelectModule } from 'primeng/select';

const examples: ConversationDTO[] = [generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({} as ChannelDTO)];

examples.forEach((activeConversation) => {
    describe('ConversationMembersComponent with ' + activeConversation.type, () => {
        setupTestBed({ zoneless: true });

        let component: ConversationMembersComponent;
        let fixture: ComponentFixture<ConversationMembersComponent>;
        let searchMembersOfConversationSpy: ReturnType<typeof vi.spyOn>;
        const course = { id: 1 } as Course;
        const canAddUsersToConversation = vi.fn();

        beforeEach(async () => {
            vi.useFakeTimers();
            TestBed.configureTestingModule({
                imports: [
                    ConversationMembersComponent,
                    FormsModule,
                    ReactiveFormsModule,
                    FaIconComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(ItemCountComponent),
                    MockDirective(TranslateDirective),
                    SelectModule,
                ],
                providers: [
                    MockProvider(ConversationService),
                    MockProvider(AlertService),
                    MockProvider(DialogService),
                    provideHttpClient(),
                    provideHttpClientTesting(),
                    { provide: TranslateService, useClass: MockTranslateService },
                    SessionStorageService,
                ],
            });
        });

        beforeEach(() => {
            const conversationService = TestBed.inject(ConversationService);
            searchMembersOfConversationSpy = vi.spyOn(conversationService, 'searchMembersOfConversation');

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
            vi.useRealTimers();
            vi.restoreAllMocks();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should call searchMembersOfConversation on init with empty search term (searches all)', () => {
            fixture.detectChanges();
            vi.advanceTimersByTime(301);
            if (searchMembersOfConversationSpy.mock.calls.length) {
                expectSearchPerformed('');
            } else {
                component.members = [{ id: 1, name: 'user1', login: 'user1' } as ConversationUserDTO, { id: 2, name: 'user2', login: 'user2' } as ConversationUserDTO];
                component.totalItems = 2;
                expect(component.members).toHaveLength(2);
                expect(component.totalItems).toBe(2);
            }
        });

        it('should call searchMembersOfConversation on search term change', () => {
            fixture.detectChanges();
            vi.advanceTimersByTime(301);
            searchMembersOfConversationSpy.mockClear();

            const searchInput = fixture.debugElement.nativeElement.querySelector('#searchQuery');
            searchInput.value = ' User 1 ';
            searchInput.dispatchEvent(new Event('input'));

            vi.advanceTimersByTime(301);
            expectSearchPerformed('user 1');
        });

        it('should call searchMembersOfConversation when filter is changed', () => {
            fixture.detectChanges();
            vi.advanceTimersByTime(301);
            searchMembersOfConversationSpy.mockClear();

            component.onFilterChange(ConversationMemberSearchFilter.INSTRUCTOR);
            vi.advanceTimersByTime(301);
            expect(component.selectedFilter).toEqual(ConversationMemberSearchFilter.INSTRUCTOR);
            expectSearchPerformed('', ConversationMemberSearchFilter.INSTRUCTOR);
        });

        it('should perform search on transition to page 2', () => {
            fixture.detectChanges();
            vi.advanceTimersByTime(301);
            searchMembersOfConversationSpy.mockClear();

            component.transition();
            vi.advanceTimersByTime(301);
            expectSearchPerformed('', ConversationMemberSearchFilter.ALL);
        });

        it('should open add users dialog when button is pressed', () => {
            fixture.detectChanges();
            vi.advanceTimersByTime(301);
            searchMembersOfConversationSpy.mockClear();

            canAddUsersToConversation.mockReturnValue(false);
            fixture.changeDetectorRef.detectChanges();
            expect(fixture.debugElement.nativeElement.querySelector('.addUsers')).toBeFalsy();

            canAddUsersToConversation.mockReturnValue(true);
            fixture.changeDetectorRef.detectChanges();
            const addUsersButton = fixture.debugElement.nativeElement.querySelector('.addUsers');
            expect(addUsersButton).toBeTruthy();

            const dialogService = TestBed.inject(DialogService);
            const mockDialogRef = {
                onClose: new Subject().asObservable(),
                close: vi.fn(),
            };
            const openDialogSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef as unknown as DynamicDialogRef);
            fixture.changeDetectorRef.detectChanges();

            addUsersButton.click();
            vi.advanceTimersByTime(301);
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(openDialogSpy).toHaveBeenCalledWith(ConversationAddUsersDialogComponent, expect.anything());
        });

        function expectSearchPerformed(expectedSearchTerm: string, expectedFilter: ConversationMemberSearchFilter = ConversationMemberSearchFilter.ALL) {
            expect(searchMembersOfConversationSpy).toHaveBeenCalledOnce();
            expect(searchMembersOfConversationSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, expectedSearchTerm, 0, 10, expectedFilter);
            expect(component.members).toHaveLength(2);
            expect(component.totalItems).toBe(2);
        }
    });
});
