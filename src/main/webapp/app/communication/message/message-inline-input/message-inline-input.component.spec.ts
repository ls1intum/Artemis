import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommunicationService } from 'app/communication/service/communication.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FormBuilder } from '@angular/forms';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageInlineInputComponent } from 'app/communication/message/message-inline-input/message-inline-input.component';
import { MockCommunicationService } from 'test/helpers/mocks/service/mock-communication.service';
import { communicationPostToCreateUser1, directMessageUser1 } from 'test/helpers/sample/communication-sample-data';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DraftService } from 'app/communication/message/service/draft-message.service';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { DialogService } from 'primeng/dynamicdialog';

describe('MessageInlineInputComponent', () => {
    let component: MessageInlineInputComponent;
    let fixture: ComponentFixture<MessageInlineInputComponent>;
    let communicationService: CommunicationService;
    let communicationServiceCreateStub: ReturnType<typeof vi.spyOn>;
    let communicationServiceUpdateStub: ReturnType<typeof vi.spyOn>;
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
                { provide: CommunicationService, useClass: MockCommunicationService },
                { provide: CourseConversationsService, useValue: {} },
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
        communicationService = TestBed.inject(CommunicationService);
        draftService = TestBed.inject(DraftService);
        accountService = TestBed.inject(AccountService);
        communicationServiceCreateStub = vi.spyOn(communicationService, 'createPost');
        communicationServiceUpdateStub = vi.spyOn(communicationService, 'updatePost');
    });

    it('should invoke communication service with created post', () => {
        component.posting.set(communicationPostToCreateUser1);
        fixture.detectChanges();

        const newContent = 'new content';
        const onCreateSpy = vi.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        vi.advanceTimersByTime(300);
        expect(communicationServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting()!,
            content: newContent,
            title: undefined,
        });
        vi.advanceTimersByTime(0);
        expect(component.isLoading()).toBe(false);
        expect(onCreateSpy).toHaveBeenCalledOnce();
    });

    it('should stop loading when communication service throws error during message creation', () => {
        communicationServiceCreateStub.mockImplementation(() => throwError(() => new Error('error')));
        const onCreateSpy = vi.spyOn(component.onCreate, 'emit');

        component.posting.set(communicationPostToCreateUser1);
        fixture.detectChanges();

        const newContent = 'new content';
        component.formGroup.setValue({
            content: newContent,
        });

        component.confirm();

        vi.advanceTimersByTime(300);
        expect(component.isLoading()).toBe(false);
        expect(onCreateSpy).not.toHaveBeenCalled();
    });

    it('should invoke communication service with edited post', () => {
        component.posting.set(directMessageUser1);
        fixture.detectChanges();

        const editedContent = 'edited content';
        const onEditSpy = vi.spyOn(component.isModalOpen, 'emit');

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        expect(communicationServiceUpdateStub).toHaveBeenCalledWith({
            ...component.posting()!,
            content: editedContent,
            title: undefined,
        });
        vi.advanceTimersByTime(0);
        expect(component.isLoading()).toBe(false);
        expect(onEditSpy).toHaveBeenCalledOnce();
    });

    it('should stop loading when communication service throws error during message updating', () => {
        communicationServiceUpdateStub.mockImplementation(() => throwError(() => new Error('error')));

        component.posting.set(directMessageUser1);
        fixture.detectChanges();

        const editedContent = 'edited content';

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        vi.advanceTimersByTime(0);
        expect(component.isLoading()).toBe(false);
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
