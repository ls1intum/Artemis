import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MetisService } from 'app/communication/service/metis.service';
import { MockPipe } from 'ng-mocks';
import { FormBuilder } from '@angular/forms';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { directMessageUser1, metisPostToCreateUser1 } from 'test/helpers/sample/metis-sample-data';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MessageReplyInlineInputComponent } from 'app/communication/message/message-reply-inline-input/message-reply-inline-input.component';
import { of, throwError } from 'rxjs';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Post } from 'app/communication/shared/entities/post.model';

describe('MessageReplyInlineInputComponent', () => {
    let component: MessageReplyInlineInputComponent;
    let fixture: ComponentFixture<MessageReplyInlineInputComponent>;
    let metisService: MetisService;
    let metisServiceCreateStub: jest.SpyInstance;
    let metisServiceUpdateStub: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [MessageReplyInlineInputComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FormBuilder,
                { provide: MetisService, useClass: MockMetisService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MessageReplyInlineInputComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceCreateStub = jest.spyOn(metisService, 'createAnswerPost');
                metisServiceUpdateStub = jest.spyOn(metisService, 'updateAnswerPost');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should invoke metis service with created message reply', fakeAsync(() => {
        component.posting = { ...metisPostToCreateUser1 };
        component.ngOnChanges();

        const newContent = 'new content';
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting,
            title: undefined,
        });
        tick();
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).toHaveBeenCalledExactlyOnceWith({ ...component.posting, content: newContent });
    }));

    it('should stop loading when metis service throws error during replying to message', fakeAsync(() => {
        metisServiceCreateStub.mockImplementation(() => throwError(() => new Error('error')));
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');

        component.posting = metisPostToCreateUser1;
        component.ngOnChanges();

        const newContent = 'new content';
        component.formGroup.setValue({
            content: newContent,
        });

        component.confirm();

        tick();
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).not.toHaveBeenCalled();
    }));

    it('should invoke metis service with edited message reply', fakeAsync(() => {
        component.posting = directMessageUser1;
        component.ngOnChanges();

        const editedContent = 'edited content';

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        expect(metisServiceUpdateStub).toHaveBeenCalledWith({
            ...component.posting,
            content: editedContent,
            title: undefined,
        });
        tick();
        expect(component.isLoading).toBeFalse();
    }));

    it('should stop loading when metis service throws error during message replying', fakeAsync(() => {
        metisServiceUpdateStub.mockImplementation(() => throwError(() => new Error('error')));

        component.posting = directMessageUser1;
        component.ngOnChanges();

        const editedContent = 'edited content';
        const onEditSpy = jest.spyOn(component.isModalOpen, 'emit');

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        tick();
        expect(component.isLoading).toBeFalse();
        expect(onEditSpy).not.toHaveBeenCalled();
    }));

    it('should toggle sendAsDirectMessage value', () => {
        expect(component.sendAsDirectMessage()).toBeFalse();
        component.toggleSendAsDirectMessage();
        expect(component.sendAsDirectMessage()).toBeTrue();
        component.toggleSendAsDirectMessage();
        expect(component.sendAsDirectMessage()).toBeFalse();
    });

    it('should reset form group with provided content', () => {
        component.posting = { content: 'old content' } as any;
        component.resetFormGroup('new content');
        expect(component.posting.content).toBe('new content');
        expect(component.formGroup.get('content')?.value).toBe('new content');
    });

    it('should create a direct‐message post and emit it when sendAsDirectMessage=true', fakeAsync(() => {
        const conv = { id: 42, title: 'Test Conversation' } as any;
        component.posting = { id: 1, content: '', post: { id: 99 } as any } as AnswerPost;
        (component as any).activeConversation = () => conv;
        component.sendAsDirectMessage.set(true);

        component.resetFormGroup();
        component.formGroup.setValue({ content: 'msg' });

        const answer = { ...component.posting, content: 'msg' } as AnswerPost;
        jest.spyOn(metisService, 'createAnswerPost').mockReturnValue(of(answer));

        const direct = { content: 'msg', conversation: conv, originalPostId: 99 } as Post;
        jest.spyOn(metisService, 'createPost').mockReturnValue(of(direct));

        const emitSpy = jest.spyOn(component.onCreate, 'emit');

        component.createPosting();
        tick();

        expect(metisService.createPost).toHaveBeenCalledWith(
            expect.objectContaining({
                content: 'msg',
                conversation: conv,
                originalPostId: 99,
            }),
        );
        expect(emitSpy).toHaveBeenCalledWith(direct);
        expect(component.isLoading).toBeFalse();
    }));
});
