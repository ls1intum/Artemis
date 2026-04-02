import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/communication/service/metis.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FormBuilder } from '@angular/forms';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { directMessageUser1, metisPostToCreateUser1 } from 'test/helpers/sample/metis-sample-data';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MessageReplyInlineInputComponent } from 'app/communication/message/message-reply-inline-input/message-reply-inline-input.component';
import { throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DraftService } from 'app/communication/message/service/draft-message.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProvider } from 'ng-mocks';
import { DialogService } from 'primeng/dynamicdialog';

describe('MessageReplyInlineInputComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MessageReplyInlineInputComponent;
    let fixture: ComponentFixture<MessageReplyInlineInputComponent>;
    let metisService: MetisService;
    let metisServiceCreateStub: ReturnType<typeof vi.spyOn>;
    let metisServiceUpdateStub: ReturnType<typeof vi.spyOn>;
    let draftService: DraftService;
    let accountService: AccountService;

    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MessageReplyInlineInputComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FormBuilder,
                { provide: MetisService, useClass: MockMetisService },
                { provide: MetisConversationService, useValue: {} },
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(DraftService),
                { provide: DialogService, useValue: { open: vi.fn() } },
            ],
        });

        TestBed.overrideComponent(MessageReplyInlineInputComponent, {
            remove: { imports: [PostingMarkdownEditorComponent, PostingButtonComponent, ArtemisTranslatePipe] },
            add: { imports: [MockComponent(PostingMarkdownEditorComponent), MockComponent(PostingButtonComponent), MockPipe(ArtemisTranslatePipe)] },
        });
        fixture = TestBed.createComponent(MessageReplyInlineInputComponent);
        component = fixture.componentInstance;
        metisService = TestBed.inject(MetisService);
        draftService = TestBed.inject(DraftService);
        accountService = TestBed.inject(AccountService);
        metisServiceCreateStub = vi.spyOn(metisService, 'createAnswerPost');
        metisServiceUpdateStub = vi.spyOn(metisService, 'updateAnswerPost');
    });

    it('should invoke metis service with created message reply', () => {
        component.posting.set({ ...metisPostToCreateUser1 });
        fixture.detectChanges();

        const newContent = 'new content';
        const onCreateSpy = vi.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith(expect.objectContaining({ content: newContent }));
        vi.advanceTimersByTime(0);
        expect(component.isLoading).toBe(false);
        expect(onCreateSpy).toHaveBeenCalledExactlyOnceWith({ ...component.posting()!, content: newContent });
    });

    it('should stop loading when metis service throws error during replying to message', () => {
        metisServiceCreateStub.mockImplementation(() => throwError(() => new Error('error')));
        const onCreateSpy = vi.spyOn(component.onCreate, 'emit');

        component.posting.set(metisPostToCreateUser1);
        fixture.detectChanges();

        const newContent = 'new content';
        component.formGroup.setValue({
            content: newContent,
        });

        component.confirm();

        vi.advanceTimersByTime(0);
        expect(component.isLoading).toBe(false);
        expect(onCreateSpy).not.toHaveBeenCalled();
    });

    it('should invoke metis service with edited message reply', () => {
        component.posting.set(directMessageUser1);
        fixture.detectChanges();

        const editedContent = 'edited content';

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        expect(metisServiceUpdateStub).toHaveBeenCalledWith(expect.objectContaining({ content: editedContent }));
        vi.advanceTimersByTime(0);
        expect(component.isLoading).toBe(false);
    });

    it('should stop loading when metis service throws error during message replying', () => {
        metisServiceUpdateStub.mockImplementation(() => throwError(() => new Error('error')));

        component.posting.set(directMessageUser1);
        fixture.detectChanges();

        const editedContent = 'edited content';
        const onEditSpy = vi.spyOn(component.isModalOpen, 'emit');

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        vi.advanceTimersByTime(0);
        expect(component.isLoading).toBe(false);
        expect(onEditSpy).not.toHaveBeenCalled();
    });

    describe('Draft functionality', () => {
        beforeEach(() => {
            component.posting.set(directMessageUser1);
            vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 1 } as any);
            component.resetFormGroup();
            component.ngOnInit();
            vi.advanceTimersByTime(0);
        });

        it('should save draft when content changes', () => {
            fixture.detectChanges();

            const saveDraft = 'test draft content';
            const saveDraftSpy = vi.spyOn(draftService, 'saveDraft');
            const getDraftKeySpy = vi.spyOn(component as any, 'getDraftKey').mockReturnValue('thread_draft_1_1_1');

            component.formGroup.setValue({
                content: saveDraft,
            });
            vi.advanceTimersByTime(0);

            expect(getDraftKeySpy).toHaveBeenCalledOnce();
            expect(saveDraftSpy).toHaveBeenCalledWith('thread_draft_1_1_1', saveDraft);
        });

        it('should clear draft when content is empty', () => {
            const clearDraftSpy = vi.spyOn(draftService, 'clearDraft');
            const getDraftKeySpy = vi.spyOn(component as any, 'getDraftKey').mockReturnValue('thread_draft_1_1_1');

            component.formGroup.setValue({
                content: '',
            });
            vi.advanceTimersByTime(0);

            expect(getDraftKeySpy).toHaveBeenCalledOnce();
            expect(clearDraftSpy).toHaveBeenCalledWith('thread_draft_1_1_1');
        });

        it('should load draft on init if available', () => {
            const draftContent = 'saved draft content';
            vi.spyOn(component as any, 'getDraftKey').mockReturnValue('thread_draft_1_1_1');
            vi.spyOn(draftService, 'loadDraft').mockReturnValue(draftContent);

            component.ngOnInit();
            vi.advanceTimersByTime(0);

            component['loadDraft']();
            vi.advanceTimersByTime(0);

            expect(component.formGroup.get('content')?.value).toBe(draftContent);
        });

        it('should clear draft after successful reply creation', () => {
            const clearDraftSpy = vi.spyOn(draftService, 'clearDraft');
            const getDraftKeySpy = vi.spyOn(component as any, 'getDraftKey').mockReturnValue('thread_draft_1_1_1');

            component.formGroup.setValue({
                content: 'new content',
            });
            vi.advanceTimersByTime(0);

            component.confirm();
            vi.advanceTimersByTime(0);

            expect(getDraftKeySpy).toHaveBeenCalled();
            expect(clearDraftSpy).toHaveBeenCalledWith('thread_draft_1_1_1');
        });

        it('should not save draft if conversation or post id is missing', () => {
            const saveDraftSpy = vi.spyOn(draftService, 'saveDraft');
            vi.spyOn(component as any, 'getDraftKey').mockReturnValue('');

            component.posting.set({ content: '' } as any);
            component.ngOnInit();

            vi.advanceTimersByTime(0);

            expect(saveDraftSpy).not.toHaveBeenCalled();
        });
    });
});
