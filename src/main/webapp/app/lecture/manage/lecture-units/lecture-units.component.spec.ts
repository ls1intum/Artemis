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
    let wizardUnitComponentFixture: ComponentFixture<LectureUpdateUnitsComponent>;
    let wizardUnitComponent: LectureUpdateUnitsComponent;
    let attachmentVideoUnitService: AttachmentVideoUnitService;
    let unitManagementComponentMock: Pick<LectureUnitManagementComponent, 'loadData'>;

    const mockUnitManagementComponent = () => {
        unitManagementComponentMock = {
            loadData: jest.fn(),
        } as Pick<LectureUnitManagementComponent, 'loadData'>;

        wizardUnitComponent.unitManagementComponent = computed(() => unitManagementComponentMock as LectureUnitManagementComponent) as Signal<
            LectureUnitManagementComponent | undefined
        >;
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [
                MockComponent(UnitCreationCardComponent),
                LectureUpdateUnitsComponent,
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
                { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
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
        wizardUnitComponent.lecture = new Lecture();
        wizardUnitComponent.lecture.id = 1;
        attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        wizardUnitComponent.editFormContainer = computed(() => ({ nativeElement: { scrollIntoView: jest.fn() } }) as unknown as ElementRef) as Signal<ElementRef | undefined>;
        mockUnitManagementComponent();
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

        const updateSpy = jest.spyOn(unitManagementComponentMock, 'loadData');

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

        const updateSpy = jest.spyOn(unitManagementComponentMock, 'loadData');

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

        const updateSpy = jest.spyOn(unitManagementComponentMock, 'loadData');

        wizardUnitComponent.isAttachmentVideoUnitFormOpen.set(true);
        wizardUnitComponent.createEditAttachmentVideoUnit(attachmentVideoUnitFormData);
        tick();

        const lectureIdCallArgument: number = createAttachmentVideoUnitStub.mock.calls[0][1];

        expect(lectureIdCallArgument).toBe(1);
        expect(createAttachmentVideoUnitStub).toHaveBeenCalledWith(expect.any(FormData), 1);
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

        const updateSpy = jest.spyOn(unitManagementComponentMock, 'loadData');

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

        const updateSpy = jest.spyOn(unitManagementComponentMock, 'loadData');

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

    describe('PDF drop zone', () => {
        it('should create attachment units from dropped PDF files', fakeAsync(() => {
            const alertService = TestBed.inject(AlertService);

            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 42;
            createdUnit.name = 'Test File';

            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));
            const successSpy = jest.spyOn(alertService, 'success');

            wizardUnitComponentFixture.detectChanges();
            tick();

            const loadDataSpy = jest.spyOn(unitManagementComponentMock, 'loadData');

            const pdfFile = new File(['content'], 'Test_File.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);
            tick();

            expect(createSpy).toHaveBeenCalledOnce();
            expect(successSpy).toHaveBeenCalledWith('artemisApp.lecture.pdfUpload.success');
            expect(loadDataSpy).toHaveBeenCalledOnce();

            loadDataSpy.mockRestore();
        }));

        it('should call service with correct lecture id and file', fakeAsync(() => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            wizardUnitComponentFixture.detectChanges();
            tick();

            const pdfFile = new File(['content'], 'Chapter_01_Introduction.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);
            tick();

            expect(createSpy).toHaveBeenCalledWith(wizardUnitComponent.lecture.id, pdfFile);
        }));

        it('should handle multiple PDF files sequentially', fakeAsync(() => {
            const alertService = TestBed.inject(AlertService);

            let callCount = 0;
            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockImplementation(() => {
                callCount++;
                const unit = new AttachmentVideoUnit();
                unit.id = callCount;
                return of(new HttpResponse({ body: unit, status: 201 }));
            });
            const successSpy = jest.spyOn(alertService, 'success');

            wizardUnitComponentFixture.detectChanges();
            tick();

            const pdfFiles = [
                new File(['content1'], 'file1.pdf', { type: 'application/pdf' }),
                new File(['content2'], 'file2.pdf', { type: 'application/pdf' }),
                new File(['content3'], 'file3.pdf', { type: 'application/pdf' }),
            ];

            wizardUnitComponent.onPdfFilesDropped(pdfFiles);
            tick();

            expect(createSpy).toHaveBeenCalledTimes(3);
            expect(successSpy).toHaveBeenCalledOnce();
        }));

        it('should open edit form for last created unit after upload', fakeAsync(() => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 99;
            createdUnit.name = 'Created Unit';

            jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            wizardUnitComponentFixture.detectChanges();
            tick();

            const startEditSpy = jest.spyOn(wizardUnitComponent, 'startEditLectureUnit');

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);
            tick();

            expect(startEditSpy).toHaveBeenCalledWith(createdUnit);
        }));

        it('should show error alert on upload failure', fakeAsync(() => {
            const alertService = TestBed.inject(AlertService);

            jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(throwError(() => ({ status: 400 })));
            const errorSpy = jest.spyOn(alertService, 'error');

            wizardUnitComponentFixture.detectChanges();
            tick();

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);
            tick();

            expect(errorSpy).toHaveBeenCalled();
            expect(wizardUnitComponent.isUploadingPdfs()).toBeFalse();
        }));

        it('should not process if no files are provided', fakeAsync(() => {
            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile');

            wizardUnitComponentFixture.detectChanges();
            tick();

            wizardUnitComponent.onPdfFilesDropped([]);
            tick();

            expect(createSpy).not.toHaveBeenCalled();
        }));

        it('should not process if lecture has no id', fakeAsync(() => {
            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile');

            wizardUnitComponent.lecture = new Lecture();
            wizardUnitComponent.lecture.id = undefined;
            wizardUnitComponentFixture.detectChanges();
            tick();

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);
            tick();

            expect(createSpy).not.toHaveBeenCalled();
        }));

        it('should set isUploadingPdfs during upload', fakeAsync(() => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            wizardUnitComponentFixture.detectChanges();
            tick();

            expect(wizardUnitComponent.isUploadingPdfs()).toBeFalse();

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            wizardUnitComponent.onPdfFilesDropped([pdfFile]);

            // After completion
            tick();
            expect(wizardUnitComponent.isUploadingPdfs()).toBeFalse();
        }));
    });
});
