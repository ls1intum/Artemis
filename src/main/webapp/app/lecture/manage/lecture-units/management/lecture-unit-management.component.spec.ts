import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUnitManagementComponent } from 'app/lecture/manage/lecture-units/management/lecture-unit-management.component';
import { AttachmentVideoUnit, TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { LectureUnitCombinedStatus, LectureUnitService, ProcessingPhase } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { CompetencyLectureUnitLink } from 'app/atlas/shared/entities/competency.model';
import { faCheck, faFile, faFilePdf, faFileVideo, faLink, faScroll } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { Course } from 'app/course/shared/entities/course.model';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { throwError } from 'rxjs';
import { PdfDropZoneComponent } from '../../pdf-drop-zone/pdf-drop-zone.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

@Component({ selector: 'jhi-pdf-drop-zone', template: '' })
class PdfDropZoneStubComponent {
    disabled = input<boolean>(false);
    title = input<string>();
}

describe('LectureUnitManagementComponent', () => {
    let lectureUnitManagementComponent: LectureUnitManagementComponent;
    let lectureUnitManagementComponentFixture: ComponentFixture<LectureUnitManagementComponent>;
    let lectureService: LectureService;
    let lectureUnitService: LectureUnitService;
    let alertService: AlertService;
    let attachmentVideoUnitService: AttachmentVideoUnitService;
    let findLectureWithDetailsSpy: ReturnType<typeof vi.spyOn>;
    let deleteLectureUnitSpy: ReturnType<typeof vi.spyOn>;
    let updateOrderSpy: ReturnType<typeof vi.spyOn>;

    let attachmentVideoUnit: AttachmentVideoUnit;
    let exerciseUnit: ExerciseUnit;
    let textUnit: TextUnit;
    let lecture: Lecture;
    let course: Course;

    const lectureId = 1;
    const route = { parent: { snapshot: { paramMap: convertToParamMap({ lectureId }) } } } as any as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                FaIconComponent,
                LectureUnitManagementComponent,
                MockComponent(UnitCreationCardComponent),
                PdfDropZoneStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(LectureUnitService),
                MockProvider(LectureService),
                MockProvider(AlertService),
                MockProvider(AttachmentVideoUnitService),
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: WebsocketService, useClass: MockWebsocketService },
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
        alertService = TestBed.inject(AlertService);
        attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        findLectureWithDetailsSpy = vi.spyOn(lectureService, 'findWithDetails');
        deleteLectureUnitSpy = vi.spyOn(lectureUnitService, 'delete');
        updateOrderSpy = vi.spyOn(lectureUnitService, 'updateOrder');
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
        // Mock the bulk status endpoint
        const combinedStatuses: LectureUnitCombinedStatus[] = [
            {
                lectureUnitId: attachmentVideoUnit.id!,
                processingPhase: ProcessingPhase.DONE,
                retryCount: 0,
                transcriptionStatus: TranscriptionStatus.COMPLETED,
            },
        ];
        vi.spyOn(lectureUnitService, 'getUnitStatuses').mockReturnValue(of(combinedStatuses));
        lectureUnitManagementComponentFixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should reorder', () => {
        const originalOrder = [...lecture.lectureUnits!];
        lectureUnitManagementComponentFixture.detectChanges();
        expect(lectureUnitManagementComponent.lectureUnits()[0].id).toEqual(originalOrder[0].id);
        lectureUnitManagementComponent.drop({ previousIndex: 0, currentIndex: 1 } as CdkDragDrop<LectureUnit[]>);
        expect(lectureUnitManagementComponent.lectureUnits()[0].id).toEqual(originalOrder[1].id);
        expect(lectureUnitManagementComponent.lectureUnits()[1].id).toEqual(originalOrder[0].id);
    });

    it('should emit edit button event', () => {
        const editButtonClickedSpy = vi.spyOn(lectureUnitManagementComponent, 'onEditButtonClicked');
        lectureUnitManagementComponentFixture.componentRef.setInput('emitEditEvents', true);
        lectureUnitManagementComponentFixture.changeDetectorRef.detectChanges();
        const buttons = lectureUnitManagementComponentFixture.debugElement.queryAll(By.css(`.edit`));
        for (const button of buttons) {
            button.nativeElement.click();
        }
        lectureUnitManagementComponentFixture.changeDetectorRef.detectChanges();
        expect(editButtonClickedSpy).toHaveBeenCalledTimes(buttons.length);
    });

    it('should show loadData on delete', () => {
        const loadDataSpy = vi.spyOn(lectureUnitManagementComponent, 'loadData');
        lectureUnitManagementComponent.deleteLectureUnit(1);
        expect(loadDataSpy).toHaveBeenCalledTimes(1);
    });

    it('should handle loadData error and set isStatusLoading to false', () => {
        findLectureWithDetailsSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        lectureUnitManagementComponent.isStatusLoading.set(true);

        lectureUnitManagementComponent.loadData();

        expect(lectureUnitManagementComponent.isStatusLoading()).toBe(false);
    });

    it('should handle lecture with no lectureUnits and set isStatusLoading to false', () => {
        const lectureWithNoUnits = { ...lecture, lectureUnits: undefined };
        findLectureWithDetailsSpy.mockReturnValue(of(new HttpResponse({ body: lectureWithNoUnits, status: 200 })));
        lectureUnitManagementComponent.isStatusLoading.set(true);

        lectureUnitManagementComponent.loadData();

        expect(lectureUnitManagementComponent.isStatusLoading()).toBe(false);
        expect(lectureUnitManagementComponent.lectureUnits()).toEqual([]);
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
            expect(lectureUnitManagementComponent.isViewButtonAvailable(lectureUnit)).toBe(true);
        });

        it('should return false for file extension different than .pdf', () => {
            const lectureUnit = {
                type: LectureUnitType.ATTACHMENT_VIDEO,
                attachment: { link: 'file.txt' },
            };
            expect(lectureUnitManagementComponent.isViewButtonAvailable(lectureUnit)).toBe(false);
        });

        it('should return false for a text unit', () => {
            const lectureUnit = {
                type: LectureUnitType.TEXT,
            };
            expect(lectureUnitManagementComponent.isViewButtonAvailable(lectureUnit)).toBe(false);
        });
    });

    describe('Transcription', () => {
        it('should load transcription status from bulk endpoint for attachment video units', () => {
            const combinedStatuses: LectureUnitCombinedStatus[] = [
                {
                    lectureUnitId: attachmentVideoUnit.id!,
                    processingPhase: ProcessingPhase.DONE,
                    retryCount: 0,
                    transcriptionStatus: TranscriptionStatus.COMPLETED,
                },
            ];
            const statusSpy = vi.spyOn(lectureUnitService, 'getUnitStatuses').mockReturnValue(of(combinedStatuses));

            lectureUnitManagementComponent.loadData();

            expect(statusSpy).toHaveBeenCalledWith(lectureId);
            expect(lectureUnitManagementComponent.transcriptionStatus()[attachmentVideoUnit.id!]).toBe(TranscriptionStatus.COMPLETED);
        });

        it('should correctly identify transcription states', () => {
            lectureUnitManagementComponent.transcriptionStatus.set({ [attachmentVideoUnit.id!]: TranscriptionStatus.COMPLETED });
            expect(lectureUnitManagementComponent.hasTranscription(attachmentVideoUnit)).toBe(true);

            lectureUnitManagementComponent.transcriptionStatus.set({ [attachmentVideoUnit.id!]: TranscriptionStatus.PENDING });
            expect(lectureUnitManagementComponent.isTranscriptionPending(attachmentVideoUnit)).toBe(true);

            lectureUnitManagementComponent.transcriptionStatus.set({ [attachmentVideoUnit.id!]: TranscriptionStatus.FAILED });
            expect(lectureUnitManagementComponent.isTranscriptionFailed(attachmentVideoUnit)).toBe(true);
        });
    });

    describe('Processing Status', () => {
        it('should load processing status from bulk endpoint for attachment video units', () => {
            const combinedStatuses: LectureUnitCombinedStatus[] = [
                {
                    lectureUnitId: attachmentVideoUnit.id!,
                    processingPhase: ProcessingPhase.DONE,
                    retryCount: 0,
                    transcriptionStatus: TranscriptionStatus.COMPLETED,
                },
            ];
            const statusSpy = vi.spyOn(lectureUnitService, 'getUnitStatuses').mockReturnValue(of(combinedStatuses));

            lectureUnitManagementComponent.loadData();

            expect(statusSpy).toHaveBeenCalledWith(lectureId);
            expect(lectureUnitManagementComponent.processingStatus()[attachmentVideoUnit.id!]?.phase).toBe(ProcessingPhase.DONE);
        });

        it('should correctly identify processing states', () => {
            lectureUnitManagementComponent.processingStatus.set({
                [attachmentVideoUnit.id!]: {
                    lectureUnitId: attachmentVideoUnit.id!,
                    phase: ProcessingPhase.IDLE,
                    retryCount: 0,
                },
            });
            expect(lectureUnitManagementComponent.isProcessingTranscribing(attachmentVideoUnit)).toBe(false);
            expect(lectureUnitManagementComponent.isProcessingDone(attachmentVideoUnit)).toBe(false);

            lectureUnitManagementComponent.processingStatus.set({
                [attachmentVideoUnit.id!]: {
                    lectureUnitId: attachmentVideoUnit.id!,
                    phase: ProcessingPhase.TRANSCRIBING,
                    retryCount: 0,
                },
            });
            expect(lectureUnitManagementComponent.isProcessingTranscribing(attachmentVideoUnit)).toBe(true);

            lectureUnitManagementComponent.processingStatus.set({
                [attachmentVideoUnit.id!]: {
                    lectureUnitId: attachmentVideoUnit.id!,
                    phase: ProcessingPhase.INGESTING,
                    retryCount: 0,
                },
            });
            expect(lectureUnitManagementComponent.isProcessingIngesting(attachmentVideoUnit)).toBe(true);

            lectureUnitManagementComponent.processingStatus.set({
                [attachmentVideoUnit.id!]: {
                    lectureUnitId: attachmentVideoUnit.id!,
                    phase: ProcessingPhase.DONE,
                    retryCount: 0,
                },
            });
            expect(lectureUnitManagementComponent.isProcessingDone(attachmentVideoUnit)).toBe(true);

            lectureUnitManagementComponent.processingStatus.set({
                [attachmentVideoUnit.id!]: {
                    lectureUnitId: attachmentVideoUnit.id!,
                    phase: ProcessingPhase.FAILED,
                    retryCount: 0,
                },
            });
            expect(lectureUnitManagementComponent.isProcessingFailed(attachmentVideoUnit)).toBe(true);
        });

        it('should return error key from processing status', () => {
            lectureUnitManagementComponent.processingStatus.set({
                [attachmentVideoUnit.id!]: {
                    lectureUnitId: attachmentVideoUnit.id!,
                    phase: ProcessingPhase.FAILED,
                    retryCount: 3,
                    errorKey: 'artemisApp.attachmentVideoUnit.processing.error.youtubeLive',
                },
            });
            expect(lectureUnitManagementComponent.getProcessingErrorKey(attachmentVideoUnit)).toBe('artemisApp.attachmentVideoUnit.processing.error.youtubeLive');
        });

        it('should handle error when bulk status endpoint fails', () => {
            vi.spyOn(lectureUnitService, 'getUnitStatuses').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            lectureUnitManagementComponent.isStatusLoading.set(true);
            lectureUnitManagementComponent.loadData();

            expect(lectureUnitManagementComponent.isStatusLoading()).toBe(false);
        });
    });

    describe('isAwaitingProcessing', () => {
        it('should return true when status is IDLE and course is active', () => {
            lectureUnitManagementComponent.processingStatus.set({
                [attachmentVideoUnit.id!]: { lectureUnitId: attachmentVideoUnit.id!, phase: ProcessingPhase.IDLE, retryCount: 0 },
            });
            const currentLecture = lectureUnitManagementComponent.lecture()!;
            currentLecture.course!.startDate = undefined;
            currentLecture.course!.endDate = undefined;
            lectureUnitManagementComponent.lecture.set(currentLecture);
            expect(lectureUnitManagementComponent.isAwaitingProcessing(attachmentVideoUnit)).toBe(true);
        });

        it('should return true when status is undefined and course is active', () => {
            lectureUnitManagementComponent.processingStatus.set({});
            const currentLecture = lectureUnitManagementComponent.lecture()!;
            currentLecture.course!.startDate = undefined;
            currentLecture.course!.endDate = undefined;
            lectureUnitManagementComponent.lecture.set(currentLecture);
            expect(lectureUnitManagementComponent.isAwaitingProcessing(attachmentVideoUnit)).toBe(true);
        });

        it('should return false when processing is in progress', () => {
            lectureUnitManagementComponent.processingStatus.set({
                [attachmentVideoUnit.id!]: {
                    lectureUnitId: attachmentVideoUnit.id!,
                    phase: ProcessingPhase.TRANSCRIBING,
                    retryCount: 0,
                },
            });
            expect(lectureUnitManagementComponent.isAwaitingProcessing(attachmentVideoUnit)).toBe(false);
        });

        it('should return false when processing is done', () => {
            lectureUnitManagementComponent.processingStatus.set({
                [attachmentVideoUnit.id!]: {
                    lectureUnitId: attachmentVideoUnit.id!,
                    phase: ProcessingPhase.DONE,
                    retryCount: 0,
                },
            });
            expect(lectureUnitManagementComponent.isAwaitingProcessing(attachmentVideoUnit)).toBe(false);
        });
    });

    describe('isCourseActive', () => {
        it('should return true when course has no date restrictions', () => {
            const currentLecture = lectureUnitManagementComponent.lecture()!;
            currentLecture.course!.startDate = undefined;
            currentLecture.course!.endDate = undefined;
            lectureUnitManagementComponent.lecture.set(currentLecture);
            expect(lectureUnitManagementComponent.isCourseActive()).toBe(true);
        });

        it('should return false when no lecture is set', () => {
            lectureUnitManagementComponent.lecture.set(undefined);
            expect(lectureUnitManagementComponent.isCourseActive()).toBe(false);
        });

        it('should return false when no course is set', () => {
            const currentLecture = lectureUnitManagementComponent.lecture()!;
            currentLecture.course = undefined;
            lectureUnitManagementComponent.lecture.set(currentLecture);
            expect(lectureUnitManagementComponent.isCourseActive()).toBe(false);
        });
    });

    describe('retryProcessing', () => {
        it('should call retryProcessing on lectureUnitService and show success message', () => {
            const returnedStatus: LectureUnitCombinedStatus = {
                lectureUnitId: attachmentVideoUnit.id!,
                processingPhase: ProcessingPhase.TRANSCRIBING,
                retryCount: 1,
                transcriptionStatus: TranscriptionStatus.PENDING,
            };
            const retryProcessingSpy = vi.spyOn(lectureUnitService, 'retryProcessing').mockReturnValue(of(returnedStatus));
            const alertSpy = vi.spyOn(alertService, 'success');
            lectureUnitManagementComponent.lecture.set(lecture);
            lectureUnitManagementComponentFixture.componentRef.setInput('lectureId', 5);
            lectureUnitManagementComponent.ngOnInit(); // Re-run to update resolvedLectureId

            lectureUnitManagementComponent.retryProcessing(attachmentVideoUnit);

            expect(retryProcessingSpy).toHaveBeenCalledWith(5, attachmentVideoUnit.id);
            expect(lectureUnitManagementComponent.isRetryingProcessing()[attachmentVideoUnit.id!]).toBe(false);
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.lectureUnit.processingRetryStarted');
            // Verify status was updated from returned value
            expect(lectureUnitManagementComponent.processingStatus()[attachmentVideoUnit.id!]?.phase).toBe(ProcessingPhase.TRANSCRIBING);
            expect(lectureUnitManagementComponent.transcriptionStatus()[attachmentVideoUnit.id!]).toBe(TranscriptionStatus.PENDING);
        });

        it('should not call retryProcessing if lectureId is missing', () => {
            const retryProcessingSpy = vi.spyOn(lectureUnitService, 'retryProcessing');
            // Set resolvedLectureId to undefined via private property access
            (lectureUnitManagementComponent as any).resolvedLectureId = undefined;

            lectureUnitManagementComponent.retryProcessing(attachmentVideoUnit);

            expect(retryProcessingSpy).not.toHaveBeenCalled();
        });

        it('should not call retryProcessing if lectureUnit.id is missing', () => {
            const retryProcessingSpy = vi.spyOn(lectureUnitService, 'retryProcessing');
            lectureUnitManagementComponentFixture.componentRef.setInput('lectureId', 5);
            lectureUnitManagementComponent.ngOnInit();
            const unitWithoutId = new AttachmentVideoUnit();

            lectureUnitManagementComponent.retryProcessing(unitWithoutId);

            expect(retryProcessingSpy).not.toHaveBeenCalled();
        });

        it('should handle error when retryProcessing fails', () => {
            const error = new HttpErrorResponse({ status: 500, statusText: 'Server Error' });
            vi.spyOn(lectureUnitService, 'retryProcessing').mockReturnValue(throwError(() => error));
            lectureUnitManagementComponent.lecture.set(lecture);
            lectureUnitManagementComponentFixture.componentRef.setInput('lectureId', 5);
            lectureUnitManagementComponent.ngOnInit();
            lectureUnitManagementComponent.isRetryingProcessing.set({ [attachmentVideoUnit.id!]: true });

            lectureUnitManagementComponent.retryProcessing(attachmentVideoUnit);

            expect(lectureUnitManagementComponent.isRetryingProcessing()[attachmentVideoUnit.id!]).toBe(false);
        });

        it('should clear transcription status when retry returns null transcriptionStatus', () => {
            // Set up initial transcription status (e.g., FAILED)
            lectureUnitManagementComponent.transcriptionStatus.set({ [attachmentVideoUnit.id!]: TranscriptionStatus.FAILED });

            // Mock retry returning null transcriptionStatus (transcription was deleted during retry)
            const returnedStatus: LectureUnitCombinedStatus = {
                lectureUnitId: attachmentVideoUnit.id!,
                processingPhase: ProcessingPhase.TRANSCRIBING,
                retryCount: 0,
                transcriptionStatus: undefined,
            };
            vi.spyOn(lectureUnitService, 'retryProcessing').mockReturnValue(of(returnedStatus));
            lectureUnitManagementComponent.lecture.set(lecture);
            lectureUnitManagementComponentFixture.componentRef.setInput('lectureId', 5);
            lectureUnitManagementComponent.ngOnInit();

            lectureUnitManagementComponent.retryProcessing(attachmentVideoUnit);

            // Verify the old FAILED status was cleared
            expect(lectureUnitManagementComponent.transcriptionStatus()[attachmentVideoUnit.id!]).toBeUndefined();
        });
    });

    describe('PDF drop zone', () => {
        it('should create attachment units from dropped PDF files', () => {
            const alertService = TestBed.inject(AlertService);

            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 42;
            createdUnit.name = 'Test File';

            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));
            const successSpy = vi.spyOn(alertService, 'success');

            lectureUnitManagementComponent.lecture.set(lecture);
            // resolvedLectureId is already set from route in ngOnInit

            const pdfFile = new File(['content'], 'Test_File.pdf', { type: 'application/pdf' });
            lectureUnitManagementComponent.onPdfFilesDropped([pdfFile]);

            expect(createSpy).toHaveBeenCalledTimes(1);
            expect(successSpy).toHaveBeenCalledWith('artemisApp.lecture.pdfUpload.success');
        });

        it('should navigate to edit page after upload', () => {
            const router = TestBed.inject(Router);
            const navigateSpy = vi.spyOn(router, 'navigate');

            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 99;

            vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            lectureUnitManagementComponent.lecture.set(lecture);
            // resolvedLectureId is already set from route in ngOnInit

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
            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockImplementation(() => {
                callCount++;
                const unit = new AttachmentVideoUnit();
                unit.id = callCount;
                return of(new HttpResponse({ body: unit, status: 201 }));
            });
            const successSpy = vi.spyOn(alertService, 'success');

            lectureUnitManagementComponent.lecture.set(lecture);
            // resolvedLectureId is already set from route in ngOnInit

            const pdfFiles = [
                new File(['content1'], 'file1.pdf', { type: 'application/pdf' }),
                new File(['content2'], 'file2.pdf', { type: 'application/pdf' }),
                new File(['content3'], 'file3.pdf', { type: 'application/pdf' }),
            ];

            lectureUnitManagementComponent.onPdfFilesDropped(pdfFiles);

            expect(createSpy).toHaveBeenCalledTimes(3);
            expect(successSpy).toHaveBeenCalledTimes(1);
        });

        it('should show error alert on upload failure', () => {
            const alertService = TestBed.inject(AlertService);

            // Use status 400 as status 500 intentionally doesn't show an alert (see onError in global.utils.ts)
            vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(throwError(() => ({ status: 400 })));
            const errorSpy = vi.spyOn(alertService, 'error');

            lectureUnitManagementComponent.lecture.set(lecture);
            // resolvedLectureId is already set from route in ngOnInit

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            lectureUnitManagementComponent.onPdfFilesDropped([pdfFile]);

            expect(errorSpy).toHaveBeenCalled();
            expect(lectureUnitManagementComponent.isUploadingPdfs()).toBe(false);
        });

        it('should not process if no files are provided', () => {
            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile');

            lectureUnitManagementComponent.lecture.set(lecture);
            // resolvedLectureId is already set from route in ngOnInit

            lectureUnitManagementComponent.onPdfFilesDropped([]);

            expect(createSpy).not.toHaveBeenCalled();
        });

        it('should not process if lectureId is undefined', () => {
            const createSpy = vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile');

            lectureUnitManagementComponent.lecture.set(lecture);
            // Set resolvedLectureId to undefined via private property access
            (lectureUnitManagementComponent as any).resolvedLectureId = undefined;

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            lectureUnitManagementComponent.onPdfFilesDropped([pdfFile]);

            expect(createSpy).not.toHaveBeenCalled();
        });

        it('should load data if navigation fails due to missing course', () => {
            const loadDataSpy = vi.spyOn(lectureUnitManagementComponent, 'loadData');

            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 99;

            vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            // Lecture without course
            const lectureWithoutCourse = new Lecture();
            lectureWithoutCourse.id = 1;
            lectureUnitManagementComponent.lecture.set(lectureWithoutCourse);
            // resolvedLectureId is already set from route in ngOnInit

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            lectureUnitManagementComponent.onPdfFilesDropped([pdfFile]);

            expect(loadDataSpy).toHaveBeenCalled();
        });

        it('should set isUploadingPdfs during upload', () => {
            const createdUnit = new AttachmentVideoUnit();
            createdUnit.id = 1;

            vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockReturnValue(of(new HttpResponse({ body: createdUnit, status: 201 })));

            lectureUnitManagementComponent.lecture.set(lecture);
            // resolvedLectureId is already set from route in ngOnInit

            expect(lectureUnitManagementComponent.isUploadingPdfs()).toBe(false);

            const pdfFile = new File(['content'], 'test.pdf', { type: 'application/pdf' });
            lectureUnitManagementComponent.onPdfFilesDropped([pdfFile]);

            // After completion (synchronous in this test due to of())
            expect(lectureUnitManagementComponent.isUploadingPdfs()).toBe(false);
        });

        it('should navigate to last created unit when uploading multiple files', () => {
            const router = TestBed.inject(Router);
            const navigateSpy = vi.spyOn(router, 'navigate');

            let callCount = 0;
            vi.spyOn(attachmentVideoUnitService, 'createAttachmentVideoUnitFromFile').mockImplementation(() => {
                callCount++;
                const unit = new AttachmentVideoUnit();
                unit.id = callCount * 10; // 10, 20, 30
                return of(new HttpResponse({ body: unit, status: 201 }));
            });

            lectureUnitManagementComponent.lecture.set(lecture);
            // resolvedLectureId is already set from route in ngOnInit

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
    describe('content item cards', () => {
        function withAttachment(link: string | undefined, videoSource?: string): AttachmentVideoUnit {
            const unit = new AttachmentVideoUnit();
            unit.type = LectureUnitType.ATTACHMENT_VIDEO;
            unit.attachment = link ? { link } : undefined;
            unit.videoSource = videoSource;
            return unit;
        }

        it.each([
            ['a PDF', withAttachment('attachments/slides.PDF'), 'pdf', faFilePdf],
            ['another file', withAttachment('attachments/sheet.zip'), 'file', faFile],
            ['a video', withAttachment(undefined, 'https://live.rbg.tum.de/w/1'), 'video', faFileVideo],
            ['a file and a video', withAttachment('attachments/slides.pdf', 'https://live.rbg.tum.de/w/1'), 'fileAndVideo', faFileVideo],
        ])('should name a file unit with %s by what it holds', (_, unit, kind, icon) => {
            expect(lectureUnitManagementComponent.getTypeLabelKey(unit)).toBe(`artemisApp.lectureUnit.management.type.${kind}`);
            expect(lectureUnitManagementComponent.getTypeIcon(unit)).toBe(icon);
        });

        it.each([
            [LectureUnitType.TEXT, 'text', faScroll],
            [LectureUnitType.EXERCISE, 'exercise', faCheck],
            [LectureUnitType.ONLINE, 'online', faLink],
        ])('should name a %s unit by its kind', (type, kind, icon) => {
            const unit = { type } as LectureUnit;

            expect(lectureUnitManagementComponent.getTypeLabelKey(unit)).toBe(`artemisApp.lectureUnit.management.type.${kind}`);
            expect(lectureUnitManagementComponent.getTypeIcon(unit)).toBe(icon);
        });

        it('should tell a unit released later from a visible one, using the release date of an exercise for its unit', () => {
            const later = { type: LectureUnitType.TEXT, releaseDate: dayjs().add(1, 'day') } as LectureUnit;
            const earlier = { type: LectureUnitType.TEXT, releaseDate: dayjs().subtract(1, 'day') } as LectureUnit;
            const exerciseLater = { type: LectureUnitType.EXERCISE, exercise: { releaseDate: dayjs().add(2, 'days') } } as ExerciseUnit;

            expect(lectureUnitManagementComponent.isReleasedLater(later)).toBe(true);
            expect(lectureUnitManagementComponent.isReleasedLater(earlier)).toBe(false);
            expect(lectureUnitManagementComponent.isReleasedLater({ type: LectureUnitType.TEXT } as LectureUnit)).toBe(false);
            expect(lectureUnitManagementComponent.isReleasedLater(exerciseLater)).toBe(true);
        });

        it('should show one card per unit with its name and linked competencies', async () => {
            textUnit.type = LectureUnitType.TEXT;
            textUnit.name = 'Reading';
            textUnit.competencyLinks = [
                new CompetencyLectureUnitLink({ id: 7, title: 'Modeling' }, undefined, 1),
                new CompetencyLectureUnitLink({ id: 8, title: 'Testing' }, undefined, 1),
            ];
            vi.spyOn(lectureUnitService, 'getLectureUnitName').mockImplementation((unit: LectureUnit) => unit.name ?? '');
            // The first render of the setup already loaded the units, so load the changed ones again.
            lectureUnitManagementComponent.loadData();
            await lectureUnitManagementComponentFixture.whenStable();

            const cards = lectureUnitManagementComponentFixture.debugElement.queryAll(By.css('[data-testid="lecture-unit"]'));
            expect(cards).toHaveLength(3);
            expect(cards[0].query(By.css('[data-testid="lecture-unit-name"]')).nativeElement.textContent.trim()).toBe('Reading');
            expect(cards[0].queryAll(By.css('[data-testid="lecture-unit-competency"]')).map((tag) => tag.nativeElement.textContent.trim())).toEqual(['Modeling', 'Testing']);

            lectureUnitManagementComponentFixture.componentRef.setInput('showCompetencies', false);
            await lectureUnitManagementComponentFixture.whenStable();
            expect(lectureUnitManagementComponentFixture.debugElement.queryAll(By.css('[data-testid="lecture-unit-competency"]'))).toHaveLength(0);
        });

        it('should say that the lecture has no content yet', async () => {
            lecture.lectureUnits = [];
            lectureUnitManagementComponent.loadData();
            await lectureUnitManagementComponentFixture.whenStable();

            expect(lectureUnitManagementComponentFixture.debugElement.query(By.css('[data-testid="lecture-unit-empty"]'))).not.toBeNull();
            expect(lectureUnitManagementComponentFixture.debugElement.query(By.css('[data-testid="lecture-unit-list"]'))).toBeNull();
        });

        it('should announce the units to a page that works with them', () => {
            const announced: LectureUnit[][] = [];
            lectureUnitManagementComponent.lectureUnitsChange.subscribe((units) => announced.push(units));

            lectureUnitManagementComponent.loadData();
            TestBed.tick();

            expect(announced.at(-1)).toEqual([textUnit, exerciseUnit, attachmentVideoUnit]);
        });
    });
});
