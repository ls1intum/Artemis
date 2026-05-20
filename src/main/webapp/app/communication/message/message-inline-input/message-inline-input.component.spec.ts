import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/communication/service/metis.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FormBuilder } from '@angular/forms';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageInlineInputComponent } from 'app/communication/message/message-inline-input/message-inline-input.component';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { directMessageUser1, metisPostToCreateUser1 } from 'test/helpers/sample/metis-sample-data';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DraftService } from 'app/communication/message/service/draft-message.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { DialogService } from 'primeng/dynamicdialog';

describe('MessageInlineInputComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MessageInlineInputComponent;
    let fixture: ComponentFixture<MessageInlineInputComponent>;
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
            imports: [MessageInlineInputComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
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
                { provide: DialogService, useValue: { open: vi.fn() } },
            ],
        });

        TestBed.overrideComponent(MessageInlineInputComponent, {
            remove: { imports: [PostingMarkdownEditorComponent, PostingButtonComponent, ArtemisTranslatePipe] },
            add: { imports: [MockComponent(PostingMarkdownEditorComponent), MockComponent(PostingButtonComponent), MockPipe(ArtemisTranslatePipe)] },
        });
        fixture = TestBed.createComponent(MessageInlineInputComponent);
        component = fixture.componentInstance;
        metisService = TestBed.inject(MetisService);
        draftService = TestBed.inject(DraftService);
        accountService = TestBed.inject(AccountService);
        metisServiceCreateStub = vi.spyOn(metisService, 'createPost');
        metisServiceUpdateStub = vi.spyOn(metisService, 'updatePost');
    });

    it('should invoke metis service with created post', () => {
        component.posting.set(metisPostToCreateUser1);
        fixture.detectChanges();

        const newContent = 'new content';
        const onCreateSpy = vi.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        vi.advanceTimersByTime(300);
        expect(metisServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting()!,
            content: newContent,
            title: undefined,
        });
        vi.advanceTimersByTime(0);
        expect(component.isLoading).toBe(false);
        expect(onCreateSpy).toHaveBeenCalledOnce();
    });

    it('should stop loading when metis service throws error during message creation', () => {
        metisServiceCreateStub.mockImplementation(() => throwError(() => new Error('error')));
        const onCreateSpy = vi.spyOn(component.onCreate, 'emit');

        component.posting.set(metisPostToCreateUser1);
        fixture.detectChanges();

        const newContent = 'new content';
        component.formGroup.setValue({
            content: newContent,
        });

        component.confirm();

        vi.advanceTimersByTime(300);
        expect(component.isLoading).toBe(false);
        expect(onCreateSpy).not.toHaveBeenCalled();
    });

    it('should invoke metis service with edited post', () => {
        component.posting.set(directMessageUser1);
        fixture.detectChanges();

        const editedContent = 'edited content';
        const onEditSpy = vi.spyOn(component.isModalOpen, 'emit');

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        expect(metisServiceUpdateStub).toHaveBeenCalledWith({
            ...component.posting()!,
            content: editedContent,
            title: undefined,
        });
        vi.advanceTimersByTime(0);
        expect(component.isLoading).toBe(false);
        expect(onEditSpy).toHaveBeenCalledOnce();
    });

    it('should stop loading when metis service throws error during message updating', () => {
        metisServiceUpdateStub.mockImplementation(() => throwError(() => new Error('error')));

        component.posting.set(directMessageUser1);
        fixture.detectChanges();

        const editedContent = 'edited content';

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        vi.advanceTimersByTime(0);
        expect(component.isLoading).toBe(false);
    });

    describe('Draft functionality', () => {
        beforeEach(() => {
            component.posting.set(directMessageUser1);
            vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 1 } as any);
            component.resetFormGroup();
            component.ngOnInit();
            vi.advanceTimersByTime(0);
        });

        it('should not save draft if conversation or post id is missing', () => {
            const saveDraftSpy = vi.spyOn(draftService, 'saveDraft');
            vi.spyOn(component as any, 'getDraftKey').mockReturnValue('');

            component.posting.set({ content: '' } as any);
            component.ngOnInit();
            vi.advanceTimersByTime(0);

            expect(saveDraftSpy).not.toHaveBeenCalled();
        });

        it('should save draft when content changes', () => {
            const saveDraftSpy = vi.spyOn(draftService, 'saveDraft');
            const getDraftKeySpy = vi.spyOn(component as any, 'getDraftKey').mockReturnValue('message_draft_1_1');

            component.formGroup.setValue({
                content: 'test draft content',
            });
            vi.advanceTimersByTime(0);

            expect(getDraftKeySpy).toHaveBeenCalledOnce();
            expect(saveDraftSpy).toHaveBeenCalledWith('message_draft_1_1', 'test draft content');
        });

        it('should clear draft when content is empty', () => {
            const clearDraftSpy = vi.spyOn(draftService, 'clearDraft');
            const getDraftKeySpy = vi.spyOn(component as any, 'getDraftKey').mockReturnValue('message_draft_1_1');

            component.formGroup.setValue({
                content: '',
            });
            vi.advanceTimersByTime(0);

            expect(getDraftKeySpy).toHaveBeenCalled();
            expect(clearDraftSpy).toHaveBeenCalledWith('message_draft_1_1');
        });

        it('should load draft on init if available', () => {
            const draftContent = 'saved draft content';
            vi.spyOn(component as any, 'getDraftKey').mockReturnValue('message_draft_1_1');
            vi.spyOn(draftService, 'loadDraft').mockReturnValue(draftContent);

            component.ngOnInit();
            vi.advanceTimersByTime(0);

            component['loadDraft']();
            vi.advanceTimersByTime(0);

            expect(component.formGroup.get('content')?.value).toBe(draftContent);
        });

        it('should clear draft after successful post creation', () => {
            const clearDraftSpy = vi.spyOn(draftService, 'clearDraft');
            const getDraftKeySpy = vi.spyOn(component as any, 'getDraftKey').mockReturnValue('message_draft_1_1');

            component.formGroup.setValue({
                content: 'new content',
            });
            vi.advanceTimersByTime(0);

            component.confirm();
            vi.advanceTimersByTime(0);

            expect(getDraftKeySpy).toHaveBeenCalled();
            expect(clearDraftSpy).toHaveBeenCalledWith('message_draft_1_1');
        });
    });
});
