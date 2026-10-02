import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Mock, MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockComponent, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { TextUnitService } from 'app/lecture/manage/lecture-units/services/text-unit.service';
import { OnlineUnitService } from 'app/lecture/manage/lecture-units/services/online-unit.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { LectureUnitManagementComponent } from 'app/lecture/manage/lecture-units/management/lecture-unit-management.component';
import { TextUnitFormComponent, TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { OnlineUnitFormComponent, OnlineUnitFormData } from 'app/lecture/manage/lecture-units/online-unit-form/online-unit-form.component';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { AttachmentUpdateIntent, AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { objectToJsonBlob } from 'app/foundation/util/blob-util';
import { CreateExerciseUnitComponent } from 'app/lecture/manage/lecture-units/create-exercise-unit/create-exercise-unit.component';
import { AUTOSAVE_DELAY_MS, LectureUpdateUnitsComponent } from 'app/lecture/manage/lecture-units/lecture-units.component';
import { UnitFormChange } from 'app/lecture/manage/lecture-units/unit-form-change.model';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { PdfDropZoneComponent } from 'app/lecture/manage/pdf-drop-zone/pdf-drop-zone.component';
import { Component, ElementRef, NO_ERRORS_SCHEMA, Signal, computed, input, output, signal } from '@angular/core';
import { ngMocks } from 'ng-mocks';

// Tell ng-mocks to skip auto-mocking PdfDropZoneComponent
ngMocks.globalKeep(PdfDropZoneComponent);

@Component({ selector: 'jhi-pdf-drop-zone', standalone: true, template: '' })
class PdfDropZoneStubComponent {
    disabled = input<boolean>(false);
    filesDropped = output<File[]>();
}

@Component({ selector: 'jhi-text-unit-form', standalone: true, template: '' })
class TextUnitFormStubComponent {
    formData = input<TextUnitFormData>();
    isEditMode = input<boolean>(false);
    hasCancelButton = input<boolean>(false);
    formSubmitted = output<TextUnitFormData>();
    autosave = input<boolean>(false);
    formChanged = output<UnitFormChange<TextUnitFormData>>();
    isFormValid = () => true;
}

@Component({ selector: 'jhi-online-unit-form', standalone: true, template: '' })
class OnlineUnitFormStubComponent {
    formData = input<OnlineUnitFormData>();
    isEditMode = input<boolean>(false);
    hasCancelButton = input<boolean>(false);
    formSubmitted = output<OnlineUnitFormData>();
    autosave = input<boolean>(false);
    formChanged = output<UnitFormChange<OnlineUnitFormData>>();
    isFormValid = () => true;
}

@Component({ selector: 'jhi-attachment-video-unit-form', standalone: true, template: '' })
class AttachmentVideoUnitFormStubComponent {
    formData = input<AttachmentVideoUnitFormData>();
    isEditMode = input<boolean>(false);
    hasCancelButton = input<boolean>(false);
    formSubmitted = output<AttachmentVideoUnitFormData>();
    autosave = input<boolean>(false);
    formChanged = output<UnitFormChange<AttachmentVideoUnitFormData>>();
    fileUploadRequested = output<AttachmentVideoUnitFormData>();
    videoSourceSaveRequested = output<AttachmentVideoUnitFormData>();
    isFormValid = () => true;
}

describe('LectureUpdateUnitsComponent', () => {
    let wizardUnitComponentFixture: ComponentFixture<LectureUpdateUnitsComponent>;
    let wizardUnitComponent: LectureUpdateUnitsComponent;
    let attachmentVideoUnitService: AttachmentVideoUnitService;
    let unitManagementComponentMock: Pick<LectureUnitManagementComponent, 'loadData' | 'replaceLectureUnit' | 'focusEditButton'>;

    const getAttachmentVideoUnitPayload = async (formData: FormData) => {
        const attachmentVideoUnitPart = formData.get('attachmentVideoUnit') as Blob;
        return JSON.parse(await attachmentVideoUnitPart.text());
    };

    const mockUnitManagementComponent = () => {
        unitManagementComponentMock = {
            loadData: vi.fn(),
            replaceLectureUnit: vi.fn(),
            focusEditButton: vi.fn(),
        };

        wizardUnitComponent.unitManagementComponent = signal(unitManagementComponentMock as LectureUnitManagementComponent).asReadonly() as Signal<
            LectureUnitManagementComponent | undefined
        >;
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                LectureUpdateUnitsComponent,
                MockComponent(UnitCreationCardComponent),
                MockComponent(CreateExerciseUnitComponent),
                MockComponent(LectureUnitManagementComponent),
                PdfDropZoneStubComponent,
            ],
            schemas: [NO_ERRORS_SCHEMA],
            providers: [
                MockProvider(AlertService),
                MockProvider(TextUnitService),
                MockProvider(OnlineUnitService),
                MockProvider(AttachmentVideoUnitService),
                MockProvider(LectureUnitManagementComponent),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        queryParams: of({}),
                        paramMap: of(new Map()),
                        snapshot: { paramMap: { get: () => null } },
                        parent: {
                            snapshot: { paramMap: { get: () => null } },
                            parent: {
                                paramMap: of(new Map([['lectureId', '1']])),
                                snapshot: { paramMap: { get: (key: string) => (key === 'lectureId' ? '1' : null) } },
                                parent: {
                                    paramMap: of(new Map([['courseId', '1']])),
                                    snapshot: { paramMap: { get: (key: string) => (key === 'courseId' ? '1' : null) } },
                                },
                            },
                        },
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(LectureUpdateUnitsComponent, {
                remove: { imports: [LectureUnitManagementComponent, PdfDropZoneComponent, TextUnitFormComponent, OnlineUnitFormComponent, AttachmentVideoUnitFormComponent] },
                add: {
                    imports: [
                        MockComponent(LectureUnitManagementComponent),
                        PdfDropZoneStubComponent,
                        TextUnitFormStubComponent,
                        OnlineUnitFormStubComponent,
                        AttachmentVideoUnitFormStubComponent,
                    ],
                },
            })
            .compileComponents();

        wizardUnitComponentFixture = TestBed.createComponent(LectureUpdateUnitsComponent);
        wizardUnitComponent = wizardUnitComponentFixture.componentInstance;

        const lecture = new Lecture();
        lecture.id = 1;
        wizardUnitComponentFixture.componentRef.setInput('lecture', lecture);

        attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        wizardUnitComponent.editFormContainer = computed(() => ({ nativeElement: { scrollIntoView: vi.fn(), querySelector: vi.fn() } }) as unknown as ElementRef) as Signal<
            ElementRef | undefined
        >;
        mockUnitManagementComponent();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        wizardUnitComponentFixture.detectChanges();
        expect(wizardUnitComponent).not.toBeNull();
    });

    it('should open online form when clicked', async () => {
        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();
        const unitCreationCard: UnitCreationCardComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardComponent)).componentInstance;

        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.ONLINE);
        await wizardUnitComponentFixture.whenStable();

        expect(wizardUnitComponent.isOnlineUnitFormOpen()).toBe(true);
    });

    it('should open attachment form when clicked', async () => {
        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();
        const unitCreationCard: UnitCreationCardComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardComponent)).componentInstance;

        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.ATTACHMENT_VIDEO);
        await wizardUnitComponentFixture.whenStable();

        expect(wizardUnitComponent.isAttachmentVideoUnitFormOpen()).toBe(true);
    });

    it('should open text form when clicked', async () => {
        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();
        const unitCreationCard: UnitCreationCardComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardComponent)).componentInstance;

        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.TEXT);
        await wizardUnitComponentFixture.whenStable();

        expect(wizardUnitComponent.isTextUnitFormOpen()).toBe(true);
    });

    it('should open exercise form when clicked', async () => {
        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();
        const unitCreationCard: UnitCreationCardComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardComponent)).componentInstance;

        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.EXERCISE);
        await wizardUnitComponentFixture.whenStable();

        expect(wizardUnitComponent.isExerciseUnitFormOpen()).toBe(true);
    });

    it('should close all forms when clicked', async () => {
        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.onCloseLectureUnitForms();
        await wizardUnitComponentFixture.whenStable();

        expect(wizardUnitComponent.isOnlineUnitFormOpen()).toBe(false);
        expect(wizardUnitComponent.isTextUnitFormOpen()).toBe(false);
        expect(wizardUnitComponent.isExerciseUnitFormOpen()).toBe(false);
        expect(wizardUnitComponent.isAttachmentVideoUnitFormOpen()).toBe(false);
    });

    it('should send POST request upon text form submission and update units', async () => {
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

        const createStub = vi.spyOn(textUnitService, 'create').mockReturnValue(of(response));

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        const updateSpy = vi.spyOn(unitManagementComponentMock, 'loadData');

        wizardUnitComponent.isTextUnitFormOpen.set(true);
        wizardUnitComponent.createTextUnit(formData);
        await wizardUnitComponentFixture.whenStable();

        const textUnitCallArgument: TextUnit = createStub.mock.calls[0][0];
        const lectureIdCallArgument: number = createStub.mock.calls[0][1];

        expect(textUnitCallArgument.name).toEqual(formData.name);
        expect(textUnitCallArgument.content).toEqual(formData.content);
        expect(textUnitCallArgument.releaseDate).toEqual(formData.releaseDate);
        expect(textUnitCallArgument.competencyLinks).toEqual(formData.competencyLinks);
        expect(lectureIdCallArgument).toBe(1);

        expect(createStub).toHaveBeenCalledTimes(1);
        expect(updateSpy).toHaveBeenCalledTimes(1);

        updateSpy.mockRestore();
    });

    it('should not send POST request upon empty text form submission', async () => {
        const textUnitService = TestBed.inject(TextUnitService);

        const formData: TextUnitFormData = {};

        const createStub = vi.spyOn(textUnitService, 'create');

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.isTextUnitFormOpen.set(true);
        wizardUnitComponent.createTextUnit(formData);
        await wizardUnitComponentFixture.whenStable();

        expect(createStub).not.toHaveBeenCalled();
    });

    it('should show alert upon unsuccessful text form submission', async () => {
        const textUnitService = TestBed.inject(TextUnitService);
        const alertService = TestBed.inject(AlertService);

        const formData: TextUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            content: 'Lorem Ipsum',
        };

        const createStub = vi.spyOn(textUnitService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertStub = vi.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.isTextUnitFormOpen.set(true);
        wizardUnitComponent.createTextUnit(formData);
        await wizardUnitComponentFixture.whenStable();

        expect(createStub).toHaveBeenCalledTimes(1);
        expect(alertStub).toHaveBeenCalledTimes(1);
    });

    it('should show alert upon unsuccessful online form submission', async () => {
        const onlineUnitService = TestBed.inject(OnlineUnitService);
        const alertService = TestBed.inject(AlertService);

        const formData: OnlineUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            description: 'Lorem Ipsum',
            source: 'https://www.example.com',
        };

        const createStub = vi.spyOn(onlineUnitService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertStub = vi.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.isOnlineUnitFormOpen.set(true);
        wizardUnitComponent.createOnlineUnit(formData);
        await wizardUnitComponentFixture.whenStable();

        expect(createStub).toHaveBeenCalledTimes(1);
        expect(alertStub).toHaveBeenCalledTimes(1);
    });

    it('should send POST request upon online form submission and update units', async () => {
        const onlineUnitService = TestBed.inject(OnlineUnitService);

        const formDate: OnlineUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            description: 'Lorem Ipsum',
            source: 'https://www.example.com',
            competencyLinks: [new CompetencyLectureUnitLink({ id: 1, masteryThreshold: 0, optional: false, taxonomy: undefined, title: 'Test' }, undefined, 1)],
        };

        const response: HttpResponse<OnlineUnit> = new HttpResponse({ body: new OnlineUnit(), status: 201 });

        const createStub = vi.spyOn(onlineUnitService, 'create').mockReturnValue(of(response));

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        const updateSpy = vi.spyOn(unitManagementComponentMock, 'loadData');

        wizardUnitComponent.isOnlineUnitFormOpen.set(true);
        wizardUnitComponent.createOnlineUnit(formDate);
        await wizardUnitComponentFixture.whenStable();

        const onlineUnitCallArgument: OnlineUnit = createStub.mock.calls[0][0];
        const lectureIdCallArgument: number = createStub.mock.calls[0][1];

        expect(onlineUnitCallArgument.name).toEqual(formDate.name);
        expect(onlineUnitCallArgument.description).toEqual(formDate.description);
        expect(onlineUnitCallArgument.releaseDate).toEqual(formDate.releaseDate);
        expect(onlineUnitCallArgument.source).toEqual(formDate.source);
        expect(onlineUnitCallArgument.competencyLinks).toEqual(formDate.competencyLinks);
        expect(lectureIdCallArgument).toBe(1);

        expect(createStub).toHaveBeenCalledTimes(1);
        expect(updateSpy).toHaveBeenCalledTimes(1);

        updateSpy.mockRestore();
    });

    it('should not send POST request upon empty online form submission', async () => {
        const onlineUnitService = TestBed.inject(OnlineUnitService);

        const formData: OnlineUnitFormData = {};

        const createStub = vi.spyOn(onlineUnitService, 'create');

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.isOnlineUnitFormOpen.set(true);
        wizardUnitComponent.createOnlineUnit(formData);
        await wizardUnitComponentFixture.whenStable();

        expect(createStub).not.toHaveBeenCalled();
    });

    it('should send POST request upon attachment form submission and update units', async () => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const fakeFile = new File(['content'], 'Test-File.pdf', { type: 'application/pdf' });

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
        const createAttachmentVideoUnitStub = vi.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(attachmentVideoUnitResponse));

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        const updateSpy = vi.spyOn(unitManagementComponentMock, 'loadData');

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createAttachmentVideoUnit(attachmentVideoUnitFormData);
        await wizardUnitComponentFixture.whenStable();

        const lectureIdCallArgument: number = createAttachmentVideoUnitStub.mock.calls[0][1];

        expect(lectureIdCallArgument).toBe(1);
        expect(createAttachmentVideoUnitStub).toHaveBeenCalledWith(expect.any(FormData), 1);
        expect(updateSpy).toHaveBeenCalledTimes(1);

        updateSpy.mockRestore();
    });

    it('should show alert upon unsuccessful attachment form submission', async () => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        const alertService = TestBed.inject(AlertService);

        const fakeFile = new File(['content'], 'Test-File.pdf', { type: 'application/pdf' });

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

        const createAttachmentVideoUnitStub = vi.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertStub = vi.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createAttachmentVideoUnit(attachmentVideoUnitFormData);
        await wizardUnitComponentFixture.whenStable();

        expect(createAttachmentVideoUnitStub).toHaveBeenCalledTimes(1);
        expect(alertStub).toHaveBeenCalledTimes(1);
    });

    it('should show alert upon unsuccessful attachment form submission with error information', async () => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        const alertService = TestBed.inject(AlertService);

        const fakeFile = new File(['content'], 'Test-File.pdf', { type: 'application/pdf' });

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

        const createAttachmentVideoUnitStub = vi
            .spyOn(attachmentVideoUnitService, 'create')
            .mockReturnValue(throwError(() => ({ status: 404, error: { params: 'file', title: 'Test Title' } })));
        const alertStub = vi.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createAttachmentVideoUnit(attachmentVideoUnitFormData);
        await wizardUnitComponentFixture.whenStable();

        expect(createAttachmentVideoUnitStub).toHaveBeenCalledTimes(1);
        expect(alertStub).toHaveBeenCalledTimes(1);
    });

    it('should not send POST request upon empty attachment form submission', async () => {
        const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);

        const formData: AttachmentVideoUnitFormData = {
            formProperties: {},
            fileProperties: {},
        };

        const createStub = vi.spyOn(attachmentVideoUnitService, 'create');

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createAttachmentVideoUnit(formData);
        await wizardUnitComponentFixture.whenStable();

        expect(createStub).not.toHaveBeenCalled();
    });

    it('should update units upon exercise unit creation', async () => {
        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        const updateSpy = vi.spyOn(unitManagementComponentMock, 'loadData');

        wizardUnitComponent.onExerciseUnitCreated();
        await wizardUnitComponentFixture.whenStable();

        expect(updateSpy).toHaveBeenCalledTimes(1);
        updateSpy.mockRestore();
    });

    it('should be in edit mode when clicked', async () => {
        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.startEditLectureUnit(new TextUnit());
        await wizardUnitComponentFixture.whenStable();

        expect(wizardUnitComponent.isEditingLectureUnit()).toBe(true);
    });

    it('should open edit online form when clicked', async () => {
        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.startEditLectureUnit(new OnlineUnit());
        await wizardUnitComponentFixture.whenStable();

        expect(wizardUnitComponent.isOnlineUnitFormOpen()).toBe(true);
    });

    it('should open edit attachment form when clicked', async () => {
        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        const attachment = new Attachment();
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = dayjs().year(2010).month(3).date(5);
        attachment.name = 'test';
        attachment.link = '/path/to/file';

        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.attachment = attachment;

        wizardUnitComponent.startEditLectureUnit(attachmentVideoUnit);
        await wizardUnitComponentFixture.whenStable();

        expect(wizardUnitComponent.isAttachmentVideoUnitFormOpen()).toBe(true);
    });

    it('should open edit text form when clicked', async () => {
        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.startEditLectureUnit(new TextUnit());
        await wizardUnitComponentFixture.whenStable();

        expect(wizardUnitComponent.isTextUnitFormOpen()).toBe(true);
    });

    it('should open exercise form upon init when requested', async () => {
        const route = TestBed.inject(ActivatedRoute);
        route.queryParams = of({ shouldOpenCreateExercise: true });

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        expect(wizardUnitComponent).not.toBeNull();
        expect(wizardUnitComponent.isExerciseUnitFormOpen()).toBe(true);
    });

    describe('PDF drop zone', () => {
        it('should create attachment units from dropped PDF files', async () => {
            const alertService = TestBed.inject(AlertService);

            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 42;
            createdUnit.name = 'Test File';

            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));
            const successSpy = vi.spyOn(alertService, 'success');

            wizardUnitComponentFixture.detectChanges();
            await wizardUnitComponentFixture.whenStable();

            const loadDataSpy = vi.spyOn(unitManagementComponentMock, 'loadData');

            const pdfFile = new File(['content'], 'Test_File.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);
            await wizardUnitComponentFixture.whenStable();

            expect(createSpy).toHaveBeenCalledTimes(1);
            expect(successSpy).toHaveBeenCalledWith('artemisApp.lecture.pdfUpload.success');
            expect(loadDataSpy).toHaveBeenCalledTimes(1);

            loadDataSpy.mockRestore();
        });

        it('should call service with correct lecture id and file', async () => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            wizardUnitComponentFixture.detectChanges();
            await wizardUnitComponentFixture.whenStable();

            const pdfFile = new File(['content'], 'Chapter_01_Introduction.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);
            await wizardUnitComponentFixture.whenStable();

            expect(createSpy).toHaveBeenCalledWith(wizardUnitComponent.lecture().id, pdfFile);
        });

        it('should handle multiple PDF files sequentially', async () => {
            const alertService = TestBed.inject(AlertService);

            let callCount = 0;
            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockImplementation(() => {
                callCount++;
                const unit = new AttachmentVideoUnit();
                unit.id = callCount;
                return of(new HttpResponse({ body: unit, status: 201 }));
            });
            const successSpy = vi.spyOn(alertService, 'success');

            wizardUnitComponentFixture.detectChanges();
            await wizardUnitComponentFixture.whenStable();

            const pdfFiles = [
                new File(['content1'], 'file1.pdf', { type: 'application/pdf' }),
                new File(['content2'], 'file2.pdf', { type: 'application/pdf' }),
                new File(['content3'], 'file3.pdf', { type: 'application/pdf' }),
            ];

            wizardUnitComponent.onPdfFilesDropped(pdfFiles);
            await wizardUnitComponentFixture.whenStable();

            expect(createSpy).toHaveBeenCalledTimes(3);
            expect(successSpy).toHaveBeenCalledTimes(1);
        });

        it('should open edit form for last created unit after upload', async () => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 99;
            createdUnit.name = 'Created Unit';

            vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            wizardUnitComponentFixture.detectChanges();
            await wizardUnitComponentFixture.whenStable();

            const startEditSpy = vi.spyOn(wizardUnitComponent, 'startEditLectureUnit');

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);
            await wizardUnitComponentFixture.whenStable();

            expect(startEditSpy).toHaveBeenCalledWith(createdUnit);
        });

        it('should show error alert on upload failure', async () => {
            const alertService = TestBed.inject(AlertService);

            vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(throwError(() => ({ status: 400 })));
            const errorSpy = vi.spyOn(alertService, 'error');

            wizardUnitComponentFixture.detectChanges();
            await wizardUnitComponentFixture.whenStable();

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);
            await wizardUnitComponentFixture.whenStable();

            expect(errorSpy).toHaveBeenCalled();
            expect(wizardUnitComponent.isUploadingPdfs()).toBe(false);
        });

        it('should not process if no files are provided', async () => {
            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile');

            wizardUnitComponentFixture.detectChanges();
            await wizardUnitComponentFixture.whenStable();

            wizardUnitComponent.onPdfFilesDropped([]);
            await wizardUnitComponentFixture.whenStable();

            expect(createSpy).not.toHaveBeenCalled();
        });

        it('should not process if lecture has no id', async () => {
            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile');

            const lectureWithNoId = new Lecture();
            lectureWithNoId.id = undefined;
            wizardUnitComponentFixture.componentRef.setInput('lecture', lectureWithNoId);
            wizardUnitComponentFixture.detectChanges();
            await wizardUnitComponentFixture.whenStable();

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);
            await wizardUnitComponentFixture.whenStable();

            expect(createSpy).not.toHaveBeenCalled();
        });

        it('should set isUploadingPdfs during upload', async () => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            wizardUnitComponentFixture.detectChanges();
            await wizardUnitComponentFixture.whenStable();

            expect(wizardUnitComponent.isUploadingPdfs()).toBe(false);

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);

            // After completion
            await wizardUnitComponentFixture.whenStable();
            expect(wizardUnitComponent.isUploadingPdfs()).toBe(false);
        });
    });
    describe('splitting a lecture PDF', () => {
        function pdfUnit(id: number, name: string, link = `attachments/${name}.pdf`): AttachmentVideoUnit {
            const unit = new AttachmentVideoUnit();
            unit.id = id;
            unit.name = name;
            unit.type = LectureUnitType.ATTACHMENT_VIDEO;
            unit.attachment = { link } as Attachment;
            return unit;
        }

        function textUnitNamed(name: string): TextUnit {
            const unit = new TextUnit();
            unit.id = 50;
            unit.name = name;
            unit.type = LectureUnitType.TEXT;
            return unit;
        }

        beforeEach(() => {
            const lecture = new Lecture();
            lecture.id = 1;
            lecture.course = { id: 7 } as Lecture['course'];
            wizardUnitComponentFixture.componentRef.setInput('lecture', lecture);
        });

        it('should offer the PDF items of the lecture and preselect the only one', () => {
            const slides = pdfUnit(3, 'Slides', 'attachments/Slides.PDF');
            wizardUnitComponent.lectureUnits.set([textUnitNamed('Reading'), pdfUnit(4, 'Sheet', 'attachments/sheet.zip'), slides]);

            expect(wizardUnitComponent.pdfUnits()).toEqual([slides]);
            expect(wizardUnitComponent.selectedSplitUnit()).toBe(slides);
        });

        it('should keep the chosen PDF while it exists and let the user choose among several', () => {
            const first = pdfUnit(3, 'Part 1');
            const second = pdfUnit(4, 'Part 2');
            wizardUnitComponent.lectureUnits.set([first, second]);
            expect(wizardUnitComponent.selectedSplitUnitId()).toBeUndefined();

            wizardUnitComponent.selectedSplitUnitId.set(4);
            wizardUnitComponent.lectureUnits.set([first, second, pdfUnit(5, 'Part 3')]);
            expect(wizardUnitComponent.selectedSplitUnit()).toBe(second);

            wizardUnitComponent.lectureUnits.set([first]);
            expect(wizardUnitComponent.selectedSplitUnit()).toBe(first);
        });

        it('should say how to add a PDF when the lecture has none', () => {
            wizardUnitComponent.lectureUnits.set([textUnitNamed('Reading')]);
            wizardUnitComponentFixture.detectChanges();

            expect(wizardUnitComponentFixture.nativeElement.querySelector('[data-testid="lecture-split-pdf-none"]')).not.toBeNull();
            expect(wizardUnitComponentFixture.nativeElement.querySelector('[data-testid="lecture-split-pdf-start"]')).toBeNull();
        });

        it('should open the split page with a copy of the selected PDF', async () => {
            const router = TestBed.inject(Router);
            const navigateSpy = vi.spyOn(router, 'navigate');
            const getFileSpy = vi.spyOn(attachmentVideoUnitService, 'getAttachmentFile').mockReturnValue(of(new Blob(['%PDF'], { type: 'application/pdf' })));
            wizardUnitComponent.lectureUnits.set([pdfUnit(3, 'Introduction')]);

            wizardUnitComponent.splitSelectedPdf();
            await wizardUnitComponentFixture.whenStable();

            expect(getFileSpy).toHaveBeenCalledExactlyOnceWith(7, 3);
            const [commands, extras] = navigateSpy.mock.calls[0];
            expect(commands).toEqual(['course-management', 7, 'lectures', 1, 'unit-management', 'attachment-video-units', 'process']);
            const state = extras?.state as { file: File; fileName: string; sourceUnit?: unknown; returnToEditor: boolean };
            expect(state.file.name).toBe('Introduction.pdf');
            expect(state.file.type).toBe('application/pdf');
            expect(state.fileName).toBe('Introduction.pdf');
            expect(state.returnToEditor).toBe(true);
            // Only instructors may delete content, so only they are offered to remove the PDF item after the split.
            expect(state.sourceUnit).toBeUndefined();
            expect(wizardUnitComponent.isPreparingSplit()).toBe(false);
        });

        it('should let an instructor remove the PDF item after the split', async () => {
            const lecture = new Lecture();
            lecture.id = 1;
            lecture.course = { id: 7 } as Lecture['course'];
            lecture.isAtLeastInstructor = true;
            wizardUnitComponentFixture.componentRef.setInput('lecture', lecture);
            const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate');
            vi.spyOn(attachmentVideoUnitService, 'getAttachmentFile').mockReturnValue(of(new Blob(['%PDF'], { type: 'application/pdf' })));
            wizardUnitComponent.lectureUnits.set([pdfUnit(3, 'Introduction')]);

            wizardUnitComponent.splitSelectedPdf();
            await wizardUnitComponentFixture.whenStable();

            expect(navigateSpy.mock.calls[0][1]?.state).toMatchObject({ sourceUnit: { id: 3, name: 'Introduction' }, returnToEditor: true });
        });

        it('should open the split page with another PDF the user chooses', () => {
            const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate');
            wizardUnitComponentFixture.detectChanges();
            const input: HTMLInputElement = wizardUnitComponentFixture.nativeElement.querySelector('[data-testid="lecture-split-other-pdf-input"]');
            const pickerSpy = vi.spyOn(input, 'click').mockImplementation(() => {});

            wizardUnitComponentFixture.nativeElement.querySelector('[data-testid="lecture-split-other-pdf"]').click();
            expect(pickerSpy).toHaveBeenCalledOnce();

            Object.defineProperty(input, 'files', { value: [new File(['%PDF'], 'Week 2.PDF', { type: 'application/pdf' })] });
            input.dispatchEvent(new Event('change'));

            const [commands, extras] = navigateSpy.mock.calls[0];
            expect(commands).toEqual(['course-management', 7, 'lectures', 1, 'unit-management', 'attachment-video-units', 'process']);
            const state = extras?.state as { file: File; fileName: string; sourceUnit?: unknown; returnToEditor: boolean };
            // The split only accepts file names that end in a lowercase .pdf.
            expect(state.file.name).toBe('Week 2.pdf');
            expect(state.sourceUnit).toBeUndefined();
            expect(state.returnToEditor).toBe(true);
        });

        it('should stay on the page and report it when the PDF cannot be loaded', async () => {
            const router = TestBed.inject(Router);
            const navigateSpy = vi.spyOn(router, 'navigate');
            vi.spyOn(attachmentVideoUnitService, 'getAttachmentFile').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            const alertService = TestBed.inject(AlertService);
            const errorSpy = vi.spyOn(alertService, 'error');
            wizardUnitComponent.lectureUnits.set([pdfUnit(3, 'Introduction')]);

            wizardUnitComponent.splitSelectedPdf();
            await wizardUnitComponentFixture.whenStable();

            expect(navigateSpy).not.toHaveBeenCalled();
            expect(errorSpy).toHaveBeenCalled();
            expect(wizardUnitComponent.isPreparingSplit()).toBe(false);
        });
    });

    describe('editing an item in place', () => {
        let textUnitService: TextUnitService;

        const competencyLink = () => new CompetencyLectureUnitLink({ id: 1, masteryThreshold: 0, optional: false, taxonomy: undefined, title: 'Testing' }, undefined, 1);

        function savedTextUnit(id: number, name: string): TextUnit {
            const unit = new TextUnit();
            unit.id = id;
            unit.name = name;
            unit.content = 'Content';
            return unit;
        }

        function savedOnlineUnit(): OnlineUnit {
            const unit = new OnlineUnit();
            unit.id = 4;
            unit.name = 'Artemis';
            unit.source = 'https://artemis.tum.de';
            return unit;
        }

        function textChange(name: string, immediate = false, valid = true): UnitFormChange<TextUnitFormData> {
            return { data: { name, content: 'Content' }, immediate, valid };
        }

        const savedResponse = () => of(new HttpResponse<TextUnit>({ status: 200 }));

        beforeEach(() => {
            vi.useFakeTimers();
            textUnitService = TestBed.inject(TextUnitService);
            wizardUnitComponentFixture.detectChanges();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should open the form of the item with its data, including its competencies', () => {
            const unit = savedTextUnit(3, 'Reading');
            unit.competencyLinks = [competencyLink()];

            wizardUnitComponent.startEditLectureUnit(unit);

            expect(wizardUnitComponent.editingUnitId()).toBe(3);
            expect(wizardUnitComponent.isEditingLectureUnit()).toBe(true);
            expect(wizardUnitComponent.isTextUnitFormOpen()).toBe(true);
            expect(wizardUnitComponent.textUnitFormData()).toEqual({ name: 'Reading', releaseDate: undefined, content: 'Content', competencyLinks: unit.competencyLinks });
            expect(wizardUnitComponent.autosaveState()).toEqual({ kind: 'idle' });
        });

        it('should save typed text after the pause, without an alert, and show when it was saved', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValue(savedResponse());
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));

            wizardUnitComponent.onTextUnitChanged(textChange('Reading list'));
            expect(wizardUnitComponent.autosaveState().kind).toBe('saving');
            vi.advanceTimersByTime(AUTOSAVE_DELAY_MS - 1);
            expect(updateSpy).not.toHaveBeenCalled();

            vi.advanceTimersByTime(1);
            expect(updateSpy).toHaveBeenCalledOnce();
            const [sentUnit, lectureId, context] = updateSpy.mock.calls[0];
            expect(sentUnit.id).toBe(3);
            expect(sentUnit.name).toBe('Reading list');
            expect(lectureId).toBe(1);
            expect(context?.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
            expect(wizardUnitComponent.autosaveState().kind).toBe('saved');
            expect(unitManagementComponentMock.replaceLectureUnit).toHaveBeenCalledWith(expect.objectContaining({ id: 3, name: 'Reading list' }));
            expect(unitManagementComponentMock.loadData).not.toHaveBeenCalled();
            expect(wizardUnitComponent.hasUnsavedContent()).toBe(false);
        });

        it('should save a choice at once and send nothing for a change that restores the saved state', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValue(savedResponse());
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));

            wizardUnitComponent.onTextUnitChanged(textChange('Reading', true));
            expect(updateSpy).not.toHaveBeenCalled();
            expect(wizardUnitComponent.autosaveState()).toEqual({ kind: 'idle' });

            wizardUnitComponent.onTextUnitChanged(textChange('Reading list', true));
            expect(updateSpy).toHaveBeenCalledOnce();

            wizardUnitComponent.onTextUnitChanged(textChange('Reading list', true));
            expect(updateSpy).toHaveBeenCalledOnce();
            expect(wizardUnitComponent.autosaveState().kind).toBe('saved');
        });

        it('should send one save at a time and the newest change once it completes', () => {
            const firstResponse = new Subject<HttpResponse<TextUnit>>();
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValueOnce(firstResponse).mockReturnValue(savedResponse());
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));

            wizardUnitComponent.onTextUnitChanged(textChange('Reading l', true));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading list', true));
            expect(updateSpy).toHaveBeenCalledOnce();
            expect(wizardUnitComponent.hasUnsavedContent()).toBe(false);

            firstResponse.next(new HttpResponse({ status: 200 }));
            firstResponse.complete();

            expect(updateSpy).toHaveBeenCalledTimes(2);
            expect(updateSpy.mock.calls[1][0].name).toBe('Reading list');
            expect(wizardUnitComponent.autosaveState().kind).toBe('saved');
        });

        it('should keep a failed change, say so in the item instead of an alert, and send it again on retry', () => {
            const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
            const updateSpy = vi
                .spyOn(textUnitService, 'update')
                .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400, error: { title: 'The name is too long' } })))
                .mockReturnValue(savedResponse());
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));

            wizardUnitComponent.onTextUnitChanged(textChange('Reading list', true));
            expect(wizardUnitComponent.autosaveState()).toEqual({ kind: 'failed', reason: 'The name is too long' });
            expect(wizardUnitComponent.hasUnsavedContent()).toBe(true);
            expect(alertSpy).not.toHaveBeenCalled();

            wizardUnitComponent.retryAutosave();
            expect(updateSpy).toHaveBeenCalledTimes(2);
            expect(updateSpy.mock.calls[1][0].name).toBe('Reading list');
            expect(wizardUnitComponent.autosaveState().kind).toBe('saved');
            expect(wizardUnitComponent.hasUnsavedContent()).toBe(false);
        });

        it('should show a form that became invalid while a save ran', () => {
            const response = new Subject<HttpResponse<TextUnit>>();
            vi.spyOn(textUnitService, 'update').mockReturnValueOnce(response);
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));

            wizardUnitComponent.onTextUnitChanged(textChange('Reading list', true));
            wizardUnitComponent.onTextUnitChanged(textChange('', false, false));
            response.next(new HttpResponse({ status: 200 }));
            response.complete();

            expect(wizardUnitComponent.autosaveState().kind).toBe('invalid');
            expect(wizardUnitComponent.hasUnsavedContent()).toBe(true);
        });

        it('should send the saved state again when the user returns to it while another state is being saved', () => {
            const response = new Subject<HttpResponse<TextUnit>>();
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValueOnce(response).mockReturnValue(savedResponse());
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));

            wizardUnitComponent.onTextUnitChanged(textChange('Reading list', true));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading', true));
            response.next(new HttpResponse({ status: 200 }));
            response.complete();

            expect(updateSpy).toHaveBeenCalledTimes(2);
            expect(updateSpy.mock.calls[1][0].name).toBe('Reading');
            expect(wizardUnitComponent.autosaveState().kind).toBe('saved');
        });

        it('should send an empty list when the last competency is removed, because missing links mean unchanged', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValue(savedResponse());
            const unit = savedTextUnit(3, 'Reading');
            unit.competencyLinks = [competencyLink()];
            wizardUnitComponent.startEditLectureUnit(unit);

            wizardUnitComponent.onTextUnitChanged({ data: { name: 'Reading', content: 'Content', competencyLinks: undefined }, immediate: true, valid: true });

            expect(updateSpy.mock.calls[0][0].competencyLinks).toEqual([]);
        });

        it('should send no links for an item that has none', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValue(savedResponse());
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));

            wizardUnitComponent.onTextUnitChanged(textChange('Reading list', true));

            expect(updateSpy.mock.calls[0][0].competencyLinks).toBeUndefined();
        });

        it('should report a save that fails after the page was left in an alert', () => {
            const response = new Subject<HttpResponse<TextUnit>>();
            vi.spyOn(textUnitService, 'update').mockReturnValueOnce(response);
            const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading list', true));

            wizardUnitComponentFixture.destroy();
            response.error(new HttpErrorResponse({ status: 500 }));

            expect(alertSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.lecture.unitEditor.failedAfterLeaving');
        });

        it('should keep the form open when it becomes invalid while Save waits for a save', () => {
            const response = new Subject<HttpResponse<TextUnit>>();
            vi.spyOn(textUnitService, 'update').mockReturnValueOnce(response);
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading list', true));

            wizardUnitComponent.saveAndCloseEditor();
            wizardUnitComponent.onTextUnitChanged(textChange('', false, false));
            response.next(new HttpResponse({ status: 200 }));
            response.complete();

            expect(wizardUnitComponent.editingUnitId()).toBe(3);
            expect(wizardUnitComponent.autosaveState().kind).toBe('invalid');
        });

        it('should let the user discard a change that cannot be saved', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update');
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            wizardUnitComponent.onTextUnitChanged(textChange('', false, false));

            wizardUnitComponent.discardUnsavedChange();

            expect(wizardUnitComponent.editingUnitId()).toBeUndefined();
            expect(wizardUnitComponent.hasUnsavedContent()).toBe(false);
            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should let the user discard a change that failed to save, and continue at the Edit button of the item', () => {
            vi.spyOn(textUnitService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading list', true));
            expect(wizardUnitComponent.autosaveState().kind).toBe('failed');

            wizardUnitComponent.discardUnsavedChange();
            TestBed.tick();

            expect(wizardUnitComponent.editingUnitId()).toBeUndefined();
            expect(wizardUnitComponent.hasUnsavedContent()).toBe(false);
            expect(unitManagementComponentMock.focusEditButton).toHaveBeenCalledExactlyOnceWith(3);
        });

        it('should continue in the first field of an item that opens', () => {
            const firstField = { focus: vi.fn() };
            wizardUnitComponent.editFormContainer = computed(
                () => ({ nativeElement: { scrollIntoView: vi.fn(), querySelector: vi.fn(() => firstField) } }) as unknown as ElementRef,
            ) as Signal<ElementRef | undefined>;

            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            vi.advanceTimersByTime(100);

            expect(firstField.focus).toHaveBeenCalledExactlyOnceWith({ preventScroll: true });
        });

        it('should save a waiting change at once on Enter in a single-line field of the item, but not on Enter in its markdown editor', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValue(savedResponse());
            const editor = document.createElement('div');
            const nameField = document.createElement('input');
            nameField.type = 'text';
            const markdownField = document.createElement('textarea');
            editor.append(nameField, markdownField);
            (wizardUnitComponent as unknown as { inPlaceEditor: Signal<ElementRef<HTMLElement>> }).inPlaceEditor = signal(new ElementRef(editor)).asReadonly();
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading list'));
            const enterIn = (target: HTMLElement) => wizardUnitComponent.onEditorEnter({ target, defaultPrevented: false } as unknown as Event);

            enterIn(markdownField);
            const outsideField = document.createElement('input');
            outsideField.type = 'text';
            enterIn(outsideField);
            expect(updateSpy).not.toHaveBeenCalled();

            enterIn(nameField);
            expect(updateSpy).toHaveBeenCalledOnce();
            expect(wizardUnitComponent.editingUnitId()).toBe(3);
        });

        it('should report text the markdown editor has not reported yet before saving and closing', () => {
            const flushPendingEdits = vi.fn();
            wizardUnitComponent.textUnitForm = signal({ flushPendingEdits } as unknown as TextUnitFormComponent).asReadonly() as Signal<TextUnitFormComponent | undefined>;
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));

            wizardUnitComponent.onEditorFocusOut();
            wizardUnitComponent.saveAndCloseEditor();

            expect(flushPendingEdits).toHaveBeenCalledTimes(2);
        });

        it('should not count the running save of a deleted item for the item opened next', () => {
            const response = new Subject<HttpResponse<TextUnit>>();
            vi.spyOn(textUnitService, 'update').mockReturnValueOnce(response);
            wizardUnitComponent.onLectureUnitsChange([savedTextUnit(3, 'Reading'), savedOnlineUnit()]);
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading list', true));

            wizardUnitComponent.onLectureUnitsChange([savedOnlineUnit()]);
            wizardUnitComponent.startEditLectureUnit(savedOnlineUnit());

            expect(wizardUnitComponent.editingUnitId()).toBe(4);
            expect(wizardUnitComponent.autosaveState()).toEqual({ kind: 'idle' });
            response.next(new HttpResponse({ status: 200 }));
            response.complete();
            expect(wizardUnitComponent.editingUnitId()).toBe(4);
        });

        it('should keep the form of an item that is not in the list yet', () => {
            wizardUnitComponent.onLectureUnitsChange([savedOnlineUnit()]);
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Uploaded just now'));

            wizardUnitComponent.onLectureUnitsChange([savedOnlineUnit()]);

            expect(wizardUnitComponent.editingUnitId()).toBe(3);
        });

        it('should not save a form that cannot be saved and keep it open on Save', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update');
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));

            wizardUnitComponent.onTextUnitChanged(textChange('', false, false));
            vi.advanceTimersByTime(AUTOSAVE_DELAY_MS);
            wizardUnitComponent.saveAndCloseEditor();

            expect(updateSpy).not.toHaveBeenCalled();
            expect(wizardUnitComponent.autosaveState().kind).toBe('invalid');
            expect(wizardUnitComponent.hasUnsavedContent()).toBe(true);
            expect(wizardUnitComponent.editingUnitId()).toBe(3);
        });

        it('should save at once and close the form on Save', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValue(savedResponse());
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading list'));

            wizardUnitComponent.saveAndCloseEditor();
            TestBed.tick();

            expect(updateSpy).toHaveBeenCalledOnce();
            expect(wizardUnitComponent.editingUnitId()).toBeUndefined();
            expect(wizardUnitComponent.isTextUnitFormOpen()).toBe(false);
            // The Save button is gone with the form, so the keyboard focus continues at the Edit button of the item.
            expect(unitManagementComponentMock.focusEditButton).toHaveBeenCalledExactlyOnceWith(3);
            vi.advanceTimersByTime(AUTOSAVE_DELAY_MS);
            expect(updateSpy).toHaveBeenCalledOnce();
        });

        it('should save and close the open item before opening another one', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValue(savedResponse());
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading list'));

            wizardUnitComponent.startEditLectureUnit(savedOnlineUnit());

            expect(updateSpy).toHaveBeenCalledOnce();
            expect(updateSpy.mock.calls[0][0].name).toBe('Reading list');
            expect(wizardUnitComponent.editingUnitId()).toBe(4);
            expect(wizardUnitComponent.isOnlineUnitFormOpen()).toBe(true);
            expect(wizardUnitComponent.isTextUnitFormOpen()).toBe(false);
            expect(wizardUnitComponent.autosaveState()).toEqual({ kind: 'idle' });
        });

        it('should save and close the open item before a new item is added', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValue(savedResponse());
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading list'));

            wizardUnitComponent.onCreateLectureUnit(LectureUnitType.ONLINE);

            expect(updateSpy).toHaveBeenCalledOnce();
            expect(wizardUnitComponent.editingUnitId()).toBeUndefined();
            expect(wizardUnitComponent.isEditingLectureUnit()).toBe(false);
            expect(wizardUnitComponent.isOnlineUnitFormOpen()).toBe(true);
        });

        it('should close the form when its item is deleted', () => {
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));

            wizardUnitComponent.onLectureUnitsChange([savedTextUnit(3, 'Reading')]);
            expect(wizardUnitComponent.editingUnitId()).toBe(3);

            wizardUnitComponent.onLectureUnitsChange([]);
            expect(wizardUnitComponent.editingUnitId()).toBeUndefined();
            expect(wizardUnitComponent.isTextUnitFormOpen()).toBe(false);
            expect(wizardUnitComponent.lectureUnits()).toEqual([]);
        });

        it('should save a waiting change as soon as the user leaves a field', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValue(savedResponse());
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading list'));

            wizardUnitComponent.onEditorFocusOut();

            expect(updateSpy).toHaveBeenCalledOnce();
        });

        it('should send a waiting change when the page is left', () => {
            const updateSpy = vi.spyOn(textUnitService, 'update').mockReturnValue(savedResponse());
            wizardUnitComponent.startEditLectureUnit(savedTextUnit(3, 'Reading'));
            wizardUnitComponent.onTextUnitChanged(textChange('Reading list'));

            wizardUnitComponentFixture.destroy();

            expect(updateSpy).toHaveBeenCalledOnce();
            expect(updateSpy.mock.calls[0][0].name).toBe('Reading list');
        });

        it('should save an online resource without an alert', () => {
            const updateSpy = vi.spyOn(TestBed.inject(OnlineUnitService), 'update').mockReturnValue(of(new HttpResponse<OnlineUnit>({ status: 200 })));
            wizardUnitComponent.startEditLectureUnit(savedOnlineUnit());

            wizardUnitComponent.onOnlineUnitChanged({ data: { name: 'Artemis', source: 'https://artemis.tum.de', description: 'Start here' }, immediate: false, valid: true });
            vi.advanceTimersByTime(AUTOSAVE_DELAY_MS);

            expect(updateSpy).toHaveBeenCalledOnce();
            const [sentUnit, , context] = updateSpy.mock.calls[0];
            expect(sentUnit.description).toBe('Start here');
            expect(sentUnit.source).toBe('https://artemis.tum.de');
            expect(context?.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
        });

        describe('file and video items', () => {
            let updateSpy: MockInstance<AttachmentVideoUnitService['update']>;
            let formMock: { takeOverSavedFile: Mock; takeOverSavedVideoSource: Mock; hasUnconfirmedContent: Mock };

            function savedFileUnit(): AttachmentVideoUnit {
                const unit = new AttachmentVideoUnit();
                unit.id = 5;
                unit.name = 'Slides';
                unit.videoSource = 'https://live.rbg.tum.de/w/old';
                unit.attachment = { id: 8, link: 'attachments/slides.pdf', version: 1 } as Attachment;
                return unit;
            }

            function fileFormData(formProperties: AttachmentVideoUnitFormData['formProperties'], file?: File): AttachmentVideoUnitFormData {
                return {
                    formProperties: { name: 'Slides', videoSource: 'https://live.rbg.tum.de/w/old', ...formProperties },
                    fileProperties: { file, fileName: file?.name ?? 'attachments/slides.pdf' },
                };
            }

            beforeEach(() => {
                const responseUnit = new AttachmentVideoUnit();
                responseUnit.attachment = { id: 8, link: 'attachments/slides-v2.pdf', version: 2 } as Attachment;
                updateSpy = vi.spyOn(attachmentVideoUnitService, 'update').mockReturnValue(of(new HttpResponse({ body: responseUnit, status: 200 })));
                formMock = { takeOverSavedFile: vi.fn(), takeOverSavedVideoSource: vi.fn(), hasUnconfirmedContent: vi.fn(() => false) };
                wizardUnitComponent.attachmentVideoUnitForm = signal(formMock as unknown as AttachmentVideoUnitFormComponent).asReadonly() as Signal<
                    AttachmentVideoUnitFormComponent | undefined
                >;
                wizardUnitComponent.startEditLectureUnit(savedFileUnit());
            });

            it('should save the details but neither a new file nor a video URL that were not confirmed', async () => {
                const newFile = new File(['%PDF'], 'slides-v2.pdf', { type: 'application/pdf' });
                const data = fileFormData({ description: 'Week 1', videoSource: 'https://live.rbg.tum.de/w/new', updateNotificationText: 'New slides' }, newFile);

                wizardUnitComponent.onAttachmentVideoUnitChanged({ data, immediate: true, valid: true });

                expect(updateSpy).toHaveBeenCalledOnce();
                const [lectureId, unitId, formData, notificationText, context] = updateSpy.mock.calls[0];
                expect([lectureId, unitId]).toEqual([1, 5]);
                expect(formData.has('file')).toBe(false);
                expect(notificationText).toBeUndefined();
                expect(context?.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
                await expect(getAttachmentVideoUnitPayload(formData)).resolves.toMatchObject({
                    description: 'Week 1',
                    videoSource: 'https://live.rbg.tum.de/w/old',
                    attachmentUpdateIntent: AttachmentUpdateIntent.NO_FILE_CHANGE,
                });
                expect(formMock.takeOverSavedFile).not.toHaveBeenCalled();
                expect(formMock.takeOverSavedVideoSource).not.toHaveBeenCalled();
            });

            it('should upload a confirmed file as the next version and notify students when asked', async () => {
                const newFile = new File(['%PDF'], 'slides-v2.pdf', { type: 'application/pdf' });
                const data = fileFormData({}, newFile);
                data.fileProperties.notifyStudents = true;

                wizardUnitComponent.onAttachmentFileUploadRequested(data);

                expect(updateSpy).toHaveBeenCalledOnce();
                const [, , formData, notificationText] = updateSpy.mock.calls[0];
                expect(formData.get('file')).toBeInstanceOf(File);
                // The server notifies whenever the parameter is present; it does not use the text.
                expect(notificationText).toBe('');
                await expect(getAttachmentVideoUnitPayload(formData)).resolves.toMatchObject({ attachmentUpdateIntent: AttachmentUpdateIntent.FILE_UPLOAD });
                expect(formMock.takeOverSavedFile).toHaveBeenCalledExactlyOnceWith('attachments/slides-v2.pdf', 2, newFile);
                expect(unitManagementComponentMock.replaceLectureUnit).toHaveBeenCalledWith(
                    expect.objectContaining({ id: 5, attachment: expect.objectContaining({ version: 2 }) }),
                );
                expect(wizardUnitComponent.autosaveState().kind).toBe('saved');
            });

            it('should keep a confirmed upload that waits behind another save when the details change', async () => {
                const firstResponse = new Subject<HttpResponse<AttachmentVideoUnit>>();
                const responseUnit = new AttachmentVideoUnit();
                responseUnit.attachment = { id: 8, link: 'attachments/slides-v2.pdf', version: 2 } as Attachment;
                updateSpy.mockReset();
                updateSpy.mockReturnValueOnce(firstResponse).mockReturnValue(of(new HttpResponse({ body: responseUnit, status: 200 })));
                const newFile = new File(['%PDF'], 'slides-v2.pdf', { type: 'application/pdf' });

                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ description: 'Week 1' }), immediate: true, valid: true });
                wizardUnitComponent.onAttachmentFileUploadRequested(fileFormData({ description: 'Week 1' }, newFile));
                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ description: 'Week 1 and 2' }, newFile), immediate: false, valid: true });
                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ name: '' }, newFile), immediate: false, valid: false });
                expect(updateSpy).toHaveBeenCalledOnce();

                firstResponse.next(new HttpResponse({ body: new AttachmentVideoUnit(), status: 200 }));
                firstResponse.complete();

                expect(updateSpy).toHaveBeenCalledTimes(2);
                const uploadData = updateSpy.mock.calls[1][2];
                expect(uploadData.get('file')).toBeInstanceOf(File);
                await expect(getAttachmentVideoUnitPayload(uploadData)).resolves.toMatchObject({ attachmentUpdateIntent: AttachmentUpdateIntent.FILE_UPLOAD });
                expect(formMock.takeOverSavedFile).toHaveBeenCalledExactlyOnceWith('attachments/slides-v2.pdf', 2, newFile);
                expect(updateSpy.mock.calls[1][3]).toBeUndefined();
                // The form cannot be saved anymore, so the item says so instead of "saved".
                expect(wizardUnitComponent.autosaveState().kind).toBe('invalid');
            });

            it('should keep a confirmed video URL that waits behind another save when the details change', async () => {
                const firstResponse = new Subject<HttpResponse<AttachmentVideoUnit>>();
                updateSpy.mockReturnValueOnce(firstResponse);
                const newSource = 'https://live.rbg.tum.de/w/new';

                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ description: 'Week 1' }), immediate: true, valid: true });
                wizardUnitComponent.onVideoSourceSaveRequested(fileFormData({ description: 'Week 1', videoSource: newSource }));
                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ description: 'Week 1', videoSource: newSource }), immediate: true, valid: true });
                firstResponse.next(new HttpResponse({ body: new AttachmentVideoUnit(), status: 200 }));
                firstResponse.complete();

                expect(updateSpy).toHaveBeenCalledTimes(2);
                await expect(getAttachmentVideoUnitPayload(updateSpy.mock.calls[1][2])).resolves.toMatchObject({ videoSource: newSource });
                expect(formMock.takeOverSavedVideoSource).toHaveBeenCalledExactlyOnceWith(newSource);
                expect(wizardUnitComponent.autosaveState().kind).toBe('saved');
            });

            it('should send a failed upload again only on Retry, not with later changes', async () => {
                const responseUnit = new AttachmentVideoUnit();
                responseUnit.attachment = { id: 8, link: 'attachments/slides-v2.pdf', version: 2 } as Attachment;
                updateSpy.mockReset();
                updateSpy
                    .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400, error: { title: 'The file is too big' } })))
                    .mockReturnValue(of(new HttpResponse({ body: responseUnit, status: 200 })));
                const newFile = new File(['%PDF'], 'slides-v2.pdf', { type: 'application/pdf' });

                wizardUnitComponent.onAttachmentFileUploadRequested(fileFormData({}, newFile));
                expect(wizardUnitComponent.autosaveState()).toEqual({ kind: 'failed', reason: 'The file is too big' });
                expect(wizardUnitComponent.hasUnsavedContent()).toBe(true);

                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ description: 'Week 1' }, newFile), immediate: true, valid: true });
                wizardUnitComponent.onEditorFocusOut();
                expect(updateSpy).toHaveBeenCalledTimes(2);
                expect(updateSpy.mock.calls[1][2].has('file')).toBe(false);
                expect(wizardUnitComponent.autosaveState().kind).toBe('failed');

                wizardUnitComponent.retryAutosave();
                expect(updateSpy).toHaveBeenCalledTimes(3);
                expect(updateSpy.mock.calls[2][2].get('file')).toBeInstanceOf(File);
                expect(wizardUnitComponent.autosaveState().kind).toBe('saved');
                expect(wizardUnitComponent.hasUnsavedContent()).toBe(false);
            });

            it('should not send a failed upload once the user keeps the current file', () => {
                updateSpy.mockReset();
                updateSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

                wizardUnitComponent.onAttachmentFileUploadRequested(fileFormData({}, new File(['%PDF'], 'slides-v2.pdf', { type: 'application/pdf' })));
                wizardUnitComponent.onConfirmedContentWithdrawn('file');
                wizardUnitComponent.retryAutosave();
                wizardUnitComponent.saveAndCloseEditor();

                expect(updateSpy).toHaveBeenCalledOnce();
                expect(wizardUnitComponent.editingUnitId()).toBeUndefined();
            });

            it('should send a confirmed upload that waits with the newest details, also when they return to what runs', async () => {
                const running = new Subject<HttpResponse<AttachmentVideoUnit>>();
                updateSpy.mockReturnValueOnce(running);
                const newFile = new File(['%PDF'], 'slides-v2.pdf', { type: 'application/pdf' });

                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ description: 'B' }), immediate: true, valid: true });
                wizardUnitComponent.onAttachmentFileUploadRequested(fileFormData({ description: 'C' }, newFile));
                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ description: 'B' }, newFile), immediate: true, valid: true });
                running.next(new HttpResponse({ body: new AttachmentVideoUnit(), status: 200 }));
                running.complete();

                // The upload carries the details, so they need no request of their own.
                expect(updateSpy).toHaveBeenCalledTimes(2);
                expect(updateSpy.mock.calls[1][2].get('file')).toBeInstanceOf(File);
                await expect(getAttachmentVideoUnitPayload(updateSpy.mock.calls[1][2])).resolves.toMatchObject({ description: 'B' });
                expect(wizardUnitComponent.autosaveState().kind).toBe('saved');
            });

            it('should send a retried upload with the details saved meanwhile, not with those of the confirmation', async () => {
                const responseUnit = new AttachmentVideoUnit();
                responseUnit.attachment = { id: 8, link: 'attachments/slides-v2.pdf', version: 2 } as Attachment;
                updateSpy.mockReset();
                updateSpy.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 }))).mockReturnValue(of(new HttpResponse({ body: responseUnit, status: 200 })));
                const newFile = new File(['%PDF'], 'slides-v2.pdf', { type: 'application/pdf' });

                wizardUnitComponent.onAttachmentFileUploadRequested(fileFormData({ description: 'Week 1' }, newFile));
                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ description: 'Week 1 and 2' }, newFile), immediate: true, valid: true });
                expect(updateSpy).toHaveBeenCalledTimes(2);

                wizardUnitComponent.retryAutosave();

                expect(updateSpy).toHaveBeenCalledTimes(3);
                expect(updateSpy.mock.calls[2][2].get('file')).toBeInstanceOf(File);
                await expect(getAttachmentVideoUnitPayload(updateSpy.mock.calls[2][2])).resolves.toMatchObject({
                    description: 'Week 1 and 2',
                    attachmentUpdateIntent: AttachmentUpdateIntent.FILE_UPLOAD,
                });
            });

            it('should still save a change of the details that waited behind an upload that failed', async () => {
                const running = new Subject<HttpResponse<AttachmentVideoUnit>>();
                updateSpy.mockReset();
                updateSpy
                    .mockReturnValueOnce(running)
                    .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400, error: { title: 'The file is too big' } })))
                    .mockReturnValue(of(new HttpResponse({ body: new AttachmentVideoUnit(), status: 200 })));
                const newFile = new File(['%PDF'], 'slides-v2.pdf', { type: 'application/pdf' });

                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ description: 'Week 1' }), immediate: true, valid: true });
                wizardUnitComponent.onAttachmentFileUploadRequested(fileFormData({ description: 'Week 1' }, newFile));
                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ description: 'Week 1 and 2' }, newFile), immediate: true, valid: true });
                running.next(new HttpResponse({ body: new AttachmentVideoUnit(), status: 200 }));
                running.complete();

                expect(updateSpy).toHaveBeenCalledTimes(3);
                expect(updateSpy.mock.calls[2][2].has('file')).toBe(false);
                await expect(getAttachmentVideoUnitPayload(updateSpy.mock.calls[2][2])).resolves.toMatchObject({ description: 'Week 1 and 2' });
                // Only the file waits for Retry.
                expect(wizardUnitComponent.autosaveState()).toEqual({ kind: 'failed', reason: 'The file is too big' });
                expect(wizardUnitComponent.isSaving()).toBe(false);
            });

            it('should tell the form which confirmed file is being sent, which cannot be taken back anymore', () => {
                const running = new Subject<HttpResponse<AttachmentVideoUnit>>();
                updateSpy.mockReturnValueOnce(running);

                wizardUnitComponent.onAttachmentFileUploadRequested(fileFormData({}, new File(['%PDF'], 'slides-v2.pdf', { type: 'application/pdf' })));
                expect(wizardUnitComponent.savingConfirmed()).toBe('file');

                running.next(new HttpResponse({ body: new AttachmentVideoUnit(), status: 200 }));
                running.complete();
                expect(wizardUnitComponent.savingConfirmed()).toBeUndefined();
            });

            it('should keep the item open with a hint while a new file or video URL was neither confirmed nor discarded', () => {
                formMock.hasUnconfirmedContent.mockReturnValue(true);
                wizardUnitComponent.onAttachmentVideoUnitChanged({ data: fileFormData({ description: 'Week 1' }), immediate: false, valid: true });

                wizardUnitComponent.saveAndCloseEditor();
                wizardUnitComponent.onCreateLectureUnit(LectureUnitType.TEXT);

                expect(wizardUnitComponent.editingUnitId()).toBe(5);
                expect(wizardUnitComponent.isTextUnitFormOpen()).toBe(false);
                expect(wizardUnitComponent.showsUnconfirmedContentHint()).toBe(true);
                // What can be saved is saved anyway.
                expect(updateSpy).toHaveBeenCalledOnce();
            });

            it('should ask before a new file or video link that was not confirmed is left', () => {
                formMock.hasUnconfirmedContent.mockReturnValue(true);
                wizardUnitComponent.startEditLectureUnit(savedFileUnit());

                expect(wizardUnitComponent.hasUnsavedContent()).toBe(true);
            });

            it('should not upload an empty file', async () => {
                wizardUnitComponent.onAttachmentFileUploadRequested(fileFormData({}, new File([], 'empty.pdf', { type: 'application/pdf' })));

                const formData = updateSpy.mock.calls[0][2];
                expect(formData.has('file')).toBe(false);
                await expect(getAttachmentVideoUnitPayload(formData)).resolves.toMatchObject({ attachmentUpdateIntent: AttachmentUpdateIntent.NO_FILE_CHANGE });
            });

            it('should save a confirmed video URL and let the form take it over', async () => {
                wizardUnitComponent.onVideoSourceSaveRequested(fileFormData({ videoSource: 'https://live.rbg.tum.de/w/new' }));

                const formData = updateSpy.mock.calls[0][2];
                expect(formData.has('file')).toBe(false);
                await expect(getAttachmentVideoUnitPayload(formData)).resolves.toMatchObject({
                    videoSource: 'https://live.rbg.tum.de/w/new',
                    attachmentUpdateIntent: AttachmentUpdateIntent.NO_FILE_CHANGE,
                });
                expect(formMock.takeOverSavedVideoSource).toHaveBeenCalledExactlyOnceWith('https://live.rbg.tum.de/w/new');
                expect(formMock.takeOverSavedFile).not.toHaveBeenCalled();
            });
        });
    });
});
