import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Component, Input } from '@angular/core';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LectureService } from 'app/lecture/lecture.service';
import { AlertService } from 'app/core/util/alert.service';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { HttpResponse } from '@angular/common/http';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { of, throwError } from 'rxjs';
import { By } from '@angular/platform-browser';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Competency } from 'app/entities/competency.model';
import { UnitCreationCardComponent } from 'app/lecture/lecture-unit/lecture-unit-management/unit-creation-card/unit-creation-card.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { IrisCourseSettings } from 'app/entities/iris/settings/iris-settings.model';
import { IrisLectureIngestionSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { Course } from 'app/entities/course.model';

@Component({ selector: 'jhi-competencies-popover', template: '' })
class CompetenciesPopoverStubComponent {
    @Input()
    courseId: number;
    @Input()
    competencies: Competency[] = [];
    @Input()
    navigateTo: 'competencyManagement' | 'courseStatistics' = 'courseStatistics';
}

describe('LectureUnitManagementComponent', () => {
    let lectureUnitManagementComponent: LectureUnitManagementComponent;
    let lectureUnitManagementComponentFixture: ComponentFixture<LectureUnitManagementComponent>;

    let lectureService: LectureService;
    let lectureUnitService: LectureUnitService;
    let profileService: ProfileService;
    let irisSettingsService: IrisSettingsService;
    let findLectureSpy: jest.SpyInstance;
    let findLectureWithDetailsSpy: jest.SpyInstance;
    let deleteLectureUnitSpy: jest.SpyInstance;
    let updateOrderSpy: jest.SpyInstance;
    let getProfileInfo: jest.SpyInstance;
    let getCombinedCourseSettings: jest.SpyInstance;
    let attachmentUnit: AttachmentUnit;
    let exerciseUnit: ExerciseUnit;
    let textUnit: TextUnit;
    let videoUnit: VideoUnit;
    let lecture: Lecture;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip)],
            declarations: [
                LectureUnitManagementComponent,
                MockComponent(UnitCreationCardComponent),
                CompetenciesPopoverStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(ExerciseUnitComponent),
                MockComponent(AttachmentUnitComponent),
                MockComponent(VideoUnitComponent),
                MockComponent(TextUnitComponent),
                MockComponent(FaIconComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(LectureUnitService),
                MockProvider(LectureService),
                MockProvider(AlertService),
                MockProvider(ProfileService),
                MockProvider(IrisSettingsService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: {
                                subscribe: (fn: (value: Params) => void) =>
                                    fn({
                                        lectureId: 1,
                                    }),
                            },
                        },
                        children: [],
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                lectureUnitManagementComponentFixture = TestBed.createComponent(LectureUnitManagementComponent);
                lectureUnitManagementComponent = lectureUnitManagementComponentFixture.componentInstance;
                lectureService = TestBed.inject(LectureService);
                lectureUnitService = TestBed.inject(LectureUnitService);
                profileService = TestBed.inject(ProfileService);
                irisSettingsService = TestBed.inject(IrisSettingsService);
                findLectureSpy = jest.spyOn(lectureService, 'find');
                findLectureWithDetailsSpy = jest.spyOn(lectureService, 'findWithDetails');
                deleteLectureUnitSpy = jest.spyOn(lectureUnitService, 'delete');
                updateOrderSpy = jest.spyOn(lectureUnitService, 'updateOrder');
                getProfileInfo = jest.spyOn(profileService, 'getProfileInfo');
                getCombinedCourseSettings = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings');

                textUnit = new TextUnit();
                textUnit.id = 0;
                videoUnit = new VideoUnit();
                videoUnit.id = 1;
                exerciseUnit = new ExerciseUnit();
                exerciseUnit.id = 2;
                attachmentUnit = new AttachmentUnit();
                attachmentUnit.id = 3;

                lecture = new Lecture();
                lecture.id = 0;
                lecture.lectureUnits = [textUnit, videoUnit, exerciseUnit, attachmentUnit];

                const returnValue = of(new HttpResponse({ body: lecture, status: 200 }));
                findLectureSpy.mockReturnValue(returnValue);
                findLectureWithDetailsSpy.mockReturnValue(returnValue);
                updateOrderSpy.mockReturnValue(returnValue);
                deleteLectureUnitSpy.mockReturnValue(of(new HttpResponse({ body: videoUnit, status: 200 })));
                const profileInfo = { activeProfiles: [PROFILE_IRIS] } as ProfileInfo;
                getProfileInfo.mockReturnValue(of(profileInfo));
                const irisCourseSettings = new IrisCourseSettings();
                irisCourseSettings.irisLectureIngestionSettings = new IrisLectureIngestionSubSettings();
                irisCourseSettings.irisLectureIngestionSettings.enabled = true;
                getCombinedCourseSettings.mockReturnValue(of(irisCourseSettings));
                lectureUnitManagementComponentFixture.detectChanges();
            });
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
        lectureUnitManagementComponent.emitEditEvents = true;
        const editButtonClickedSpy = jest.spyOn(lectureUnitManagementComponent, 'onEditButtonClicked');
        lectureUnitManagementComponentFixture.detectChanges();
        const buttons = lectureUnitManagementComponentFixture.debugElement.queryAll(By.css(`.edit`));
        buttons.forEach((button) => button.nativeElement.click());
        lectureUnitManagementComponentFixture.detectChanges();
        expect(editButtonClickedSpy).toHaveBeenCalledTimes(buttons.length);
    });

    it('should show loadData on delete', () => {
        const loadDataSpy = jest.spyOn(lectureUnitManagementComponent, 'loadData');
        lectureUnitManagementComponent.deleteLectureUnit(1);
        expect(loadDataSpy).toHaveBeenCalledOnce();
    });

    it('should give the correct delete question translation key', () => {
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new AttachmentUnit())).toBe('artemisApp.attachmentUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new ExerciseUnit())).toBe('artemisApp.exerciseUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new TextUnit())).toBe('artemisApp.textUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new VideoUnit())).toBe('artemisApp.videoUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new OnlineUnit())).toBe('artemisApp.onlineUnit.delete.question');
    });

    it('should give the correct confirmation text translation key', () => {
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new AttachmentUnit())).toBe('artemisApp.attachmentUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new ExerciseUnit())).toBe('artemisApp.exerciseUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new VideoUnit())).toBe('artemisApp.videoUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new TextUnit())).toBe('artemisApp.textUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new OnlineUnit())).toBe('artemisApp.onlineUnit.delete.typeNameToConfirm');
    });

    it('should give the correct action type', () => {
        expect(lectureUnitManagementComponent.getActionType(new AttachmentUnit())).toEqual(ActionType.Delete);
        expect(lectureUnitManagementComponent.getActionType(new ExerciseUnit())).toEqual(ActionType.Unlink);
        expect(lectureUnitManagementComponent.getActionType(new TextUnit())).toEqual(ActionType.Delete);
        expect(lectureUnitManagementComponent.getActionType(new VideoUnit())).toEqual(ActionType.Delete);
        expect(lectureUnitManagementComponent.getActionType(new OnlineUnit())).toEqual(ActionType.Delete);
    });
    it('should call onIngestButtonClicked when button is clicked', () => {
        const ingestLectureUnitInPyris = jest.spyOn(lectureUnitService, 'ingestLectureUnitInPyris');
        const returnValue = of(new HttpResponse<void>({ status: 200 }));
        ingestLectureUnitInPyris.mockReturnValue(returnValue);
        const lectureUnitId = 1;
        lectureUnitManagementComponent.lecture = { id: 2 } as any;
        lectureUnitManagementComponent.onIngestButtonClicked(lectureUnitId);
        expect(lectureUnitService.ingestLectureUnitInPyris).toHaveBeenCalledWith(lectureUnitId, lectureUnitManagementComponent.lecture.id);
    });

    it('should handle error when ingestLectureUnitInPyris fails', () => {
        const ingestLectureUnitInPyris = jest.spyOn(lectureUnitService, 'ingestLectureUnitInPyris');
        const lectureUnitId = 1;
        lectureUnitManagementComponent.lecture = { id: 2 } as any;
        const error = new Error('Failed to send Ingestion request');
        ingestLectureUnitInPyris.mockReturnValue(throwError(() => error));

        jest.spyOn(console, 'error').mockImplementation(() => {});

        lectureUnitManagementComponent.onIngestButtonClicked(lectureUnitId);

        expect(lectureUnitService.ingestLectureUnitInPyris).toHaveBeenCalledWith(lectureUnitId, lectureUnitManagementComponent.lecture.id);
        expect(console.error).toHaveBeenCalledWith('Failed to send Ingestion request', error);
    });

    it('should update the icon based on ingestion state', () => {
        const attachmentUnit = { pyrisIngestionState: 'NOT_STARTED' } as any;
        expect(lectureUnitManagementComponent.getIcon(attachmentUnit)).toEqual(lectureUnitManagementComponent.sendToIris);

        attachmentUnit.pyrisIngestionState = 'IN_PROGRESS';
        expect(lectureUnitManagementComponent.getIcon(attachmentUnit)).toEqual(lectureUnitManagementComponent.loading);

        attachmentUnit.pyrisIngestionState = 'DONE';
        expect(lectureUnitManagementComponent.getIcon(attachmentUnit)).toEqual(lectureUnitManagementComponent.done);

        attachmentUnit.pyrisIngestionState = 'ERROR';
        expect(lectureUnitManagementComponent.getIcon(attachmentUnit)).toEqual(lectureUnitManagementComponent.resendToIris);
    });
    it('should initialize profile info and check for Iris settings', () => {
        const mockCourse: Course = { id: 1, title: 'Test Course' };
        lectureUnitManagementComponent.lecture = { id: 2, course: mockCourse } as Lecture;
        lectureUnitManagementComponent.initializeProfileInfo();
        expect(profileService.getProfileInfo).toHaveBeenCalled();
        expect(irisSettingsService.getCombinedCourseSettings).toHaveBeenCalledWith(lectureUnitManagementComponent.lecture!.course!.id);
        expect(lectureUnitManagementComponent.irisEnabled).toBeTrue();
        expect(lectureUnitManagementComponent.lectureIngestionEnabled).toBeTrue();
    });
});
