import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { CourseConversationsNotificationsService } from 'app/overview/course-conversations-notifications-service';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { OneToOneChat } from 'app/entities/metis/conversation/one-to-one-chat.model';

describe('CourseConversationsNotificationService', () => {
    let service: CourseConversationsNotificationsService;
    let conversation: Conversation;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(CourseConversationsNotificationsService);

        conversation = new OneToOneChat();
        conversation.id = 1;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should make http call to conversations-for-notifications only when cache is empty', fakeAsync(() => {
        const http = TestBed.inject(HttpClient);
        const response: HttpResponse<Conversation[]> = new HttpResponse({
            body: [conversation],
            status: 200,
        });
        const getSpy = jest.spyOn(http, 'get').mockReturnValue(of(response));

        // cache empty
        expect(service.coursesForNotifications$).toBeUndefined();
        service.getConversationsForNotifications().subscribe((conversations) => {
            expect(conversations).toEqual([conversation]);
            expect(getSpy).toHaveBeenCalledWith('api/courses/conversations-for-notifications', { observe: 'response' });
            expect(getSpy).toHaveBeenCalledOnce();
            getSpy.mockClear();
            // cache not empty anymore
            expect(service.coursesForNotifications$).toBeDefined();
            service.getConversationsForNotifications().subscribe((conversations2) => {
                expect(conversations2).toEqual([conversation]);
                expect(getSpy).not.toHaveBeenCalled();
            });
        });
        tick();
        tick();
    }));
});
