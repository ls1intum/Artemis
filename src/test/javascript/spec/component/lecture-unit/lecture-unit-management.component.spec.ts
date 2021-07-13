import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import * as sinon from 'sinon';
import { MockPipe } from 'ng-mocks';
import { Component, Directive, Input } from '@angular/core';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { ActivatedRoute, Params, Router, RouterOutlet } from '@angular/router';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LectureService } from 'app/lecture/lecture.service';
import { JhiAlertService } from 'ng-jhipster';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { Lecture } from 'app/entities/lecture.model';
import { HttpResponse } from '@angular/common/http';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { UnitCreationCardComponent } from 'app/lecture/lecture-unit/lecture-unit-management/unit-creation-card/unit-creation-card.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

chai.use(sinonChai);
const expect = chai.expect;

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[routerLink]' })
export class MockRouterLinkDirective {
    @Input('routerLink') data: any;
}

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
    let findLectureStub: sinon.SinonStub;
    let updateOrderStub: sinon.SinonStub;

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
                MockComponent(AlertComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(RouterOutlet),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(LectureUnitService),
                MockProvider(LectureService),
                MockProvider(JhiAlertService),
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

                findLectureStub = sinon.stub(lectureService, 'find');
                updateOrderStub = sinon.stub(lectureUnitService, 'updateOrder');

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

                findLectureStub.returns(
                    of(
                        new HttpResponse({
                            body: lecture,
                            status: 200,
                        }),
                    ),
                );

                updateOrderStub.returns(
                    of(
                        new HttpResponse({
                            body: [],
                            status: 200,
                        }),
                    ),
                );

                lectureUnitManagementComponentFixture.detectChanges();
            });
    });
    it('should initialize', () => {
        lectureUnitManagementComponentFixture.detectChanges();
        expect(lectureUnitManagementComponent).to.be.ok;
    });

    it('should move down', () => {
        const originalOrder = [...lecture.lectureUnits!];
        lectureUnitManagementComponentFixture.detectChanges();
        const moveDownSpy = sinon.spy(lectureUnitManagementComponent, 'moveDown');
        const moveUpSpy = sinon.spy(lectureUnitManagementComponent, 'moveUp');
        const upButton = lectureUnitManagementComponentFixture.debugElement.query(By.css('#up-0'));
        expect(upButton).to.exist;
        upButton.nativeElement.click();
        expect(moveUpSpy).to.not.have.been.calledOnce;
        // not moved as first one
        expect(lectureUnitManagementComponent.lectureUnits[0].id).to.equal(originalOrder[0].id);
        const downButton = lectureUnitManagementComponentFixture.debugElement.query(By.css('#down-0'));
        expect(downButton).to.exist;
        downButton.nativeElement.click();
        expect(moveDownSpy).to.have.been.calledOnce;
        expect(lectureUnitManagementComponent.lectureUnits[0].id).to.equal(originalOrder[1].id);
        expect(lectureUnitManagementComponent.lectureUnits[1].id).to.equal(originalOrder[0].id);
    });

    it('should move up', () => {
        const originalOrder = [...lecture.lectureUnits!];
        lectureUnitManagementComponentFixture.detectChanges();
        const moveDownSpy = sinon.spy(lectureUnitManagementComponent, 'moveDown');
        const moveUpSpy = sinon.spy(lectureUnitManagementComponent, 'moveUp');
        const lastPosition = lectureUnitManagementComponent.lectureUnits.length - 1;
        const downButton = lectureUnitManagementComponentFixture.debugElement.query(By.css(`#down-${lastPosition}`));
        expect(downButton).to.exist;
        downButton.nativeElement.click();
        expect(moveDownSpy).to.not.have.been.calledOnce;

        expect(lectureUnitManagementComponent.lectureUnits[lastPosition].id).to.equal(originalOrder[lastPosition].id);
        const upButton = lectureUnitManagementComponentFixture.debugElement.query(By.css(`#up-${lastPosition}`));
        expect(upButton).to.exist;
        upButton.nativeElement.click();
        expect(moveUpSpy).to.have.been.calledOnce;
        expect(lectureUnitManagementComponent.lectureUnits[lastPosition].id).to.equal(originalOrder[lastPosition - 1].id);
    });

    it('should navigate to edit attachment unit page', () => {
        const editButtonClickedSpy = sinon.spy(lectureUnitManagementComponent, 'editButtonRouterLink');
        const buttons = lectureUnitManagementComponentFixture.debugElement.queryAll(By.css(`.edit`));
        for (const button of buttons) {
            button.nativeElement.click();
        }
        lectureUnitManagementComponentFixture.detectChanges();
        expect(editButtonClickedSpy).to.have.been.called;
    });

    it('should give the correct delete question translation key', () => {
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new AttachmentUnit())).to.equal('artemisApp.attachmentUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new ExerciseUnit())).to.equal('artemisApp.exerciseUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new TextUnit())).to.equal('artemisApp.textUnit.delete.question');
        expect(lectureUnitManagementComponent.getDeleteQuestionKey(new VideoUnit())).to.equal('artemisApp.videoUnit.delete.question');
    });

    it('should give the correct confirmation text translation key', () => {
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new AttachmentUnit())).to.equal('artemisApp.attachmentUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new ExerciseUnit())).to.equal('artemisApp.exerciseUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new VideoUnit())).to.equal('artemisApp.videoUnit.delete.typeNameToConfirm');
        expect(lectureUnitManagementComponent.getDeleteConfirmationTextKey(new TextUnit())).to.equal('artemisApp.textUnit.delete.typeNameToConfirm');
    });

    it('should give the correct action type', () => {
        expect(lectureUnitManagementComponent.getActionType(new AttachmentUnit())).to.equal(ActionType.Delete);
        expect(lectureUnitManagementComponent.getActionType(new ExerciseUnit())).to.equal(ActionType.Unlink);
        expect(lectureUnitManagementComponent.getActionType(new TextUnit())).to.equal(ActionType.Delete);
        expect(lectureUnitManagementComponent.getActionType(new VideoUnit())).to.equal(ActionType.Delete);
    });
});
