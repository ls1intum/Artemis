import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OneToOneChatService } from 'app/communication/conversations/service/one-to-one-chat.service';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';

describe('OneToOneChatService', () => {
    setupTestBed({ zoneless: true });

    let service: OneToOneChatService;
    let httpMock: HttpTestingController;
    let conversationServiceMock: { convertDateFromServer: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        conversationServiceMock = {
            convertDateFromServer: vi.fn((response) => response),
        } as any;

        TestBed.configureTestingModule({
            providers: [OneToOneChatService, provideHttpClient(), provideHttpClientTesting(), { provide: ConversationService, useValue: conversationServiceMock }],
        });

        service = TestBed.inject(OneToOneChatService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('create method', () => {
        it('should create a one-to-one chat with a login', () => {
            const courseId = 1;
            const loginOfChatPartner = 'testuser';
            const mockResponse: OneToOneChatDTO = {
                id: 1,
                creationDate: dayjs(),
            };

            service.create(courseId, loginOfChatPartner).subscribe((response) => {
                expect(response.body).toEqual(mockResponse);
            });

            const req = httpMock.expectOne(`/api/communication/courses/${courseId}/one-to-one-chats`);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual([loginOfChatPartner]);
            req.flush(mockResponse);
            expect(conversationServiceMock.convertDateFromServer).toHaveBeenCalled();
        });
    });

    describe('createWithId method', () => {
        it('should create a one-to-one chat with a user ID', () => {
            const courseId = 1;
            const userIdOfChatPartner = 42;
            const mockResponse: OneToOneChatDTO = {
                id: 1,
                creationDate: dayjs(),
            };

            service.createWithId(courseId, userIdOfChatPartner).subscribe((response) => {
                expect(response.body).toEqual(mockResponse);
            });

            const req = httpMock.expectOne(`/api/communication/courses/${courseId}/one-to-one-chats/${userIdOfChatPartner}`);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toBeNull();
            req.flush(mockResponse);

            expect(conversationServiceMock.convertDateFromServer).toHaveBeenCalled();
        });
    });
});
