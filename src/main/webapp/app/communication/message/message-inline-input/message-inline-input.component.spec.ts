import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MetisService } from 'app/communication/service/metis.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockDirective, MockPipe } from 'ng-mocks';
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
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('MessageInlineInputComponent', () => {
    let component: MessageInlineInputComponent;
    let fixture: ComponentFixture<MessageInlineInputComponent>;
    let metisService: MetisService;
    let metisServiceCreateStub: jest.SpyInstance;
    let metisServiceUpdateStub: jest.SpyInstance;
    let draftService: DraftService;
    let accountService: AccountService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [MessageInlineInputComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FormBuilder,
                { provide: MetisService, useClass: MockMetisService },
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MessageInlineInputComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                draftService = TestBed.inject(DraftService);
                accountService = TestBed.inject(AccountService);
                metisServiceCreateStub = jest.spyOn(metisService, 'createPost');
                metisServiceUpdateStub = jest.spyOn(metisService, 'updatePost');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should invoke metis service with created post', fakeAsync(() => {
        component.posting = metisPostToCreateUser1;
        component.ngOnChanges();

        const newContent = 'new content';
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith(Object.assign({}, component.posting, { content: newContent, title: undefined }));
        tick();
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).toHaveBeenCalledOnce();
    }));

    it('should stop loading when metis service throws error during message creation', fakeAsync(() => {
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

    it('should invoke metis service with edited post', fakeAsync(() => {
        component.posting = directMessageUser1;
        component.ngOnChanges();

        const editedContent = 'edited content';
        const onEditSpy = jest.spyOn(component.isModalOpen, 'emit');

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        expect(metisServiceUpdateStub).toHaveBeenCalledWith(Object.assign({}, component.posting, { content: editedContent, title: undefined }));
        tick();
        expect(component.isLoading).toBeFalse();
        expect(onEditSpy).toHaveBeenCalledOnce();
    }));

    it('should stop loading when metis service throws error during message updating', fakeAsync(() => {
        metisServiceUpdateStub.mockImplementation(() => throwError(() => new Error('error')));

        component.posting = directMessageUser1;
        component.ngOnChanges();

        const editedContent = 'edited content';

        component.formGroup.setValue({
            content: editedContent,
        });

        component.confirm();

        tick();
        expect(component.isLoading).toBeFalse();
    }));

    describe('Draft functionality', () => {
        beforeEach(fakeAsync(() => {
            component.posting = directMessageUser1;
            jest.spyOn(accountService, 'identity').mockResolvedValue({ id: 1 } as any);
            component.resetFormGroup();
            component.ngOnInit();
            tick();
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

        it('should save draft when content changes', fakeAsync(() => {
            const saveDraftSpy = jest.spyOn(draftService, 'saveDraft');
            const getDraftKeySpy = jest.spyOn(component as any, 'getDraftKey').mockReturnValue('message_draft_1_1');

            component.formGroup.setValue({
                content: 'test draft content',
            });
            tick();

            expect(getDraftKeySpy).toHaveBeenCalledOnce();
            expect(saveDraftSpy).toHaveBeenCalledWith('message_draft_1_1', 'test draft content');
        }));

        it('should clear draft when content is empty', fakeAsync(() => {
            const clearDraftSpy = jest.spyOn(draftService, 'clearDraft');
            const getDraftKeySpy = jest.spyOn(component as any, 'getDraftKey').mockReturnValue('message_draft_1_1');

            component.formGroup.setValue({
                content: '',
            });
            tick();

            expect(getDraftKeySpy).toHaveBeenCalled();
            expect(clearDraftSpy).toHaveBeenCalledWith('message_draft_1_1');
        }));

        it('should load draft on init if available', fakeAsync(() => {
            const draftContent = 'saved draft content';
            const getDraftKeySpy = jest.spyOn(component as any, 'getDraftKey').mockReturnValue('message_draft_1_1');
            jest.spyOn(draftService, 'loadDraft').mockReturnValue(draftContent);

            component.ngOnInit();
            tick();

            component['loadDraft']();
            tick();

            expect(getDraftKeySpy).toHaveBeenCalledOnce();
            expect(component.posting.content).toBe(draftContent);
        }));

        it('should clear draft after successful post creation', fakeAsync(() => {
            const clearDraftSpy = jest.spyOn(draftService, 'clearDraft');
            const getDraftKeySpy = jest.spyOn(component as any, 'getDraftKey').mockReturnValue('message_draft_1_1');

            component.formGroup.setValue({
                content: 'new content',
            });
            tick();

            component.confirm();
            tick();

            expect(getDraftKeySpy).toHaveBeenCalled();
            expect(clearDraftSpy).toHaveBeenCalledWith('message_draft_1_1');
        }));
    });
});
