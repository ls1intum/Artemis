import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../../helpers/conversationExampleModels';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbCollapseMocksModule } from '../../../../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { MockLocalStorageService } from '../../../../../../helpers/mocks/service/mock-local-storage.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { ConversationSidebarSectionComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/conversation-sidebar-section/conversation-sidebar-section.component';
import { LocalStorageService } from 'ngx-webstorage';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: '[jhi-conversation-sidebar-entry]',
    template: '',
})
class ConversationSidebarEntryStubComponent {
    @Input()
    course: Course;

    @Input()
    conversation: ConversationDto;

    @Input()
    activeConversation: ConversationDto | undefined;

    @Output()
    settingsChanged = new EventEmitter<void>();

    @Output()
    conversationHiddenStatusChange = new EventEmitter<void>();

    @Output()
    conversationFavoriteStatusChange = new EventEmitter<void>();
}

const examples: (ConversationDto | undefined)[] = [undefined, generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({})];
examples.forEach((activeConversation) => {
    describe('ConversationSidebarSectionComponent with ' + (activeConversation?.type || 'no active conversation'), () => {
        let component: ConversationSidebarSectionComponent;
        let fixture: ComponentFixture<ConversationSidebarSectionComponent>;
        const course = { id: 1 } as Course;

        const hiddenConversation = generateExampleChannelDTO({ isHidden: true });
        const visibleConversation = generateExampleChannelDTO({ isHidden: false });

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [NgbCollapseMocksModule],
                declarations: [ConversationSidebarSectionComponent, ConversationSidebarEntryStubComponent, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe)],
                providers: [{ provide: LocalStorageService, useClass: MockLocalStorageService }, MockProvider(ConversationService)],
            }).compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ConversationSidebarSectionComponent);
            component = fixture.componentInstance;
            component.course = course;
            component.activeConversation = activeConversation;
            component.label = 'label';
            component.headerKey = 'headerKey';
            component.conversations = [hiddenConversation, visibleConversation];
            fixture.detectChanges();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should separate hidden and visible conversations', () => {
            expect(component.hiddenConversations).toEqual([hiddenConversation]);
            expect(component.visibleConversations).toEqual([visibleConversation]);
            expect(component.allConversations).toEqual([hiddenConversation, visibleConversation]);
            expect(component.numberOfConversations).toBe(2);
        });

        it('should store collapsed status in local storage', () => {
            expect(component.localStorageService.retrieve(component.storageKey)).toBeFalse();
            component.toggleCollapsed();
            expect(component.localStorageService.retrieve(component.storageKey)).toBeTrue();
        });

        it('should hide if empty only if hideIfEmpty is set', () => {
            component.allConversations = [];
            expect(component.hide()).toBeTrue();

            component.hideIfEmpty = false;
            expect(component.hide()).toBeFalse();
        });

        it('should hide if search term is entered and conversations are empty and ignore hideIfEmpty flag', () => {
            component.allConversations = [];
            component.hideIfEmpty = false;

            component.searchTerm = 'test';
            expect(component.hide()).toBeTrue();

            component.searchTerm = '';
            expect(component.hide()).toBeFalse();
        });
    });
});
