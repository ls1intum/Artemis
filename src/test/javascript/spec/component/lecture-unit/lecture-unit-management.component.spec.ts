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
import { ActivatedRoute, Params, Router, RouterOutlet } from '@angular/router';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LectureService } from 'app/lecture/lecture.service';
import { AlertService } from 'app/core/util/alert.service';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { HttpResponse } from '@angular/common/http';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { UnitCreationCardComponent } from 'app/lecture/lecture-unit/lecture-unit-management/unit-creation-card/unit-creation-card.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';

@Component({ selector: 'jhi-learning-goals-popover', template: '' })
class LearningGoalsPopoverStubComponent {
    @Input()
    courseId: number;
    @Input()
    learningGoals: LearningGoal[] = [];
    @Input()
    navigateTo: 'learningGoalManagement' | 'courseStatistics' = 'courseStatistics';
}
describe('LectureUnitManagementComponent', () => {
    let lectureUnitManagementComponent: LectureUnitManagementComponent;
    let lectureUnitManagementComponentFixture: ComponentFixture<LectureUnitManagementComponent>;

    let lectureService: LectureService;
    let lectureUnitService: LectureUnitService;
    let findLectureSpy: jest.SpyInstance;
    let findLectureWithDetailsSpy: jest.SpyInstance;
    let updateOrderSpy: jest.SpyInstance;

    let attachmentUnit: AttachmentUnit;
    let exerciseUnit: ExerciseUnit;
    let textUnit: TextUnit;
    let videoUnit: VideoUnit;
    let lecture: Lecture;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [],
            declarations: [
                LectureUnitManagementComponent,
                MockComponent(UnitCreationCardComponent),
                LearningGoalsPopoverStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(ExerciseUnitComponent),
                MockComponent(AttachmentUnitComponent),
                MockComponent(VideoUnitComponent),
                MockComponent(TextUnitComponent),
                MockComponent(FaIconComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(RouterOutlet),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(LectureUnitService),
                MockProvider(LectureService),
                MockProvider(AlertService),
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

                findLectureSpy = jest.spyOn(lectureService, 'find');
                findLectureWithDetailsSpy = jest.spyOn(lectureService, 'findWithDetails');
                updateOrderSpy = jest.spyOn(lectureUnitService, 'updateOrder');

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

                lectureUnitManagementComponentFixture.detectChanges();
            });
    });

    it('should move down', () => {
        const originalOrder = [...lecture.lectureUnits!];
        lectureUnitManagementComponentFixture.detectChanges();
        const moveDownSpy = jest.spyOn(lectureUnitManagementComponent, 'moveDown');
        const moveUpSpy = jest.spyOn(lectureUnitManagementComponent, 'moveUp');
        const upButton = lectureUnitManagementComponentFixture.debugElement.query(By.css('#up-0'));
        expect(upButton).toBeDefined();
        upButton.nativeElement.click();
        expect(moveUpSpy).toHaveBeenCalledTimes(0);
        // not moved as first one
        expect(lectureUnitManagementComponent.lectureUnits[0].id).toEqual(originalOrder[0].id);
        const downButton = lectureUnitManagementComponentFixture.debugElement.query(By.css('#down-0'));
        expect(downButton).toBeDefined();
        downButton.nativeElement.click();
        expect(moveDownSpy).toHaveBeenCalledOnce();
        expect(lectureUnitManagementComponent.lectureUnits[0].id).toEqual(originalOrder[1].id);
        expect(lectureUnitManagementComponent.lectureUnits[1].id).toEqual(originalOrder[0].id);
    });

    it('should move up', () => {
        const originalOrder = [...lecture.lectureUnits!];
        lectureUnitManagementComponentFixture.detectChanges();
        const moveDownSpy = jest.spyOn(lectureUnitManagementComponent, 'moveDown');
        const moveUpSpy = jest.spyOn(lectureUnitManagementComponent, 'moveUp');
        const lastPosition = lectureUnitManagementComponent.lectureUnits.length - 1;
        const downButton = lectureUnitManagementComponentFixture.debugElement.query(By.css(`#down-${lastPosition}`));
        expect(downButton).toBeDefined();
        downButton.nativeElement.click();
        expect(moveDownSpy).toHaveBeenCalledTimes(0);

        expect(lectureUnitManagementComponent.lectureUnits[lastPosition].id).toEqual(originalOrder[lastPosition].id);
        const upButton = lectureUnitManagementComponentFixture.debugElement.query(By.css(`#up-${lastPosition}`));
        expect(upButton).toBeDefined();
        upButton.nativeElement.click();
        expect(moveUpSpy).toHaveBeenCalledOnce();
        expect(lectureUnitManagementComponent.lectureUnits[lastPosition].id).toEqual(originalOrder[lastPosition - 1].id);
    });

    it('should navigate to edit attachment unit page', () => {
        const editButtonClickedSpy = jest.spyOn(lectureUnitManagementComponent, 'editButtonRouterLink');
        const buttons = lectureUnitManagementComponentFixture.debugElement.queryAll(By.css(`.edit`));
        for (const button of buttons) {
            button.nativeElement.click();
        }
        lectureUnitManagementComponentFixture.detectChanges();
        expect(editButtonClickedSpy).toHaveBeenCalledTimes(buttons.length * 2); // 3 units with edit button, each method is invoked twice
    });

    it('should give the correct delete question translation key', () => {
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new AttachmentUnit())).toEqual('artemisApp.attachmentUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new ExerciseUnit())).toEqual('artemisApp.exerciseUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new TextUnit())).toEqual('artemisApp.textUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new VideoUnit())).toEqual('artemisApp.videoUnit.delete.question');
    });

    it('should give the correct confirmation text translation key', () => {
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new AttachmentUnit())).toEqual('artemisApp.attachmentUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new ExerciseUnit())).toEqual('artemisApp.exerciseUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new VideoUnit())).toEqual('artemisApp.videoUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new TextUnit())).toEqual('artemisApp.textUnit.delete.typeNameToConfirm');
    });

    it('should give the correct action type', () => {
        expect(lectureUnitManagementComponent.getActionType(new AttachmentUnit())).toEqual(ActionType.Delete);
        expect(lectureUnitManagementComponent.getActionType(new ExerciseUnit())).toEqual(ActionType.Unlink);
        expect(lectureUnitManagementComponent.getActionType(new TextUnit())).toEqual(ActionType.Delete);
        expect(lectureUnitManagementComponent.getActionType(new VideoUnit())).toEqual(ActionType.Delete);
    });
});
