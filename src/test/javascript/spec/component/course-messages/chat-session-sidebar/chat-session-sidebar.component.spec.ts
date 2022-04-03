import { ChatSessionSidebarComponent } from 'app/overview/course-messages/chat-sessions-sidebar/chat-session-sidebar.component';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { ChatSessionService } from 'app/shared/metis/chat-session.service';
import { MockChatSessionService } from '../../../helpers/mocks/service/mock-chat-session.service';
import { CourseMessagesService } from 'app/shared/metis/course.messages.service';

import { chatSessionBetweenUser1User2, chatSessionsOfUser1, metisCourse, metisTutor, metisUser2 } from '../../../helpers/sample/metis-sample-data';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

describe('ChatSessionSidebarComponent', () => {
    let component: ChatSessionSidebarComponent;
    let fixture: ComponentFixture<ChatSessionSidebarComponent>;
    let courseManagementService: CourseManagementService;
    let emitActiveChatSessionSpy: jest.SpyInstance;

    const id = metisCourse.id;
    const parentRoute = {
        parent: {
            params: of({ id }),
            queryParams: of({ searchText: '' }),
        },
    } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, NgxDatatableModule],
            declarations: [
                ChatSessionSidebarComponent,
                MockComponent(FaIconComponent),
                MockComponent(DataTableComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
            ],
            providers: [
                FormBuilder,
                MockProvider(SessionStorageService),
                { provide: CourseMessagesService, useClass: CourseMessagesService },
                { provide: ChatSessionService, useClass: MockChatSessionService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: DataTableComponent, useClass: DataTableComponent },
            ],
        })
            .compileComponents()
            .then(() => {
                courseManagementService = TestBed.inject(CourseManagementService);
                jest.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of({ body: metisCourse }) as Observable<HttpResponse<Course>>);
                fixture = TestBed.createComponent(ChatSessionSidebarComponent);
                component = fixture.componentInstance;
                emitActiveChatSessionSpy = jest.spyOn(component.selectChatSession, 'emit');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set course and user chat sessions, activeChatSession on initialization and emitActiveChatSession for other components', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.course).toBe(metisCourse);
        expect(component.chatSessions).toBe(chatSessionsOfUser1);
        expect(component.activeChatSession).toBe(chatSessionsOfUser1.first());
        expect(emitActiveChatSessionSpy).toBeCalledTimes(1);
        expect(emitActiveChatSessionSpy).toBeCalledWith(chatSessionsOfUser1.first());
    }));

    it('should search for other users via the searchbar', () => {
        const userServiceSpy = jest.spyOn(courseManagementService, 'searchOtherUsersInCourse').mockReturnValue(of(new HttpResponse({ body: [metisUser2] })));
        fixture.detectChanges();

        const search = component.searchUsersWithinCourse(of({ text: metisUser2.name!, entities: [metisUser2] }));
        fixture.detectChanges();

        // Check if the observable output matches our expectancies
        search.subscribe((a) => {
            expect(a).toEqual([{ id: metisUser2.id, login: metisUser2.login }]);
            expect(component.searchNoResults).toBeFalse();
            expect(component.searchFailed).toBeFalse();
        });

        expect(userServiceSpy).toHaveBeenCalledTimes(1);
    });

    it('should return empty if search with less than 3 characters', () => {
        const userServiceSpy = jest.spyOn(courseManagementService, 'searchOtherUsersInCourse');
        fixture.detectChanges();

        const search = component.searchUsersWithinCourse(of({ text: 'ab', entities: [] }));
        fixture.detectChanges();

        // Check if the observable output matches our expectancies
        search.subscribe((a) => {
            expect(a).toEqual([]);
            expect(component.searchNoResults).toBeFalse();
            expect(component.searchFailed).toBeFalse();
        });

        expect(userServiceSpy).toHaveBeenCalledTimes(0);
    });

    it('should set searchNoResults true when no user found while searching', () => {
        const userServiceSpy = jest.spyOn(courseManagementService, 'searchOtherUsersInCourse').mockReturnValue(of(new HttpResponse({ body: [] })));
        fixture.detectChanges();

        const search = component.searchUsersWithinCourse(of({ text: 'non-existing user name', entities: [] }));
        fixture.detectChanges();

        // Check if the observable output matches our expectancies
        search.subscribe((a) => {
            expect(a).toEqual([]);
            expect(component.searchNoResults).toBeTrue();
            expect(component.searchFailed).toBeFalse();
        });

        expect(userServiceSpy).toHaveBeenCalledTimes(1);
    });

    it('should find if there is existing chatSession with searched user', fakeAsync(() => {
        component.ngOnInit();
        tick();

        const usersChatSessions = component.findChatSessionWithUser(metisUser2);
        expect(usersChatSessions).toEqual(chatSessionBetweenUser1User2);
    }));

    it('should return undefined if there is no existing chatSession with searched user', fakeAsync(() => {
        component.ngOnInit();
        tick();

        const usersChatSessions = component.findChatSessionWithUser(metisTutor);
        expect(usersChatSessions).toBeUndefined();
    }));

    it('should handle selection of a user from the search list with existing chatSession', () => {
        const spy = jest.spyOn(component, 'findChatSessionWithUser');

        component.chatSessions = chatSessionsOfUser1;

        // chat session already exists, activeChatSession will be set and emitted for other postOverview component
        component.onAutocompleteSelect(metisUser2);
        fixture.detectChanges();

        expect(spy).toBeCalledTimes(1);
        expect(spy).toBeCalledWith(metisUser2);
        expect(component.activeChatSession).toBe(component.findChatSessionWithUser(metisUser2));
        expect(emitActiveChatSessionSpy).toBeCalledTimes(1);
    });

    it('should handle selection of a user from the search list without existing chatSession', () => {
        const spy = jest.spyOn(component, 'findChatSessionWithUser');
        component.chatSessions = chatSessionsOfUser1;

        // chat session doesn't exist, so it will be created and added to the beginning of the chatSessions
        component.onAutocompleteSelect(metisTutor);
        fixture.detectChanges();

        expect(spy).toBeCalledTimes(1);
        expect(spy).toBeCalledWith(metisTutor);
        expect(component.activeChatSession).toBe(component.findChatSessionWithUser(metisTutor));
        expect(emitActiveChatSessionSpy).toBeCalledTimes(1);
    });

    it('should format search result', () => {
        const resultString = component.searchResultFormatter(metisUser2);
        expect(resultString).toEqual(metisUser2.name);
    });

    it('should clear search text from user', () => {
        const resultString = component.clearUserSearchBar();
        expect(resultString).toEqual('');
    });

    it('should create and initialize chatSession correctly', fakeAsync(() => {
        component.ngOnInit();
        tick();

        const newChatSessionWithUser2 = component.createNewChatSessionWithUser(metisUser2);
        expect(newChatSessionWithUser2.course).toBe(metisCourse);
        expect(newChatSessionWithUser2.userChatSessions!.first()?.user).toBe(metisUser2);
    }));
});
