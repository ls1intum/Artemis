import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MetisService } from 'app/communication/service/metis.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockPipe } from 'ng-mocks';
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
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProvider } from 'ng-mocks';

describe('MessageReplyInlineInputComponent', () => {
    let component: MessageReplyInlineInputComponent;
    let fixture: ComponentFixture<MessageReplyInlineInputComponent>;
    let metisService: MetisService;
    let metisServiceCreateStub: jest.SpyInstance;
    let metisServiceUpdateStub: jest.SpyInstance;
    let draftService: DraftService;
    let accountService: AccountService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [MessageReplyInlineInputComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FormBuilder,
                { provide: MetisService, useClass: MockMetisService },
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
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
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should invoke metis service with created message reply', fakeAsync(() => {
        component.posting = Object.assign({}, metisPostToCreateUser1);
        component.ngOnChanges();

        const newContent = 'new content';
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith(Object.assign({}, component.posting, { title: undefined }));
        tick();
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).toHaveBeenCalledExactlyOnceWith(Object.assign({}, component.posting, { content: newContent }));
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

        expect(metisServiceUpdateStub).toHaveBeenCalledWith(Object.assign({}, component.posting, { content: editedContent, title: undefined }));
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

    describe('Draft functionality', () => {
        beforeEach(fakeAsync(() => {
            component.posting = directMessageUser1;
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

            expect(getDraftKeySpy).toHaveBeenCalledOnce();
            expect(saveDraftSpy).toHaveBeenCalledWith('thread_draft_1_1_1', saveDraft);
        }));

        it('should clear draft when content is empty', fakeAsync(() => {
            const clearDraftSpy = jest.spyOn(draftService, 'clearDraft');
            const getDraftKeySpy = jest.spyOn(component as any, 'getDraftKey').mockReturnValue('thread_draft_1_1_1');

            component.formGroup.setValue({
                content: '',
            });
            tick();

            expect(getDraftKeySpy).toHaveBeenCalledOnce();
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

            expect(getDraftKeySpy).toHaveBeenCalledOnce();
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
