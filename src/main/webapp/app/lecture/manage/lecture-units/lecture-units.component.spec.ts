import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockComponent, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { TextUnitService } from 'app/lecture/manage/lecture-units/services/text-unit.service';
import { OnlineUnitService } from 'app/lecture/manage/lecture-units/services/online-unit.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { LectureUnitManagementComponent } from 'app/lecture/manage/lecture-units/management/lecture-unit-management.component';
import { TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { OnlineUnitFormData } from 'app/lecture/manage/lecture-units/online-unit-form/online-unit-form.component';
import { AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { AttachmentVideoUnit, LectureTranscriptionDTO } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { CreateExerciseUnitComponent } from 'app/lecture/manage/lecture-units/create-exercise-unit/create-exercise-unit.component';
import { LectureUpdateUnitsComponent } from 'app/lecture/manage/lecture-units/lecture-units.component';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

// Helper type so CI uses the exact method return type
type StartTxReturn = ReturnType<AttachmentVideoUnitService['startTranscription']>;

describe('LectureUpdateUnitsComponent', () => {
    let wizardUnitComponentFixture: ComponentFixture<LectureUpdateUnitsComponent>;
    let wizardUnitComponent: LectureUpdateUnitsComponent;
    let lectureTranscriptionService: LectureTranscriptionService;
    let accountService: AccountService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [
                MockComponent(UnitCreationCardComponent),
                LectureUpdateUnitsComponent,
                MockComponent(CreateExerciseUnitComponent),
                MockComponent(LectureUnitManagementComponent),
            ],
            providers: [
                MockProvider(AlertService),
                MockProvider(TextUnitService),
                MockProvider(OnlineUnitService),
                MockProvider(AttachmentVideoUnitService),
                MockProvider(LectureUnitManagementComponent),
                MockProvider(LectureTranscriptionService),
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        wizardUnitComponentFixture = TestBed.createComponent(LectureUpdateUnitsComponent);
        wizardUnitComponent = wizardUnitComponentFixture.componentInstance;
        wizardUnitComponent.lecture = new Lecture();
        wizardUnitComponent.lecture.id = 1;
        lectureTranscriptionService = TestBed.inject(LectureTranscriptionService);
        accountService = TestBed.inject(AccountService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        wizardUnitComponentFixture.detectChanges();
        expect(wizardUnitComponent).not.toBeNull();
    });

    it('should open online form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardComponent)).componentInstance;

        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.ONLINE);
        tick();

        expect(wizardUnitComponent.isOnlineUnitFormOpen()).toBeTrue();
    }));

    it('should open attachment form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardComponent)).componentInstance;

        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.ATTACHMENT_VIDEO);
        tick();

        expect(wizardUnitComponent.isAttachmentVideoUnitFormOpen()).toBeTrue();
    }));

    it('should open text form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardComponent)).componentInstance;

        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.TEXT);
        tick();

        expect(wizardUnitComponent.isTextUnitFormOpen()).toBeTrue();
    }));

    it('should open exercise form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardComponent)).componentInstance;

        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.EXERCISE);
        tick();

        expect(wizardUnitComponent.isExerciseUnitFormOpen()).toBeTrue();
    }));

    it('should close all forms when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.onCloseLectureUnitForms();
        tick();

        expect(wizardUnitComponent.isOnlineUnitFormOpen()).toBeFalse();
        expect(wizardUnitComponent.isTextUnitFormOpen()).toBeFalse();
        expect(wizardUnitComponent.isExerciseUnitFormOpen()).toBeFalse();
        expect(wizardUnitComponent.isAttachmentVideoUnitFormOpen()).toBeFalse();
    }));

    it('should send POST request upon text form submission and update units', fakeAsync(() => {
        const textUnitService = TestBed.inject(TextUnitService);

        const formData: TextUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            content: 'Lorem Ipsum',
            competencyLinks: [new CompetencyLectureUnitLink({ id: 1, masteryThreshold: 0, optional: false, taxonomy: undefined, title: 'Test' }, undefined, 1)],
        };

        const persistedTextUnit: TextUnit = new TextUnit();
        persistedTextUnit.id = 1;
        persistedTextUnit.name = formData.name;
        persistedTextUnit.releaseDate = formData.releaseDate;
        persistedTextUnit.content = formData.content;

        const response: HttpResponse<TextUnit> = new HttpResponse({ body: persistedTextUnit, status: 200 });

        const createStub = jest.spyOn(textUnitService, 'create').mockReturnValue(of(response));

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isTextUnitFormOpen.set(true);
        wizardUnitComponent.createEditTextUnit(formData);
        tick();

        const textUnitCallArgument: TextUnit = createStub.mock.calls[0][0];
        const lectureIdCallArgument: number = createStub.mock.calls[0][1];

        expect(textUnitCallArgument.name).toEqual(formData.name);
        expect(textUnitCallArgument.content).toEqual(formData.content);
        expect(textUnitCallArgument.releaseDate).toEqual(formData.releaseDate);
        expect(textUnitCallArgument.competencyLinks).toEqual(formData.competencyLinks);
        expect(lectureIdCallArgument).toBe(1);

        expect(createStub).toHaveBeenCalledOnce();
        expect(updateSpy).toHaveBeenCalledOnce();

        updateSpy.mockRestore();
    }));

    it('should not send POST request upon empty text form submission', fakeAsync(() => {
        const textUnitService = TestBed.inject(TextUnitService);

        const formData: TextUnitFormData = {};

        const createStub = jest.spyOn(textUnitService, 'create');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isTextUnitFormOpen.set(true);
        wizardUnitComponent.createEditTextUnit(formData);
        tick();

        expect(createStub).not.toHaveBeenCalled();
    }));

    it('should show alert upon unsuccessful text form submission', fakeAsync(() => {
        const textUnitService = TestBed.inject(TextUnitService);
        const alertService = TestBed.inject(AlertService);

        const formData: TextUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            content: 'Lorem Ipsum',
        };

        const createStub = jest.spyOn(textUnitService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertStub = jest.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isTextUnitFormOpen.set(true);
        wizardUnitComponent.createEditTextUnit(formData);
        tick();

        expect(createStub).toHaveBeenCalledOnce();
        expect(alertStub).toHaveBeenCalledOnce();
    }));

    it('should show alert upon unsuccessful online form submission', fakeAsync(() => {
        const onlineUnitService = TestBed.inject(OnlineUnitService);
        const alertService = TestBed.inject(AlertService);

        const formData: OnlineUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            description: 'Lorem Ipsum',
            source: 'https://www.example.com',
        };

        const createStub = jest.spyOn(onlineUnitService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertStub = jest.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isOnlineUnitFormOpen.set(true);
        wizardUnitComponent.createEditOnlineUnit(formData);
        tick();

        expect(createStub).toHaveBeenCalledOnce();
        expect(alertStub).toHaveBeenCalledOnce();
    }));

    it('should send POST request upon online form submission and update units', fakeAsync(() => {
        const onlineUnitService = TestBed.inject(OnlineUnitService);

        const formDate: OnlineUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            description: 'Lorem Ipsum',
            source: 'https://www.example.com',
            competencyLinks: [new CompetencyLectureUnitLink({ id: 1, masteryThreshold: 0, optional: false, taxonomy: undefined, title: 'Test' }, undefined, 1)],
        };

        const response: HttpResponse<OnlineUnit> = new HttpResponse({ body: new OnlineUnit(), status: 201 });

        const createStub = jest.spyOn(onlineUnitService, 'create').mockReturnValue(of(response));

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isOnlineUnitFormOpen.set(true);
        wizardUnitComponent.createEditOnlineUnit(formDate);
        tick();

        const onlineUnitCallArgument: OnlineUnit = createStub.mock.calls[0][0];
        const lectureIdCallArgument: number = createStub.mock.calls[0][1];

        expect(onlineUnitCallArgument.name).toEqual(formDate.name);
        expect(onlineUnitCallArgument.description).toEqual(formDate.description);
        expect(onlineUnitCallArgument.releaseDate).toEqual(formDate.releaseDate);
        expect(onlineUnitCallArgument.source).toEqual(formDate.source);
        expect(onlineUnitCallArgument.competencyLinks).toEqual(formDate.competencyLinks);
        expect(lectureIdCallArgument).toBe(1);

        expect(createStub).toHaveBeenCalledOnce();
        expect(updateSpy).toHaveBeenCalledOnce();

        updateSpy.mockRestore();
    }));

    it('should not send POST request upon empty online form submission', fakeAsync(() => {
        const onlineUnitService = TestBed.inject(OnlineUnitService);

        const formData: OnlineUnitFormData = {};

        const createStub = jest.spyOn(onlineUnitService, 'create');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isOnlineUnitFormOpen.set(true);
        wizardUnitComponent.createEditOnlineUnit(formData);
        tick();

        expect(createStub).not.toHaveBeenCalled();
    }));

    it('should send POST request upon attachment form submission and update units', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'test',
                description: 'lorem ipsum',
                releaseDate: dayjs().year(2010).month(3).date(5),
                version: 2,
                updateNotificationText: 'lorem ipsum',
                competencyLinks: [new CompetencyLectureUnitLink({ id: 1, masteryThreshold: 0, optional: false, taxonomy: undefined, title: 'Test' }, undefined, 1)],
            },
            fileProperties: {
                file: fakeFile,
                fileName: 'lorem ipsum',
            },
        };

        const examplePath = '/path/to/file';

        const attachment = new Attachment();
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = attachmentVideoUnitFormData.formProperties.releaseDate;
        attachment.name = attachmentVideoUnitFormData.formProperties.name;
        attachment.link = examplePath;

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.description = attachmentVideoUnitFormData.formProperties.description;
        attachmentVideoUnit.attachment = attachment;

        const formData = new FormData();
        formData.append('file', fakeFile, attachmentVideoUnitFormData.fileProperties.fileName);
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentVideoUnit', objectToJsonBlob(attachmentVideoUnit));

        const attachmentVideoUnitResponse: HttpResponse<AttachmentVideoUnit> = new HttpResponse({ body: attachmentVideoUnit, status: 201 });
        const createAttachmentVideoUnitStub = jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(attachmentVideoUnitResponse));

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createEditAttachmentVideoUnit(attachmentVideoUnitFormData);
        tick();

        const lectureIdCallArgument: number = createAttachmentVideoUnitStub.mock.calls[0][1];

        expect(lectureIdCallArgument).toBe(1);
        expect(createAttachmentVideoUnitStub).toHaveBeenCalledWith(formData, 1);
        expect(updateSpy).toHaveBeenCalledOnce();

        updateSpy.mockRestore();
    }));

    it('should send POST request upon attachment form submission and update units when editing lecture', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'test',
                description: 'lorem ipsum',
                releaseDate: dayjs().year(2010).month(3).date(5),
                version: 2,
                updateNotificationText: 'lorem ipsum',
            },
            fileProperties: {
                file: fakeFile,
                fileName: 'lorem ipsum',
            },
        };

        const examplePath = '/path/to/file';

        const attachment = new Attachment();
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = attachmentVideoUnitFormData.formProperties.releaseDate;
        attachment.name = attachmentVideoUnitFormData.formProperties.name;
        attachment.link = examplePath;

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.description = attachmentVideoUnitFormData.formProperties.description;
        attachmentVideoUnit.attachment = attachment;

        const formData = new FormData();
        formData.append('file', fakeFile, attachmentVideoUnitFormData.fileProperties.fileName);
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentVideoUnit', objectToJsonBlob(attachmentVideoUnit));

        const attachmentVideoUnitResponse: HttpResponse<AttachmentVideoUnit> = new HttpResponse({ body: attachmentVideoUnit, status: 201 });
        const createAttachmentVideoUnitStub = jest.spyOn(attachmentVideoUnitService, 'update').mockReturnValue(of(attachmentVideoUnitResponse));

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isEditingLectureUnit = true;
        wizardUnitComponent.currentlyProcessedAttachmentVideoUnit = new AttachmentVideoUnit();
        wizardUnitComponent.currentlyProcessedAttachmentVideoUnit.attachment = new Attachment();
        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);

        wizardUnitComponent.createEditAttachmentVideoUnit(attachmentVideoUnitFormData);
        tick();

        expect(createAttachmentVideoUnitStub).toHaveBeenCalledOnce();
        expect(updateSpy).toHaveBeenCalledOnce();

        updateSpy.mockRestore();
    }));

    it('should show alert upon unsuccessful attachment form submission', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        const alertService = TestBed.inject(AlertService);

        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'test',
                description: 'lorem ipsum',
                releaseDate: dayjs().year(2010).month(3).date(5),
                version: 2,
                updateNotificationText: 'lorem ipsum',
            },
            fileProperties: {
                file: fakeFile,
                fileName: 'lorem ipsum',
            },
        };

        const examplePath = '/path/to/file';

        const attachment = new Attachment();
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = attachmentVideoUnitFormData.formProperties.releaseDate;
        attachment.name = attachmentVideoUnitFormData.formProperties.name;
        attachment.link = examplePath;

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.description = attachmentVideoUnitFormData.formProperties.description;
        attachmentVideoUnit.attachment = attachment;

        const formData = new FormData();
        formData.append('file', fakeFile, attachmentVideoUnitFormData.fileProperties.fileName);
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentVideoUnit', objectToJsonBlob(attachmentVideoUnit));

        const createAttachmentVideoUnitStub = jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertStub = jest.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createEditAttachmentVideoUnit(attachmentVideoUnitFormData);
        tick();

        expect(createAttachmentVideoUnitStub).toHaveBeenCalledOnce();
        expect(alertStub).toHaveBeenCalledOnce();
    }));

    it('should show alert upon unsuccessful attachment form submission with error information', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        const alertService = TestBed.inject(AlertService);

        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'test',
                description: 'lorem ipsum',
                releaseDate: dayjs().year(2010).month(3).date(5),
                version: 2,
                updateNotificationText: 'lorem ipsum',
            },
            fileProperties: {
                file: fakeFile,
                fileName: 'lorem ipsum',
            },
        };

        const examplePath = '/path/to/file';

        const attachment = new Attachment();
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = attachmentVideoUnitFormData.formProperties.releaseDate;
        attachment.name = attachmentVideoUnitFormData.formProperties.name;
        attachment.link = examplePath;

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.description = attachmentVideoUnitFormData.formProperties.description;
        attachmentVideoUnit.attachment = attachment;

        const formData = new FormData();
        formData.append('file', fakeFile, attachmentVideoUnitFormData.fileProperties.fileName);
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentVideoUnit', objectToJsonBlob(attachmentVideoUnit));

        const createAttachmentVideoUnitStub = jest
            .spyOn(attachmentVideoUnitService, 'create')
            .mockReturnValue(throwError(() => ({ status: 404, error: { params: 'file', title: 'Test Title' } })));
        const alertStub = jest.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createEditAttachmentVideoUnit(attachmentVideoUnitFormData);
        tick();

        expect(createAttachmentVideoUnitStub).toHaveBeenCalledOnce();
        expect(alertStub).toHaveBeenCalledOnce();
    }));

    it('should not send POST request upon empty attachment form submission', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const formData: AttachmentVideoUnitFormData = {
            formProperties: {},
            fileProperties: {},
        };

        const createStub = jest.spyOn(attachmentVideoUnitService, 'create');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createEditAttachmentVideoUnit(formData);
        tick();

        expect(createStub).not.toHaveBeenCalled();
    }));

    it('should update units upon exercise unit creation', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.onExerciseUnitCreated();
        tick();

        expect(updateSpy).toHaveBeenCalledOnce();
        updateSpy.mockRestore();
    }));

    it('should be in edit mode when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.startEditLectureUnit(new TextUnit());
        tick();

        expect(wizardUnitComponent.isEditingLectureUnit).toBeTrue();
    }));

    it('should open edit online form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.startEditLectureUnit(new OnlineUnit());
        tick();

        expect(wizardUnitComponent.isOnlineUnitFormOpen()).toBeTrue();
    }));

    it('should open edit attachment form when clicked', fakeAsync(() => {
        jest.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(undefined));

        wizardUnitComponentFixture.detectChanges();
        tick();

        const attachment = new Attachment();
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = dayjs().year(2010).month(3).date(5);
        attachment.name = 'test';
        attachment.link = '/path/to/file';

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.attachment = attachment;

        wizardUnitComponent.startEditLectureUnit(attachmentVideoUnit);
        tick();

        expect(wizardUnitComponent.isAttachmentVideoUnitFormOpen()).toBeTrue();
    }));

    it('should open edit text form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.startEditLectureUnit(new TextUnit());
        tick();

        expect(wizardUnitComponent.isTextUnitFormOpen()).toBeTrue();
    }));

    it('should open exercise form upon init when requested', fakeAsync(() => {
        const route = TestBed.inject(ActivatedRoute);
        route.queryParams = of({ shouldOpenCreateExercise: true });

        wizardUnitComponentFixture.detectChanges();
        tick();

        expect(wizardUnitComponent).not.toBeNull();
        expect(wizardUnitComponent.isExerciseUnitFormOpen()).toBeTrue();
    }));

    it('should fetch transcription when starting to edit a video unit as admin', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();

        const attachment = new Attachment();
        attachment.id = 1;
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = dayjs().year(2010).month(3).date(5);
        attachment.name = 'test';
        attachment.link = '/path/to/file';

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.id = 1;
        attachmentVideoUnit.attachment = attachment;

        const transcript: LectureTranscriptionDTO = {
            lectureUnitId: 1,
            language: 'en',
            segments: [],
        };

        jest.spyOn(accountService, 'isAdmin').mockReturnValue(true);
        const getTranscriptionSpy = jest.spyOn(lectureTranscriptionService, 'getTranscription').mockReturnValue(of(transcript));

        wizardUnitComponent.startEditLectureUnit(attachmentVideoUnit);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isAttachmentVideoUnitFormOpen()).toBeTrue();
            expect(getTranscriptionSpy).toHaveBeenCalledWith(attachmentVideoUnit.id);
            expect(wizardUnitComponent.currentlyProcessedAttachmentVideoUnit?.transcriptionProperties).toBe(transcript);
        });
    }));

    it('should not fetch transcription when starting to edit a video unit as non-admin', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();

        const attachment = new Attachment();
        attachment.id = 1;
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = dayjs().year(2010).month(3).date(5);
        attachment.name = 'test';
        attachment.link = '/path/to/file';

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.id = 1;
        attachmentVideoUnit.attachment = attachment;

        jest.spyOn(accountService, 'isAdmin').mockReturnValue(false);
        const getTranscriptionSpy = jest.spyOn(lectureTranscriptionService, 'getTranscription');

        wizardUnitComponent.startEditLectureUnit(attachmentVideoUnit);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isAttachmentVideoUnitFormOpen()).toBeTrue();
            expect(getTranscriptionSpy).not.toHaveBeenCalled();
        });
    }));

    it('should create transcription when creating a video unit with transcription properties', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const fakeFile = new File([''], 'Test-File.mp4', { type: 'video/mp4' });

        const attachmentVideoUnitFormData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'test',
                description: 'lorem ipsum',
                releaseDate: dayjs().year(2010).month(3).date(5),
            },
            fileProperties: {
                file: fakeFile,
                fileName: 'lorem ipsum',
            },
            transcriptionProperties: {
                videoTranscription: JSON.stringify({
                    language: 'en',
                    content: 'test transcription',
                }),
            },
        };

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.id = 1;

        const attachmentVideoUnitResponse: HttpResponse<AttachmentVideoUnit> = new HttpResponse({
            body: attachmentVideoUnit,
            status: 201,
        });
        const createAttachmentVideoUnitStub = jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(attachmentVideoUnitResponse));
        const createTranscriptionStub = jest.spyOn(lectureTranscriptionService, 'createTranscription').mockReturnValue(of(true));

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);

        wizardUnitComponent.createEditAttachmentVideoUnit(attachmentVideoUnitFormData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createAttachmentVideoUnitStub).toHaveBeenCalledOnce();
            expect(createTranscriptionStub).toHaveBeenCalledWith(attachmentVideoUnit.id, attachmentVideoUnitFormData.transcriptionProperties);
        });
    }));

    it('should start async transcription with playlistUrl when generateTranscript is enabled', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        const alertService = TestBed.inject(AlertService);

        const fakeFile = new File([''], 'video.mp4', { type: 'video/mp4' });

        const formData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'Video w/ transcript',
                description: 'desc',
                releaseDate: dayjs(),
                generateTranscript: true,
            },
            fileProperties: {
                file: fakeFile,
                fileName: 'video.mp4',
            },
        } as any;

        (formData as any).playlistUrl = 'https://example.com/playlist.m3u8';

        const savedUnit = new AttachmentVideoUnit();
        savedUnit.id = 42;

        jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(new HttpResponse({ body: savedUnit, status: 201 })));
        const startSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription').mockReturnValue(of(new HttpResponse<string>({ status: 200, body: '' })) as StartTxReturn);
        const successSpy = jest.spyOn(alertService, 'success');
        const errorSpy = jest.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createEditAttachmentVideoUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(startSpy).toHaveBeenCalledWith(1, 42, 'https://example.com/playlist.m3u8');
            expect(successSpy).toHaveBeenCalledWith('Transcript generation started.');
            expect(errorSpy).not.toHaveBeenCalled();
        });
    }));

    it('should prefer playlistUrl over unit.videoSource when both exist', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const formData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'Video',
                releaseDate: dayjs(),
                generateTranscript: true,
            },
            fileProperties: { fileName: 'x' },
        } as any;
        (formData as any).playlistUrl = 'https://example.com/p.m3u8';

        const savedUnit = new AttachmentVideoUnit();
        savedUnit.id = 7;
        savedUnit.videoSource = 'https://example.com/video-source.m3u8'; // should be ignored due to playlistUrl

        jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(new HttpResponse({ body: savedUnit, status: 201 })));
        const startSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription').mockReturnValue(of(new HttpResponse<string>({ status: 200, body: '' })) as StartTxReturn);

        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.createEditAttachmentVideoUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(startSpy).toHaveBeenCalledWith(1, 7, 'https://example.com/p.m3u8');
        });
    }));

    it('should use unit.videoSource when playlistUrl is missing', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const formData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'Video',
                releaseDate: dayjs(),
                generateTranscript: true,
            },
            fileProperties: { fileName: 'x' },
        };

        const savedUnit = new AttachmentVideoUnit();
        savedUnit.id = 9;
        savedUnit.videoSource = 'https://example.com/from-unit.m3u8';

        jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(new HttpResponse({ body: savedUnit, status: 201 })));
        const startSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription').mockReturnValue(of(new HttpResponse<string>({ status: 200, body: '' })) as StartTxReturn);

        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.createEditAttachmentVideoUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(startSpy).toHaveBeenCalledWith(1, 9, 'https://example.com/from-unit.m3u8');
        });
    }));

    it('should not start transcription when editing an existing unit', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const formData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'Existing video',
                releaseDate: dayjs(),
                generateTranscript: true,
            },
            fileProperties: { fileName: 'x' },
        };

        const savedUnit = new AttachmentVideoUnit();
        savedUnit.id = 5;
        savedUnit.videoSource = 'https://example.com/src.m3u8';

        jest.spyOn(attachmentVideoUnitService, 'update').mockReturnValue(of(new HttpResponse({ body: savedUnit, status: 200 })));
        const startSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription');

        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isEditingLectureUnit = true;
        wizardUnitComponent.currentlyProcessedAttachmentVideoUnit = new AttachmentVideoUnit();
        wizardUnitComponent.currentlyProcessedAttachmentVideoUnit.attachment = new Attachment();

        wizardUnitComponent.createEditAttachmentVideoUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(startSpy).not.toHaveBeenCalled();
        });
    }));

    it('should not start transcription when generateTranscript is disabled or missing', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        const savedUnit = new AttachmentVideoUnit();
        savedUnit.id = 11;
        savedUnit.videoSource = 'https://example.com/a.m3u8';

        jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(new HttpResponse({ body: savedUnit, status: 201 })));
        const startSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription');

        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        // generateTranscript undefined
        wizardUnitComponent.createEditAttachmentVideoUnit({
            formProperties: { name: 'No auto', releaseDate: dayjs() },
            fileProperties: { fileName: 'x' },
        });

        // generateTranscript false
        wizardUnitComponent.createEditAttachmentVideoUnit({
            formProperties: { name: 'No auto 2', releaseDate: dayjs(), generateTranscript: false },
            fileProperties: { fileName: 'y' },
        });

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(startSpy).not.toHaveBeenCalled();
        });
    }));

    it('should not start transcription when neither playlistUrl nor videoSource exists', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const savedUnit = new AttachmentVideoUnit();
        savedUnit.id = 66; // no videoSource set on purpose

        jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(new HttpResponse({ body: savedUnit, status: 201 })));
        const startSpy = jest.spyOn(attachmentVideoUnitService, 'startTranscription');

        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.createEditAttachmentVideoUnit({
            formProperties: { name: 'No URLs', releaseDate: dayjs(), generateTranscript: true },
            fileProperties: { fileName: 'x' },
        });

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(startSpy).not.toHaveBeenCalled();
        });
    }));

    it('should show error alert when startTranscription returns non-200 status', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        const alertService = TestBed.inject(AlertService);

        const savedUnit = new AttachmentVideoUnit();
        savedUnit.id = 100;
        savedUnit.videoSource = 'https://example.com/not-200.m3u8';

        jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(new HttpResponse({ body: savedUnit, status: 201 })));
        jest.spyOn(attachmentVideoUnitService, 'startTranscription').mockReturnValue(of(new HttpResponse<string>({ status: 500, body: 'error' })) as StartTxReturn);
        const errorSpy = jest.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.createEditAttachmentVideoUnit({
            formProperties: { name: 'Bad start', releaseDate: dayjs(), generateTranscript: true },
            fileProperties: { fileName: 'x' },
        });

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(errorSpy).toHaveBeenCalledWith(expect.stringContaining('Status: 500'));
        });
    }));

    it('should show error alert when startTranscription throws', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        const alertService = TestBed.inject(AlertService);

        const savedUnit = new AttachmentVideoUnit();
        savedUnit.id = 101;
        savedUnit.videoSource = 'https://example.com/boom.m3u8';

        jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(new HttpResponse({ body: savedUnit, status: 201 })));
        jest.spyOn(attachmentVideoUnitService, 'startTranscription').mockReturnValue(throwError(() => new Error('Boom')) as StartTxReturn);
        const errorSpy = jest.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.createEditAttachmentVideoUnit({
            formProperties: { name: 'Throw', releaseDate: dayjs(), generateTranscript: true },
            fileProperties: { fileName: 'x' },
        });

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(errorSpy).toHaveBeenCalledWith('Transcript failed to start: Boom');
        });
    }));

    it('should show alert and skip createTranscription when provided JSON is invalid', fakeAsync(() => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        const lectureTranscriptionService = TestBed.inject(LectureTranscriptionService);
        const alertService = TestBed.inject(AlertService);

        const fakeFile = new File([''], 'video.mp4', { type: 'video/mp4' });

        const formData: AttachmentVideoUnitFormData = {
            formProperties: {
                name: 'Video invalid JSON',
                description: 'desc',
                releaseDate: dayjs(),
            },
            fileProperties: {
                file: fakeFile,
                fileName: 'video.mp4',
            },
            transcriptionProperties: {
                videoTranscription: '{not-json', // invalid JSON
            },
        };

        const savedUnit = new AttachmentVideoUnit();
        savedUnit.id = 55;

        jest.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(new HttpResponse({ body: savedUnit, status: 201 })));
        const createTranscriptionSpy = jest.spyOn(lectureTranscriptionService, 'createTranscription');
        const alertSpy = jest.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);
        jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.createEditAttachmentVideoUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.lectureUnit.attachmentVideoUnit.transcriptionInvalidJson');
            expect(createTranscriptionSpy).not.toHaveBeenCalled();
        });
    }));
});
