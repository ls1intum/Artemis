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
import { DraftService } from 'app/communication/message/service/draft-message.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProvider } from 'ng-mocks';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MetisConversationService } from '../../service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('MessageReplyInlineInputComponent', () => {
    let component: MessageReplyInlineInputComponent;
    let fixture: ComponentFixture<MessageReplyInlineInputComponent>;
    let metisService: MetisService;
    let metisServiceCreateStub: jest.SpyInstance;
    let metisServiceUpdateStub: jest.SpyInstance;
    let draftService: DraftService;
    let accountService: AccountService;
    let consoleWarnSpy: jest.SpyInstance;

    beforeEach(() => {
        consoleWarnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
        return TestBed.configureTestingModule({
            declarations: [MessageReplyInlineInputComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FormBuilder,
                { provide: MetisService, useClass: MockMetisService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(DraftService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MessageReplyInlineInputComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                draftService = TestBed.inject(DraftService);
                accountService = TestBed.inject(AccountService);
                metisServiceCreateStub = jest.spyOn(metisService, 'createAnswerPost');
                metisServiceUpdateStub = jest.spyOn(metisService, 'updateAnswerPost');

                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        consoleWarnSpy?.mockRestore();
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
        expect(component.isLoading).toBeFalsy();
        expect(onCreateSpy).toHaveBeenCalledWith({ ...component.posting, content: newContent });
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
        expect(component.isLoading).toBeFalsy();
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
        expect(component.isLoading).toBeFalsy();
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
        expect(component.isLoading).toBeFalsy();
        expect(onEditSpy).not.toHaveBeenCalled();
    }));

    it('should reset form group with provided content', () => {
        component.posting = { content: 'old content' } as any;
        component.resetFormGroup('new content');
        expect(component.posting.content).toBe('new content');
        expect(component.formGroup.get('content')?.value).toBe('new content');
    });

    describe('Direct Message functionality', () => {
        let conv: any;
        let answerPost: AnswerPost;

        beforeEach(() => {
            conv = { id: 42, title: 'Test Conversation', type: 'CHANNEL' } as any;
            answerPost = { id: 1, content: '', post: { id: 99 } as any } as AnswerPost;
            component.posting = answerPost;
            (component as any).activeConversation = () => conv;
        });

        it('should toggle sendAsDirectMessage value', () => {
            expect(component.sendAsDirectMessage()).toBeFalsy();
            component.toggleSendAsDirectMessage();
            expect(component.sendAsDirectMessage()).toBeTruthy();
            component.toggleSendAsDirectMessage();
            expect(component.sendAsDirectMessage()).toBeFalsy();
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
            expect(component.isLoading).toBeFalsy();
        }));

        it('should create regular answer post when sendAsDirectMessage=false', fakeAsync(() => {
            component.sendAsDirectMessage.set(false);
            component.resetFormGroup();
            component.formGroup.setValue({ content: 'regular reply' });

            const createdAnswer = { ...answerPost, content: 'regular reply' } as AnswerPost;
            jest.spyOn(metisService, 'createAnswerPost').mockReturnValue(of(createdAnswer));
            const createPostSpy = jest.spyOn(metisService, 'createPost');
            const emitSpy = jest.spyOn(component.onCreate, 'emit');

            component.createPosting();
            tick();

            expect(metisService.createAnswerPost).toHaveBeenCalledWith(answerPost);
            expect(createPostSpy).not.toHaveBeenCalled();
            expect(emitSpy).toHaveBeenCalledWith(createdAnswer);
            expect(component.isLoading).toBeFalsy();
        }));

        it('should preserve original answer post content when creating direct message', fakeAsync(() => {
            component.sendAsDirectMessage.set(true);
            component.resetFormGroup();
            const originalContent = 'This is a very long message with special characters: @#$%^&*()';
            component.formGroup.setValue({ content: originalContent });

            const createdAnswer = { ...answerPost, content: originalContent } as AnswerPost;
            jest.spyOn(metisService, 'createAnswerPost').mockReturnValue(of(createdAnswer));

            const directPost = { content: originalContent, conversation: conv, originalPostId: 99 } as Post;
            jest.spyOn(metisService, 'createPost').mockReturnValue(of(directPost));

            const emitSpy = jest.spyOn(component.onCreate, 'emit');

            component.createPosting();
            tick();

            expect(metisService.createPost).toHaveBeenCalledWith(
                expect.objectContaining({
                    content: originalContent,
                    conversation: conv,
                    originalPostId: 99,
                }),
            );
            expect(emitSpy).toHaveBeenCalledWith(directPost);
        }));

        it('should handle empty content in direct message creation', fakeAsync(() => {
            component.sendAsDirectMessage.set(true);
            component.resetFormGroup();
            component.formGroup.setValue({ content: '' });

            const createdAnswer = { ...answerPost, content: '' } as AnswerPost;
            jest.spyOn(metisService, 'createAnswerPost').mockReturnValue(of(createdAnswer));

            const directPost = { content: '', conversation: conv, originalPostId: 99 } as Post;
            jest.spyOn(metisService, 'createPost').mockReturnValue(of(directPost));

            const emitSpy = jest.spyOn(component.onCreate, 'emit');

            component.createPosting();
            tick();

            expect(metisService.createPost).toHaveBeenCalledWith(
                expect.objectContaining({
                    content: '',
                    conversation: conv,
                    originalPostId: 99,
                }),
            );
            expect(emitSpy).toHaveBeenCalledWith(directPost);
        }));
    });

    describe('Draft functionality', () => {
        beforeEach(fakeAsync(() => {
            component.posting = directMessageUser1;
            (component as any).activeConversation = () => ({ id: 1, name: 'Test Channel' }) as any;
            jest.spyOn(accountService, 'identity').mockResolvedValue({ id: 1 } as any);
            component.resetFormGroup();
            component.ngOnInit();
            tick();
        }));

        it('should save draft when content changes', fakeAsync(() => {
            component.ngOnChanges();

            const saveDraft = 'test draft content';
            const saveDraftSpy = jest.spyOn(draftService, 'saveDraft');
            const getDraftKeySpy = jest.spyOn(component as any, 'getDraftKey').mockReturnValue('thread_draft_1_1_1');

            component.formGroup.setValue({
                content: saveDraft,
            });
            tick();

            expect(getDraftKeySpy).toHaveBeenCalled();
            expect(saveDraftSpy).toHaveBeenCalledWith('thread_draft_1_1_1', saveDraft);
        }));

        it('should clear draft when content is empty', fakeAsync(() => {
            const clearDraftSpy = jest.spyOn(draftService, 'clearDraft');
            const getDraftKeySpy = jest.spyOn(component as any, 'getDraftKey').mockReturnValue('thread_draft_1_1_1');

            component.formGroup.setValue({
                content: '',
            });
            tick();

            expect(getDraftKeySpy).toHaveBeenCalled();
            expect(clearDraftSpy).toHaveBeenCalledWith('thread_draft_1_1_1');
        }));

        it('should load draft on init if available', fakeAsync(() => {
            const draftContent = 'saved draft content';
            const getDraftKeySpy = jest.spyOn(component as any, 'getDraftKey').mockReturnValue('thread_draft_1_1_1');
            jest.spyOn(draftService, 'loadDraft').mockReturnValue(draftContent);

            component.ngOnInit();
            tick();

            component['loadDraft']();
            tick();

            expect(getDraftKeySpy).toHaveBeenCalled();
            expect(component.posting.content).toBe(draftContent);
        }));

        it('should clear draft after successful reply creation', fakeAsync(() => {
            const clearDraftSpy = jest.spyOn(draftService, 'clearDraft');
            const getDraftKeySpy = jest.spyOn(component as any, 'getDraftKey').mockReturnValue('thread_draft_1_1_1');

            component.formGroup.setValue({
                content: 'new content',
            });
            tick();

            component.confirm();
            tick();

            expect(getDraftKeySpy).toHaveBeenCalled();
            expect(clearDraftSpy).toHaveBeenCalledWith('thread_draft_1_1_1');
        }));

        it('should not save draft if conversation or post id is missing', fakeAsync(() => {
            const saveDraftSpy = jest.spyOn(draftService, 'saveDraft');
            const getDraftKeySpy = jest.spyOn(component as any, 'getDraftKey').mockReturnValue('');

            component.posting = { content: '' };
            component.ngOnInit();

            tick();

            expect(getDraftKeySpy).not.toHaveBeenCalled();
            expect(saveDraftSpy).not.toHaveBeenCalled();
        }));
    });
});
