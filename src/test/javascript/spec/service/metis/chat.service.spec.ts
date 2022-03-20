import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';

import { ChatService } from 'app/shared/metis/chat.service';
import { metisChatSessionsOfUser1, metisChatSessionToCreateUser1, metisCourse } from '../../helpers/sample/metis-sample-data';
import { ChatSession } from 'app/entities/metis/chat.session/chat.session.model';

describe('ChatSession Service', () => {
    let chatService: ChatService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        chatService = TestBed.inject(ChatService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Chat Session service methods', () => {
        it('should create a ChatSession', fakeAsync(() => {
            const returnedFromService = { ...metisChatSessionToCreateUser1 };
            const expected = { ...returnedFromService };
            chatService
                .create(1, new ChatSession())
                .pipe(take(1))
                .subscribe((response) => expect(response.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all ChatSessions of a user for a course', fakeAsync(() => {
            const returnedFromService = metisChatSessionsOfUser1;
            const expected = returnedFromService;
            chatService
                .getChatSessionsOfUser(metisCourse.id!)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));
    });
});
