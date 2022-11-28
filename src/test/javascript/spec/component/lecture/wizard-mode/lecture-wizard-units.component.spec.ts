import { Component, EventEmitter, Input, Output } from '@angular/core';
import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of, throwError } from 'rxjs';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LectureUpdateWizardUnitsComponent } from 'app/lecture/wizard-mode/lecture-wizard-units.component';
import { Lecture } from 'app/entities/lecture.model';
import { TextUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/textUnit.service';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { TextUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/text-unit-form/text-unit-form.component';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { OnlineUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { objectToJsonBlob } from 'app/utils/blob-util';

@Component({ selector: 'jhi-video-unit-form', template: '' })
class VideoUnitFormStubComponent {
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<VideoUnitFormData> = new EventEmitter<VideoUnitFormData>();
}

@Component({ selector: 'jhi-unit-creation-card', template: '' })
class UnitCreationCardStubComponent {
    @Input() emitEvents = true;
    @Output() onUnitCreationCardClicked: EventEmitter<LectureUnitType> = new EventEmitter<LectureUnitType>();
}

describe('LectureWizardUnitComponent', () => {
    let wizardUnitComponentFixture: ComponentFixture<LectureUpdateWizardUnitsComponent>;
    let wizardUnitComponent: LectureUpdateWizardUnitsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [VideoUnitFormStubComponent, UnitCreationCardStubComponent, LectureUpdateWizardUnitsComponent],
            providers: [
                MockProvider(VideoUnitService),
                MockProvider(AlertService),
                MockProvider(TextUnitService),
                MockProvider(OnlineUnitService),
                MockProvider(AttachmentUnitService),
                MockProvider(LectureUnitManagementComponent),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                wizardUnitComponentFixture = TestBed.createComponent(LectureUpdateWizardUnitsComponent);
                wizardUnitComponent = wizardUnitComponentFixture.componentInstance;
                wizardUnitComponent.lecture = new Lecture();
                wizardUnitComponent.lecture.id = 1;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        wizardUnitComponentFixture.detectChanges();
        expect(wizardUnitComponent).not.toBeNull();
    });

    it('should open video form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardStubComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardStubComponent)).componentInstance;
        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.VIDEO);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isVideoUnitFormOpen).toBeTrue();
        });
    }));

    it('should open online form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardStubComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardStubComponent)).componentInstance;
        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.ONLINE);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isOnlineUnitFormOpen).toBeTrue();
        });
    }));

    it('should open attachment form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardStubComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardStubComponent)).componentInstance;
        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.ATTACHMENT);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isAttachmentUnitFormOpen).toBeTrue();
        });
    }));

    it('should open text form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardStubComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardStubComponent)).componentInstance;
        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.TEXT);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isTextUnitFormOpen).toBeTrue();
        });
    }));

    it('should open exercise form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        const unitCreationCard: UnitCreationCardStubComponent = wizardUnitComponentFixture.debugElement.query(By.directive(UnitCreationCardStubComponent)).componentInstance;
        unitCreationCard.onUnitCreationCardClicked.emit(LectureUnitType.EXERCISE);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isExerciseUnitFormOpen).toBeTrue();
        });
    }));

    it('should close all forms when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.onCloseLectureUnitForms();

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isOnlineUnitFormOpen).toBeFalse();
            expect(wizardUnitComponent.isTextUnitFormOpen).toBeFalse();
            expect(wizardUnitComponent.isExerciseUnitFormOpen).toBeFalse();
            expect(wizardUnitComponent.isAttachmentUnitFormOpen).toBeFalse();
            expect(wizardUnitComponent.isVideoUnitFormOpen).toBeFalse();
        });
    }));

    it('should send POST request upon video form submission and update units', fakeAsync(() => {
        const videoUnitService = TestBed.inject(VideoUnitService);

        const formData: VideoUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            description: 'Lorem Ipsum',
            source: 'https://www.youtube.com/embed/8iU8LPEa4o0',
        };

        const response: HttpResponse<VideoUnit> = new HttpResponse({
            body: new VideoUnit(),
            status: 201,
        });

        const createStub = jest.spyOn(videoUnitService, 'create').mockReturnValue(of(response));

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);

        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isVideoUnitFormOpen = true;

        wizardUnitComponent.createEditVideoUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            const videoUnitCallArgument: VideoUnit = createStub.mock.calls[0][0];
            const lectureIdCallArgument: number = createStub.mock.calls[0][1];

            expect(videoUnitCallArgument.name).toEqual(formData.name);
            expect(videoUnitCallArgument.description).toEqual(formData.description);
            expect(videoUnitCallArgument.releaseDate).toEqual(formData.releaseDate);
            expect(videoUnitCallArgument.source).toEqual(formData.source);
            expect(lectureIdCallArgument).toBe(1);

            expect(createStub).toHaveBeenCalledOnce();
            expect(updateSpy).toHaveBeenCalledOnce();

            updateSpy.mockRestore();
        });
    }));

    it('should not send POST request upon empty video form submission', fakeAsync(() => {
        const videoUnitService = TestBed.inject(VideoUnitService);

        const formData: VideoUnitFormData = {};

        const createStub = jest.spyOn(videoUnitService, 'create');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isVideoUnitFormOpen = true;

        wizardUnitComponent.createEditVideoUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledTimes(0);
        });
    }));

    it('should send POST request upon text form submission and update units', fakeAsync(() => {
        const textUnitService = TestBed.inject(TextUnitService);

        const formData: TextUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            content: 'Lorem Ipsum',
        };

        const persistedTextUnit: TextUnit = new TextUnit();
        persistedTextUnit.id = 1;
        persistedTextUnit.name = formData.name;
        persistedTextUnit.releaseDate = formData.releaseDate;
        persistedTextUnit.content = formData.content;

        const response: HttpResponse<TextUnit> = new HttpResponse({
            body: persistedTextUnit,
            status: 200,
        });

        const createStub = jest.spyOn(textUnitService, 'create').mockReturnValue(of(response));

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);

        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isTextUnitFormOpen = true;

        wizardUnitComponent.createEditTextUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledOnce();
            expect(updateSpy).toHaveBeenCalledOnce();

            updateSpy.mockRestore();
        });
    }));

    it('should not send POST request upon empty text form submission', fakeAsync(() => {
        const textUnitService = TestBed.inject(TextUnitService);

        const formData: TextUnitFormData = {};

        const createStub = jest.spyOn(textUnitService, 'create');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isTextUnitFormOpen = true;

        wizardUnitComponent.createEditTextUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledTimes(0);
        });
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

        wizardUnitComponent.isTextUnitFormOpen = true;

        wizardUnitComponent.createEditTextUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledOnce();
        });
    }));

    it('should show alert upon unsuccessful video form submission', fakeAsync(() => {
        const videoUnitService = TestBed.inject(VideoUnitService);
        const alertService = TestBed.inject(AlertService);

        const formData: VideoUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            description: 'Lorem Ipsum',
            source: 'https://www.youtube.com/embed/8iU8LPEa4o0',
        };

        const createStub = jest.spyOn(videoUnitService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertStub = jest.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isVideoUnitFormOpen = true;

        wizardUnitComponent.createEditVideoUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledOnce();
        });
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

        wizardUnitComponent.isOnlineUnitFormOpen = true;

        wizardUnitComponent.createEditOnlineUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledOnce();
        });
    }));

    it('should send POST request upon online form submission and update units', fakeAsync(() => {
        const onlineUnitService = TestBed.inject(OnlineUnitService);

        const formDate: OnlineUnitFormData = {
            name: 'Test',
            releaseDate: dayjs().year(2010).month(3).date(5),
            description: 'Lorem Ipsum',
            source: 'https://www.example.com',
        };

        const response: HttpResponse<OnlineUnit> = new HttpResponse({
            body: new OnlineUnit(),
            status: 201,
        });

        const createStub = jest.spyOn(onlineUnitService, 'create').mockReturnValue(of(response));

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);

        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isOnlineUnitFormOpen = true;

        wizardUnitComponent.createEditOnlineUnit(formDate);

        wizardUnitComponentFixture.whenStable().then(() => {
            const onlineUnitCallArgument: OnlineUnit = createStub.mock.calls[0][0];
            const lectureIdCallArgument: number = createStub.mock.calls[0][1];

            expect(onlineUnitCallArgument.name).toEqual(formDate.name);
            expect(onlineUnitCallArgument.description).toEqual(formDate.description);
            expect(onlineUnitCallArgument.releaseDate).toEqual(formDate.releaseDate);
            expect(onlineUnitCallArgument.source).toEqual(formDate.source);
            expect(lectureIdCallArgument).toBe(1);

            expect(createStub).toHaveBeenCalledOnce();
            expect(updateSpy).toHaveBeenCalledOnce();

            updateSpy.mockRestore();
        });
    }));

    it('should not send POST request upon empty online form submission', fakeAsync(() => {
        const onlineUnitService = TestBed.inject(OnlineUnitService);

        const formData: OnlineUnitFormData = {};

        const createStub = jest.spyOn(onlineUnitService, 'create');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isOnlineUnitFormOpen = true;

        wizardUnitComponent.createEditOnlineUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledTimes(0);
        });
    }));

    it('should send POST request upon attachment form submission and update units', fakeAsync(() => {
        const attachmentUnitService = TestBed.inject(AttachmentUnitService);

        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        const attachmentUnitFormData: AttachmentUnitFormData = {
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
        attachment.releaseDate = attachmentUnitFormData.formProperties.releaseDate;
        attachment.name = attachmentUnitFormData.formProperties.name;
        attachment.link = examplePath;

        const attachmentUnit = new AttachmentUnit();
        attachmentUnit.description = attachmentUnitFormData.formProperties.description;
        attachmentUnit.attachment = attachment;

        const formData = new FormData();
        formData.append('file', fakeFile, attachmentUnitFormData.fileProperties.fileName);
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentUnit', objectToJsonBlob(attachmentUnit));

        const attachmentUnitResponse: HttpResponse<AttachmentUnit> = new HttpResponse({
            body: attachmentUnit,
            status: 201,
        });
        const createAttachmentUnitStub = jest.spyOn(attachmentUnitService, 'create').mockReturnValue(of(attachmentUnitResponse));

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);

        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isAttachmentUnitFormOpen = true;

        wizardUnitComponent.createEditAttachmentUnit(attachmentUnitFormData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createAttachmentUnitStub).toHaveBeenCalledWith(formData, 1);
            expect(updateSpy).toHaveBeenCalledOnce();

            updateSpy.mockRestore();
        });
    }));

    it('should send POST request upon attachment form submission and update units when editing lecture', fakeAsync(() => {
        const attachmentUnitService = TestBed.inject(AttachmentUnitService);

        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        const attachmentUnitFormData: AttachmentUnitFormData = {
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
        attachment.releaseDate = attachmentUnitFormData.formProperties.releaseDate;
        attachment.name = attachmentUnitFormData.formProperties.name;
        attachment.link = examplePath;

        const attachmentUnit = new AttachmentUnit();
        attachmentUnit.description = attachmentUnitFormData.formProperties.description;
        attachmentUnit.attachment = attachment;

        const formData = new FormData();
        formData.append('file', fakeFile, attachmentUnitFormData.fileProperties.fileName);
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentUnit', objectToJsonBlob(attachmentUnit));

        const attachmentUnitResponse: HttpResponse<AttachmentUnit> = new HttpResponse({
            body: attachmentUnit,
            status: 201,
        });
        const createAttachmentUnitStub = jest.spyOn(attachmentUnitService, 'update').mockReturnValue(of(attachmentUnitResponse));

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);

        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.isEditingLectureUnit = true;
        wizardUnitComponent.currentlyProcessedAttachmentUnit = new AttachmentUnit();
        wizardUnitComponent.currentlyProcessedAttachmentUnit.attachment = new Attachment();
        wizardUnitComponent.isAttachmentUnitFormOpen = true;

        wizardUnitComponent.createEditAttachmentUnit(attachmentUnitFormData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createAttachmentUnitStub).toHaveBeenCalledOnce();
            expect(updateSpy).toHaveBeenCalledOnce();

            updateSpy.mockRestore();
        });
    }));

    it('should show alert upon unsuccessful attachment form submission', fakeAsync(() => {
        const attachmentUnitService = TestBed.inject(AttachmentUnitService);
        const alertService = TestBed.inject(AlertService);

        const fakeFile = new File([''], 'Test-File.pdf', { type: 'application/pdf' });

        const attachmentUnitFormData: AttachmentUnitFormData = {
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
        attachment.releaseDate = attachmentUnitFormData.formProperties.releaseDate;
        attachment.name = attachmentUnitFormData.formProperties.name;
        attachment.link = examplePath;

        const attachmentUnit = new AttachmentUnit();
        attachmentUnit.description = attachmentUnitFormData.formProperties.description;
        attachmentUnit.attachment = attachment;

        const formData = new FormData();
        formData.append('file', fakeFile, attachmentUnitFormData.fileProperties.fileName);
        formData.append('attachment', objectToJsonBlob(attachment));
        formData.append('attachmentUnit', objectToJsonBlob(attachmentUnit));

        const createAttachmentUnitStub = jest.spyOn(attachmentUnitService, 'create').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertStub = jest.spyOn(alertService, 'error');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isAttachmentUnitFormOpen = true;

        wizardUnitComponent.createEditAttachmentUnit(attachmentUnitFormData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createAttachmentUnitStub).toHaveBeenCalledOnce();
            expect(alertStub).toHaveBeenCalledOnce();
        });
    }));

    it('should not send POST request upon empty attachment form submission', fakeAsync(() => {
        const attachmentUnitService = TestBed.inject(AttachmentUnitService);

        const formData: AttachmentUnitFormData = {
            formProperties: {},
            fileProperties: {},
        };

        const createStub = jest.spyOn(attachmentUnitService, 'create');

        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.isAttachmentUnitFormOpen = true;

        wizardUnitComponent.createEditAttachmentUnit(formData);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(createStub).toHaveBeenCalledTimes(0);
        });
    }));

    it('should update units upon exercise unit creation', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.unitManagementComponent = TestBed.inject(LectureUnitManagementComponent);

        const updateSpy = jest.spyOn(wizardUnitComponent.unitManagementComponent, 'loadData');

        wizardUnitComponent.onExerciseUnitCreated();

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(updateSpy).toHaveBeenCalledOnce();

            updateSpy.mockRestore();
        });
    }));

    it('should be in edit mode when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();
        wizardUnitComponent.startEditLectureUnit(new VideoUnit());

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isEditingLectureUnit).toBeTrue();
        });
    }));

    it('should open edit video form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.startEditLectureUnit(new VideoUnit());

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isVideoUnitFormOpen).toBeTrue();
        });
    }));

    it('should open edit online form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.startEditLectureUnit(new OnlineUnit());

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isOnlineUnitFormOpen).toBeTrue();
        });
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

        const attachmentUnit = new AttachmentUnit();
        attachmentUnit.attachment = attachment;

        wizardUnitComponent.startEditLectureUnit(attachmentUnit);

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isAttachmentUnitFormOpen).toBeTrue();
        });
    }));

    it('should open edit text form when clicked', fakeAsync(() => {
        wizardUnitComponentFixture.detectChanges();
        tick();

        wizardUnitComponent.startEditLectureUnit(new TextUnit());

        wizardUnitComponentFixture.whenStable().then(() => {
            expect(wizardUnitComponent.isTextUnitFormOpen).toBeTrue();
        });
    }));

    it('should open exercise form upon init when requested', fakeAsync(() => {
        const route = TestBed.inject(ActivatedRoute);
        route.queryParams = of({ shouldOpenCreateExercise: true });

        wizardUnitComponentFixture.detectChanges();

        expect(wizardUnitComponent).not.toBeNull();
        expect(wizardUnitComponent.isExerciseUnitFormOpen).toBeTrue();
    }));
});
