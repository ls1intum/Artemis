import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUnitManagementComponent } from 'app/lecture/manage/lecture-units/management/lecture-unit-management.component';
import { AttachmentVideoUnit, TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Component, Input, input } from '@angular/core';
import { ExerciseUnitComponent } from 'app/lecture/overview/course-lectures/exercise-unit/exercise-unit.component';
import { AttachmentVideoUnitComponent } from 'app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component';
import { TextUnitComponent } from 'app/lecture/overview/course-lectures/text-unit/text-unit.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { LectureUnitService, ProcessingPhase } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { throwError } from 'rxjs';
import { PdfDropZoneComponent } from '../../pdf-drop-zone/pdf-drop-zone.component';

@Component({ selector: 'jhi-competencies-popover', template: '' })
class CompetenciesPopoverStubComponent {
    @Input() courseId!: number;
    @Input() competencyLinks: CompetencyLectureUnitLink[] = [];
    @Input() navigateTo: 'competencyManagement' | 'courseStatistics' = 'courseStatistics';
}

@Component({ selector: 'jhi-pdf-drop-zone', template: '' })
class PdfDropZoneStubComponent {
    disabled = input<boolean>(false);
}

describe('LectureUnitManagementComponent', () => {
    let lectureUnitManagementComponent: LectureUnitManagementComponent;
    let lectureUnitManagementComponentFixture: ComponentFixture<LectureUnitManagementComponent>;
    let lectureService: LectureService;
    let lectureUnitService: LectureUnitService;
    let lectureTranscriptionService: LectureTranscriptionService;
    let alertService: AlertService;
    let attachmentVideoUnitService: AttachmentVideoUnitService;
    let findLectureWithDetailsSpy: jest.SpyInstance;
    let deleteLectureUnitSpy: jest.SpyInstance;
    let updateOrderSpy: jest.SpyInstance;

    let attachmentVideoUnit: AttachmentVideoUnit;
    let exerciseUnit: ExerciseUnit;
    let textUnit: TextUnit;
    let lecture: Lecture;
    let course: Course;

    const lectureId = 1;
    const route = { parent: { snapshot: { paramMap: convertToParamMap({ lectureId }) } } } as any as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip), FaIconComponent, LectureUnitManagementComponent],
            declarations: [
                MockComponent(UnitCreationCardComponent),
                CompetenciesPopoverStubComponent,
                PdfDropZoneStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(ExerciseUnitComponent),
                MockComponent(AttachmentVideoUnitComponent),
                MockComponent(TextUnitComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(LectureUnitService),
                MockProvider(LectureService),
                MockProvider(AlertService),
                MockProvider(LectureTranscriptionService),
                MockProvider(AttachmentVideoUnitService),
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
            ],
        })
            .overrideComponent(LectureUnitManagementComponent, {
                remove: { imports: [PdfDropZoneComponent] },
                add: { imports: [PdfDropZoneStubComponent] },
            })
            .compileComponents();
        lectureUnitManagementComponentFixture = TestBed.createComponent(LectureUnitManagementComponent);
        lectureUnitManagementComponent = lectureUnitManagementComponentFixture.componentInstance;
        lectureService = TestBed.inject(LectureService);
        lectureUnitService = TestBed.inject(LectureUnitService);
        lectureTranscriptionService = TestBed.inject(LectureTranscriptionService);
        alertService = TestBed.inject(AlertService);
        attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        findLectureWithDetailsSpy = jest.spyOn(lectureService, 'findWithDetails');
        deleteLectureUnitSpy = jest.spyOn(lectureUnitService, 'delete');
        updateOrderSpy = jest.spyOn(lectureUnitService, 'updateOrder');
        textUnit = new TextUnit();
        textUnit.id = 0;
        exerciseUnit = new ExerciseUnit();
        exerciseUnit.id = 2;
        attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.id = 3;
        course = new Course();
        course.id = 99;
        lecture = new Lecture();
        lecture.id = 1;
        lecture.course = course;
        lecture.lectureUnits = [textUnit, exerciseUnit, attachmentVideoUnit];
        const returnValue = of(new HttpResponse({ body: lecture, status: 200 }));
        findLectureWithDetailsSpy.mockReturnValue(returnValue);
        updateOrderSpy.mockReturnValue(returnValue);
        deleteLectureUnitSpy.mockReturnValue(of(new HttpResponse({ body: attachmentVideoUnit, status: 200 })));
        jest.spyOn(lectureTranscriptionService, 'getTranscriptionStatus').mockReturnValue(of(TranscriptionStatus.COMPLETED));
        jest.spyOn(lectureUnitService, 'getProcessingStatus').mockReturnValue(of({ lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.DONE, retryCount: 0 }));
        lectureUnitManagementComponentFixture.detectChanges();
    });

    it('should reorder', () => {
        const originalOrder = [...lecture.lectureUnits!];
        lectureUnitManagementComponentFixture.detectChanges();
        expect(lectureUnitManagementComponent.lectureUnits[0].id).toEqual(originalOrder[0].id);
        lectureUnitManagementComponent.drop({ previousIndex: 0, currentIndex: 1 } as CdkDragDrop<LectureUnit[]>);
        expect(lectureUnitManagementComponent.lectureUnits[0].id).toEqual(originalOrder[1].id);
        expect(lectureUnitManagementComponent.lectureUnits[1].id).toEqual(originalOrder[0].id);
    });

    it('should emit edit button event', () => {
        const editButtonClickedSpy = jest.spyOn(lectureUnitManagementComponent, 'onEditButtonClicked');
        lectureUnitManagementComponent.emitEditEvents = true;
        lectureUnitManagementComponentFixture.changeDetectorRef.detectChanges();
        const buttons = lectureUnitManagementComponentFixture.debugElement.queryAll(By.css(`.edit`));
        for (const button of buttons) {
            button.nativeElement.click();
        }
        lectureUnitManagementComponentFixture.changeDetectorRef.detectChanges();
        expect(editButtonClickedSpy).toHaveBeenCalledTimes(buttons.length);
    });

    it('should show loadData on delete', () => {
        const loadDataSpy = jest.spyOn(lectureUnitManagementComponent, 'loadData');
        lectureUnitManagementComponent.deleteLectureUnit(1);
        expect(loadDataSpy).toHaveBeenCalledOnce();
    });

    it('should give the correct delete question translation key', () => {
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new AttachmentVideoUnit())).toBe('artemisApp.attachmentVideoUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new ExerciseUnit())).toBe('artemisApp.exerciseUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new TextUnit())).toBe('artemisApp.textUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new OnlineUnit())).toBe('artemisApp.onlineUnit.delete.question');
    });

    it('should return default question translation key for unhandled types', () => {
        const mockUnit = {
            type: null,
        };

        expect(lectureUnitManagementComponent.getDeleteQuestionKey(mockUnit as unknown as LectureUnit)).toBe('');
    });

    it('should give the correct confirmation text translation key', () => {
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new AttachmentVideoUnit())).toBe('artemisApp.attachmentVideoUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new ExerciseUnit())).toBe('artemisApp.exerciseUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new TextUnit())).toBe('artemisApp.textUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new OnlineUnit())).toBe('artemisApp.onlineUnit.delete.typeNameToConfirm');
    });

    it('should return default confirmation text translation key for unhandled types', () => {
        const mockUnit = {
            type: null,
        };

        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(mockUnit as unknown as LectureUnit)).toBe('');
    });

    it('should give the correct action type', () => {
        expect(lectureUnitManagementComponent.getActionType(new AttachmentVideoUnit())).toEqual(ActionType.Delete);
        expect(lectureUnitManagementComponent.getActionType(new ExerciseUnit())).toEqual(ActionType.Unlink);
        expect(lectureUnitManagementComponent.getActionType(new TextUnit())).toEqual(ActionType.Delete);
        expect(lectureUnitManagementComponent.getActionType(new OnlineUnit())).toEqual(ActionType.Delete);
    });

    describe('isViewButtonAvailable', () => {
        it('should return true for an attachment video unit with a PDF link', () => {
            const lectureUnit = {
                type: LectureUnitType.ATTACHMENT_VIDEO,
                attachment: { link: 'file.pdf' },
            } as LectureUnit;
            expect(lectureUnitManagementComponent.isViewButtonAvailable(lectureUnit)).toBeTrue();
        });

        it('should return false for file extension different than .pdf', () => {
            const lectureUnit = {
                type: LectureUnitType.ATTACHMENT_VIDEO,
                attachment: { link: 'file.txt' },
            };
            expect(lectureUnitManagementComponent.isViewButtonAvailable(lectureUnit)).toBeFalse();
        });

        it('should return false for a text unit', () => {
            const lectureUnit = {
                type: LectureUnitType.TEXT,
            };
            expect(lectureUnitManagementComponent.isViewButtonAvailable(lectureUnit)).toBeFalse();
        });
    });

    describe('Transcription', () => {
        it('should load transcription status for attachment video units', () => {
            const statusSpy = jest.spyOn(lectureTranscriptionService, 'getTranscriptionStatus').mockReturnValue(of(TranscriptionStatus.COMPLETED));

            lectureUnitManagementComponent.loadData();

            expect(statusSpy).toHaveBeenCalledWith(attachmentVideoUnit.id);
            expect(lectureUnitManagementComponent.transcriptionStatus[attachmentVideoUnit.id!]).toBe(TranscriptionStatus.COMPLETED);
        });

        it('should correctly identify transcription states', () => {
            lectureUnitManagementComponent.transcriptionStatus[attachmentVideoUnit.id!] = TranscriptionStatus.COMPLETED;
            expect(lectureUnitManagementComponent.hasTranscription(attachmentVideoUnit)).toBeTrue();

            lectureUnitManagementComponent.transcriptionStatus[attachmentVideoUnit.id!] = TranscriptionStatus.PENDING;
            expect(lectureUnitManagementComponent.isTranscriptionPending(attachmentVideoUnit)).toBeTrue();

            lectureUnitManagementComponent.transcriptionStatus[attachmentVideoUnit.id!] = TranscriptionStatus.FAILED;
            expect(lectureUnitManagementComponent.isTranscriptionFailed(attachmentVideoUnit)).toBeTrue();
        });

        it('should return true for hasTranscriptionBadge when transcription is pending', () => {
            lectureUnitManagementComponent.transcriptionStatus[attachmentVideoUnit.id!] = TranscriptionStatus.PENDING;
            expect(lectureUnitManagementComponent.hasTranscriptionBadge(attachmentVideoUnit)).toBeTrue();
        });

        it('should return true for hasTranscriptionBadge when transcription is completed', () => {
            lectureUnitManagementComponent.transcriptionStatus[attachmentVideoUnit.id!] = TranscriptionStatus.COMPLETED;
            expect(lectureUnitManagementComponent.hasTranscriptionBadge(attachmentVideoUnit)).toBeTrue();
        });

        it('should return true for hasTranscriptionBadge when transcription failed', () => {
            lectureUnitManagementComponent.transcriptionStatus[attachmentVideoUnit.id!] = TranscriptionStatus.FAILED;
            expect(lectureUnitManagementComponent.hasTranscriptionBadge(attachmentVideoUnit)).toBeTrue();
        });
    });

    describe('Processing Status', () => {
        it('should load processing status for attachment video units', () => {
            const statusSpy = jest
                .spyOn(lectureUnitService, 'getProcessingStatus')
                .mockReturnValue(of({ lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.DONE, retryCount: 0 }));

            lectureUnitManagementComponent.loadData();

            expect(statusSpy).toHaveBeenCalledWith(lectureId, attachmentVideoUnit.id);
            expect(lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!]?.phase).toBe(ProcessingPhase.DONE);
        });

        it('should correctly identify processing states', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.IDLE, retryCount: 0 };
            expect(lectureUnitManagementComponent.isProcessingIdle(attachmentVideoUnit)).toBeTrue();

            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.TRANSCRIBING, retryCount: 0 };
            expect(lectureUnitManagementComponent.isProcessingTranscribing(attachmentVideoUnit)).toBeTrue();
            expect(lectureUnitManagementComponent.isProcessingInProgress(attachmentVideoUnit)).toBeTrue();

            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.INGESTING, retryCount: 0 };
            expect(lectureUnitManagementComponent.isProcessingIngesting(attachmentVideoUnit)).toBeTrue();
            expect(lectureUnitManagementComponent.isProcessingInProgress(attachmentVideoUnit)).toBeTrue();

            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.DONE, retryCount: 0 };
            expect(lectureUnitManagementComponent.isProcessingDone(attachmentVideoUnit)).toBeTrue();

            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.FAILED, retryCount: 0 };
            expect(lectureUnitManagementComponent.isProcessingFailed(attachmentVideoUnit)).toBeTrue();
        });

        it('should return true for hasProcessingBadge when processing is in progress', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.TRANSCRIBING, retryCount: 0 };
            expect(lectureUnitManagementComponent.hasProcessingBadge(attachmentVideoUnit)).toBeTrue();
        });

        it('should return true for hasProcessingBadge when processing is done', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.DONE, retryCount: 0 };
            expect(lectureUnitManagementComponent.hasProcessingBadge(attachmentVideoUnit)).toBeTrue();
        });

        it('should return true for hasProcessingBadge when processing failed', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.FAILED, retryCount: 0 };
            expect(lectureUnitManagementComponent.hasProcessingBadge(attachmentVideoUnit)).toBeTrue();
        });

        it('should return error key from processing status', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = {
                lectureUnitId: attachmentVideoUnit.id!,
                phase: ProcessingPhase.FAILED,
                retryCount: 3,
                errorKey: 'artemisApp.lectureUnit.processing.error.transcriptionFailed',
            };
            expect(lectureUnitManagementComponent.getProcessingErrorKey(attachmentVideoUnit)).toBe('artemisApp.lectureUnit.processing.error.transcriptionFailed');
        });
    });

    describe('isAwaitingProcessing', () => {
        it('should return true when status is IDLE and course is active', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.IDLE, retryCount: 0 };
            lectureUnitManagementComponent.lecture.course!.startDate = undefined;
            lectureUnitManagementComponent.lecture.course!.endDate = undefined;
            expect(lectureUnitManagementComponent.isAwaitingProcessing(attachmentVideoUnit)).toBeTrue();
        });

        it('should return true when status is undefined and course is active', () => {
            delete lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!];
            lectureUnitManagementComponent.lecture.course!.startDate = undefined;
            lectureUnitManagementComponent.lecture.course!.endDate = undefined;
            expect(lectureUnitManagementComponent.isAwaitingProcessing(attachmentVideoUnit)).toBeTrue();
        });

        it('should return false when processing is in progress', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.TRANSCRIBING, retryCount: 0 };
            expect(lectureUnitManagementComponent.isAwaitingProcessing(attachmentVideoUnit)).toBeFalse();
        });

        it('should return false when processing is done', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.DONE, retryCount: 0 };
            expect(lectureUnitManagementComponent.isAwaitingProcessing(attachmentVideoUnit)).toBeFalse();
        });
    });

    describe('isCourseActive', () => {
        it('should return true when course has no date restrictions', () => {
            lectureUnitManagementComponent.lecture.course!.startDate = undefined;
            lectureUnitManagementComponent.lecture.course!.endDate = undefined;
            expect(lectureUnitManagementComponent.isCourseActive()).toBeTrue();
        });

        it('should return false when no lecture is set', () => {
            lectureUnitManagementComponent.lecture = undefined as any;
            expect(lectureUnitManagementComponent.isCourseActive()).toBeFalse();
        });

        it('should return false when no course is set', () => {
            lectureUnitManagementComponent.lecture.course = undefined;
            expect(lectureUnitManagementComponent.isCourseActive()).toBeFalse();
        });
    });

    describe('retryProcessing', () => {
        it('should call retryProcessing on lectureUnitService and show success message', () => {
            const retryProcessingSpy = jest.spyOn(lectureUnitService, 'retryProcessing').mockReturnValue(of(new HttpResponse<void>({ status: 200 })));
            const alertSpy = jest.spyOn(alertService, 'success');
            lectureUnitManagementComponent.lecture = lecture;
            lectureUnitManagementComponent.lectureId = 5; // non-zero value

            lectureUnitManagementComponent.retryProcessing(attachmentVideoUnit);

            expect(retryProcessingSpy).toHaveBeenCalledWith(5, attachmentVideoUnit.id);
            expect(lectureUnitManagementComponent.isRetryingProcessing[attachmentVideoUnit.id!]).toBeTrue();
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.lectureUnit.processingRetryStarted');
        });

        it('should not call retryProcessing if lectureId is missing', () => {
            const retryProcessingSpy = jest.spyOn(lectureUnitService, 'retryProcessing');
            lectureUnitManagementComponent.lectureId = undefined;

            lectureUnitManagementComponent.retryProcessing(attachmentVideoUnit);

            expect(retryProcessingSpy).not.toHaveBeenCalled();
        });

        it('should not call retryProcessing if lectureUnit.id is missing', () => {
            const retryProcessingSpy = jest.spyOn(lectureUnitService, 'retryProcessing');
            lectureUnitManagementComponent.lectureId = 5;
            const unitWithoutId = new AttachmentVideoUnit();

            lectureUnitManagementComponent.retryProcessing(unitWithoutId);

            expect(retryProcessingSpy).not.toHaveBeenCalled();
        });

        it('should handle error when retryProcessing fails', () => {
            const error = new HttpErrorResponse({ status: 500, statusText: 'Server Error' });
            jest.spyOn(lectureUnitService, 'retryProcessing').mockReturnValue(throwError(() => error));
            lectureUnitManagementComponent.lecture = lecture;
            lectureUnitManagementComponent.lectureId = 5;
            lectureUnitManagementComponent.isRetryingProcessing[attachmentVideoUnit.id!] = true;

            lectureUnitManagementComponent.retryProcessing(attachmentVideoUnit);

            expect(lectureUnitManagementComponent.isRetryingProcessing[attachmentVideoUnit.id!]).toBeFalse();
        });
    });

    describe('PDF drop zone', () => {
        it('should create attachment units from dropped PDF files', () => {
            const alertService = TestBed.inject(AlertService);

            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 42;
            createdUnit.name = 'Test File';

            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));
            const successSpy = jest.spyOn(alertService, 'success');

            lectureUnitManagementComponent.lecture = lecture;
            lectureUnitManagementComponent.lectureId = lecture.id;

            const pdfFile = new File(['content'], 'Test_File.pdf', { type: 'application/pdf' });
            lectureUnitManagementComponent.onPdfFilesDropped([pdfFile]);

            expect(createSpy).toHaveBeenCalledOnce();
            expect(successSpy).toHaveBeenCalledWith('artemisApp.lecture.pdfUpload.success');
        });

        it('should navigate to edit page after upload', () => {
            const router = TestBed.inject(Router);
            const navigateSpy = jest.spyOn(router, 'navigate');

            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 99;

            jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            lectureUnitManagementComponent.lecture = lecture;
            lectureUnitManagementComponent.lectureId = lecture.id;

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            lectureUnitManagementComponent.onPdfFilesDropped([pdfFile]);

            expect(navigateSpy).toHaveBeenCalledWith([
                '/course-management',
                lecture.course!.id,
                'lectures',
                lecture.id,
                'unit-management',
                'attachment-video-units',
                createdUnit.id,
                'edit',
            ]);
        });

        it('should handle multiple PDF files sequentially', () => {
            const alertService = TestBed.inject(AlertService);

            let callCount = 0;
            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockImplementation(() => {
                callCount++;
                const unit = new AttachmentVideoUnit();
                unit.id = callCount;
                return of(new HttpResponse({ body: unit, status: 201 }));
            });
            const successSpy = jest.spyOn(alertService, 'success');

            lectureUnitManagementComponent.lecture = lecture;
            lectureUnitManagementComponent.lectureId = lecture.id;

            const pdfFiles = [
                new File(['content1'], 'file1.pdf', { type: 'application/pdf' }),
                new File(['content2'], 'file2.pdf', { type: 'application/pdf' }),
                new File(['content3'], 'file3.pdf', { type: 'application/pdf' }),
            ];

            lectureUnitManagementComponent.onPdfFilesDropped(pdfFiles);

            expect(createSpy).toHaveBeenCalledTimes(3);
            expect(successSpy).toHaveBeenCalledOnce();
        });

        it('should show error alert on upload failure', () => {
            const alertService = TestBed.inject(AlertService);

            // Use status 400 as status 500 intentionally doesn't show an alert (see onError in global.utils.ts)
            jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(throwError(() => ({ status: 400 })));
            const errorSpy = jest.spyOn(alertService, 'error');

            lectureUnitManagementComponent.lecture = lecture;
            lectureUnitManagementComponent.lectureId = lecture.id;

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            lectureUnitManagementComponent.onPdfFilesDropped([pdfFile]);

            expect(errorSpy).toHaveBeenCalled();
            expect(lectureUnitManagementComponent.isUploadingPdfs()).toBeFalse();
        });

        it('should not process if no files are provided', () => {
            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile');

            lectureUnitManagementComponent.lecture = lecture;
            lectureUnitManagementComponent.lectureId = lecture.id;

            lectureUnitManagementComponent.onPdfFilesDropped([]);

            expect(createSpy).not.toHaveBeenCalled();
        });

        it('should not process if lectureId is undefined', () => {
            const createSpy = jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile');

            lectureUnitManagementComponent.lecture = lecture;
            lectureUnitManagementComponent.lectureId = undefined;

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            lectureUnitManagementComponent.onPdfFilesDropped([pdfFile]);

            expect(createSpy).not.toHaveBeenCalled();
        });

        it('should load data if navigation fails due to missing course', () => {
            const loadDataSpy = jest.spyOn(lectureUnitManagementComponent, 'loadData');

            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 99;

            jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            // Lecture without course
            const lectureWithoutCourse = new Lecture();
            lectureWithoutCourse.id = 1;
            lectureUnitManagementComponent.lecture = lectureWithoutCourse;
            lectureUnitManagementComponent.lectureId = lectureWithoutCourse.id;

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            lectureUnitManagementComponent.onPdfFilesDropped([pdfFile]);

            expect(loadDataSpy).toHaveBeenCalled();
        });

        it('should set isUploadingPdfs during upload', () => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            lectureUnitManagementComponent.lecture = lecture;
            lectureUnitManagementComponent.lectureId = lecture.id;

            expect(lectureUnitManagementComponent.isUploadingPdfs()).toBeFalse();

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            lectureUnitManagementComponent.onPdfFilesDropped([pdfFile]);

            // After completion (synchronous in this test due to of())
            expect(lectureUnitManagementComponent.isUploadingPdfs()).toBeFalse();
        });

        it('should navigate to last created unit when uploading multiple files', () => {
            const router = TestBed.inject(Router);
            const navigateSpy = jest.spyOn(router, 'navigate');

            let callCount = 0;
            jest.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockImplementation(() => {
                callCount++;
                const unit = new AttachmentVideoUnit();
                unit.id = callCount * 10; // 10, 20, 30
                return of(new HttpResponse({ body: unit, status: 201 }));
            });

            lectureUnitManagementComponent.lecture = lecture;
            lectureUnitManagementComponent.lectureId = lecture.id;

            const pdfFiles = [
                new File(['content1'], 'file1.pdf', { type: 'application/pdf' }),
                new File(['content2'], 'file2.pdf', { type: 'application/pdf' }),
                new File(['content3'], 'file3.pdf', { type: 'application/pdf' }),
            ];

            lectureUnitManagementComponent.onPdfFilesDropped(pdfFiles);

            // Should navigate to the last created unit (id: 30)
            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', lecture.course!.id, 'lectures', lecture.id, 'unit-management', 'attachment-video-units', 30, 'edit']);
        });
    });
});
