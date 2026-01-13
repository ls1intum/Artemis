import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { TextUnitFormComponent, TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { OnlineUnitFormComponent, OnlineUnitFormData } from 'app/lecture/manage/lecture-units/online-unit-form/online-unit-form.component';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { CreateExerciseUnitComponent } from 'app/lecture/manage/lecture-units/create-exercise-unit/create-exercise-unit.component';
import { LectureUpdateUnitsComponent } from 'app/lecture/manage/lecture-units/lecture-units.component';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { PdfDropZoneComponent } from 'app/lecture/manage/pdf-drop-zone/pdf-drop-zone.component';
import { Component, ElementRef, NO_ERRORS_SCHEMA, Signal, computed, input, output } from '@angular/core';
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
    isFormValid = () => true;
}

@Component({ selector: 'jhi-online-unit-form', standalone: true, template: '' })
class OnlineUnitFormStubComponent {
    formData = input<OnlineUnitFormData>();
    isEditMode = input<boolean>(false);
    hasCancelButton = input<boolean>(false);
    formSubmitted = output<OnlineUnitFormData>();
    isFormValid = () => true;
}

@Component({ selector: 'jhi-attachment-video-unit-form', standalone: true, template: '' })
class AttachmentVideoUnitFormStubComponent {
    formData = input<AttachmentVideoUnitFormData>();
    isEditMode = input<boolean>(false);
    hasCancelButton = input<boolean>(false);
    formSubmitted = output<AttachmentVideoUnitFormData>();
    isFormValid = () => true;
}

describe('LectureUpdateUnitsComponent', () => {
    setupTestBed({ zoneless: true });

    let wizardUnitComponentFixture: ComponentFixture<LectureUpdateUnitsComponent>;
    let wizardUnitComponent: LectureUpdateUnitsComponent;
    let attachmentVideoUnitService: AttachmentVideoUnitService;
    let unitManagementComponentMock: Pick<LectureUnitManagementComponent, 'loadData'>;

    const mockUnitManagementComponent = () => {
        unitManagementComponentMock = {
            loadData: vi.fn(),
        } as Pick<LectureUnitManagementComponent, 'loadData'>;

        wizardUnitComponent.unitManagementComponent = computed(() => unitManagementComponentMock as LectureUnitManagementComponent) as Signal<
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
                remove: { imports: [PdfDropZoneComponent, TextUnitFormComponent, OnlineUnitFormComponent, AttachmentVideoUnitFormComponent] },
                add: { imports: [PdfDropZoneStubComponent, TextUnitFormStubComponent, OnlineUnitFormStubComponent, AttachmentVideoUnitFormStubComponent] },
            })
            .compileComponents();

        wizardUnitComponentFixture = TestBed.createComponent(LectureUpdateUnitsComponent);
        wizardUnitComponent = wizardUnitComponentFixture.componentInstance;

        const lecture = new Lecture();
        lecture.id = 1;
        wizardUnitComponentFixture.componentRef.setInput('lecture', lecture);

        attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        wizardUnitComponent.editFormContainer = computed(() => ({ nativeElement: { scrollIntoView: vi.fn() } }) as unknown as ElementRef) as Signal<ElementRef | undefined>;
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
        wizardUnitComponent.createEditTextUnit(formData);
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
        wizardUnitComponent.createEditTextUnit(formData);
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
        wizardUnitComponent.createEditTextUnit(formData);
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
        wizardUnitComponent.createEditOnlineUnit(formData);
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
        wizardUnitComponent.createEditOnlineUnit(formDate);
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
        wizardUnitComponent.createEditOnlineUnit(formData);
        await wizardUnitComponentFixture.whenStable();

        expect(createStub).not.toHaveBeenCalled();
    });

    it('should send POST request upon attachment form submission and update units', async () => {
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
        const createAttachmentVideoUnitStub = vi.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(of(attachmentVideoUnitResponse));

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        const updateSpy = vi.spyOn(unitManagementComponentMock, 'loadData');

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createEditAttachmentVideoUnit(attachmentVideoUnitFormData);
        await wizardUnitComponentFixture.whenStable();

        const lectureIdCallArgument: number = createAttachmentVideoUnitStub.mock.calls[0][1];

        expect(lectureIdCallArgument).toBe(1);
        expect(createAttachmentVideoUnitStub).toHaveBeenCalledWith(expect.any(FormData), 1);
        expect(updateSpy).toHaveBeenCalledTimes(1);

        updateSpy.mockRestore();
    });

    it('should send POST request upon attachment form submission and update units when editing lecture', async () => {
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
        const createAttachmentVideoUnitStub = vi.spyOn(attachmentVideoUnitService, 'update').mockReturnValue(of(attachmentVideoUnitResponse));

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        const updateSpy = vi.spyOn(unitManagementComponentMock, 'loadData');

        wizardUnitComponent.isEditingLectureUnit.set(true);
        const editingAttachmentVideoUnit = new AttachmentVideoUnit();
        editingAttachmentVideoUnit.attachment = new Attachment();
        wizardUnitComponent.currentlyProcessedAttachmentVideoUnit.set(editingAttachmentVideoUnit);
        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);

        wizardUnitComponent.createEditAttachmentVideoUnit(attachmentVideoUnitFormData);
        await wizardUnitComponentFixture.whenStable();

        expect(createAttachmentVideoUnitStub).toHaveBeenCalledTimes(1);
        expect(updateSpy).toHaveBeenCalledTimes(1);

        updateSpy.mockRestore();
    });

    it('should show alert upon unsuccessful attachment form submission', async () => {
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

        const createAttachmentVideoUnitStub = vi.spyOn(attachmentVideoUnitService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertStub = vi.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createEditAttachmentVideoUnit(attachmentVideoUnitFormData);
        await wizardUnitComponentFixture.whenStable();

        expect(createAttachmentVideoUnitStub).toHaveBeenCalledTimes(1);
        expect(alertStub).toHaveBeenCalledTimes(1);
    });

    it('should show alert upon unsuccessful attachment form submission with error information', async () => {
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

        const createAttachmentVideoUnitStub = vi
            .spyOn(attachmentVideoUnitService, 'create')
            .mockReturnValue(throwError(() => ({ status: 404, error: { params: 'file', title: 'Test Title' } })));
        const alertStub = vi.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        await wizardUnitComponentFixture.whenStable();

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createEditAttachmentVideoUnit(attachmentVideoUnitFormData);
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
        wizardUnitComponent.createEditAttachmentVideoUnit(formData);
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
});
