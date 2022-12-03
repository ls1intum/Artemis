import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';

import { ConversationService } from 'app/shared/metis/conversation.service';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

import { conversationToCreateUser1, metisConversationsOfUser1, metisCourse } from '../../helpers/sample/metis-sample-data';

describe('ConversationService', () => {
    let conversationService: ConversationService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        conversationService = TestBed.inject(ConversationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('ConversationService methods', () => {
        it('should create a Conversation', fakeAsync(() => {
            const returnedFromService = { ...conversationToCreateUser1 };
            const expected = { ...returnedFromService };
            conversationService
                .create(1, new Conversation())
                .pipe(take(1))
                .subscribe((response) => expect(response.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all conversations of a user for a course', fakeAsync(() => {
            const returnedFromService = metisConversationsOfUser1;
            const expected = returnedFromService;
            conversationService
                .getConversationsOfUser(metisCourse.id!)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));
    });
});
