import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUnitManagementComponent } from 'app/lecture/manage/lecture-units/management/lecture-unit-management.component';
import { AttachmentVideoUnit, TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Component, Input } from '@angular/core';
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
import { of, throwError } from 'rxjs';
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

@Component({ selector: 'jhi-competencies-popover', template: '' })
class CompetenciesPopoverStubComponent {
    @Input() courseId!: number;
    @Input() competencyLinks: CompetencyLectureUnitLink[] = [];
    @Input() navigateTo: 'competencyManagement' | 'courseStatistics' = 'courseStatistics';
}

describe('LectureUnitManagementComponent', () => {
    let lectureUnitManagementComponent: LectureUnitManagementComponent;
    let lectureUnitManagementComponentFixture: ComponentFixture<LectureUnitManagementComponent>;
    let lectureService: LectureService;
    let lectureUnitService: LectureUnitService;
    let lectureTranscriptionService: LectureTranscriptionService;
    let alertService: AlertService;
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
            imports: [MockDirective(NgbTooltip), FaIconComponent],
            declarations: [
                LectureUnitManagementComponent,
                MockComponent(UnitCreationCardComponent),
                CompetenciesPopoverStubComponent,
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
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
            ],
        }).compileComponents();
        lectureUnitManagementComponentFixture = TestBed.createComponent(LectureUnitManagementComponent);
        lectureUnitManagementComponent = lectureUnitManagementComponentFixture.componentInstance;
        lectureService = TestBed.inject(LectureService);
        lectureUnitService = TestBed.inject(LectureUnitService);
        lectureTranscriptionService = TestBed.inject(LectureTranscriptionService);
        alertService = TestBed.inject(AlertService);
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
        lecture.id = 0;
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
            expect(lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!]).toBe(ProcessingPhase.DONE);
        });

        it('should correctly identify processing states', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = ProcessingPhase.IDLE;
            expect(lectureUnitManagementComponent.isProcessingIdle(attachmentVideoUnit)).toBeTrue();

            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = ProcessingPhase.TRANSCRIBING;
            expect(lectureUnitManagementComponent.isProcessingTranscribing(attachmentVideoUnit)).toBeTrue();
            expect(lectureUnitManagementComponent.isProcessingInProgress(attachmentVideoUnit)).toBeTrue();

            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = ProcessingPhase.INGESTING;
            expect(lectureUnitManagementComponent.isProcessingIngesting(attachmentVideoUnit)).toBeTrue();
            expect(lectureUnitManagementComponent.isProcessingInProgress(attachmentVideoUnit)).toBeTrue();

            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = ProcessingPhase.DONE;
            expect(lectureUnitManagementComponent.isProcessingDone(attachmentVideoUnit)).toBeTrue();

            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = ProcessingPhase.FAILED;
            expect(lectureUnitManagementComponent.isProcessingFailed(attachmentVideoUnit)).toBeTrue();
        });

        it('should return true for hasProcessingBadge when processing is in progress', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = ProcessingPhase.TRANSCRIBING;
            expect(lectureUnitManagementComponent.hasProcessingBadge(attachmentVideoUnit)).toBeTrue();
        });

        it('should return true for hasProcessingBadge when processing is done', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = ProcessingPhase.DONE;
            expect(lectureUnitManagementComponent.hasProcessingBadge(attachmentVideoUnit)).toBeTrue();
        });

        it('should return true for hasProcessingBadge when processing failed', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = ProcessingPhase.FAILED;
            expect(lectureUnitManagementComponent.hasProcessingBadge(attachmentVideoUnit)).toBeTrue();
        });
    });

    describe('isAwaitingProcessing', () => {
        it('should return true when status is IDLE and course is active', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = ProcessingPhase.IDLE;
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
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = ProcessingPhase.TRANSCRIBING;
            expect(lectureUnitManagementComponent.isAwaitingProcessing(attachmentVideoUnit)).toBeFalse();
        });

        it('should return false when processing is done', () => {
            lectureUnitManagementComponent.processingStatus[attachmentVideoUnit.id!] = ProcessingPhase.DONE;
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
});
