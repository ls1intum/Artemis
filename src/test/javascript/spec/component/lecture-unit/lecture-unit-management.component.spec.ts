import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { Lecture } from 'app/entities/lecture.model';
import { Attachment } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import * as moment from 'moment';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { ReactiveFormsModule } from '@angular/forms';
import { UnitCreationCardComponent } from 'app/lecture/lecture-unit/lecture-unit-management/unit-creation-card/unit-creation-card.component';
import { By } from '@angular/platform-browser';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import sinon = require('sinon');

chai.use(sinonChai);
const expect = chai.expect;
describe('LectureUnitManagementComponent', () => {
    let comp: LectureUnitManagementComponent;
    let fixture: ComponentFixture<LectureUnitManagementComponent>;

    const attachments = [
        {
            id: 50,
            name: 'test',
            link: '/api/files/attachments/lecture/4/Mein_Test_PDF4.pdf',
            version: 2,
            uploadDate: moment('2019-05-05T10:05:25+02:00'),
            releaseDate: moment('2019-06-05T10:05:25+02:00'),
            attachmentType: 'FILE',
        },
        {
            id: 52,
            name: 'test2',
            link: '/api/files/attachments/lecture/4/Mein_Test_PDF3.pdf',
            version: 1,
            uploadDate: moment('2019-05-07T08:49:59+02:00'),
            attachmentType: 'FILE',
        },
    ] as Attachment[];

    const attachmentUnits = [
        {
            id: 42,
            name: 'A',
            attachment: attachments[0],
            type: LectureUnitType.ATTACHMENT,
        },
        {
            id: 49,
            name: 'B',
            attachment: attachments[1],
            type: LectureUnitType.ATTACHMENT,
        },
    ] as AttachmentUnit[];

    const lecture = {
        id: 4,
        title: 'Second Test Lecture2',
        description:
            'Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.',
        startDate: moment('2019-04-15T14:00:19+02:00'),
        endDate: moment('2019-04-15T15:30:20+02:00'),
        course: {
            id: 1,
            title: 'Refactoring CSS',
            description:
                'Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.',
            shortName: 'RCSS',
            studentGroupName: 'artemis-dev',
            teachingAssistantGroupName: 'tumuser',
            instructorGroupName: 'tumuser',
            startDate: moment('2018-12-15T16:11:00+01:00'),
            endDate: moment('2019-06-15T16:11:14+02:00'),
            onlineCourse: false,
            color: '#691b0b',
            registrationEnabled: false,
        },
        lectureUnits: attachmentUnits,
    } as Lecture;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [
                TranslateModule.forRoot(),
                ArtemisTestModule,
                RouterTestingModule.withRoutes([]),
                ArtemisSharedModule,
                ReactiveFormsModule,
                ArtemisSharedComponentModule,
                ArtemisCoursesModule,
                FormDateTimePickerModule,
            ],
            declarations: [LectureUnitManagementComponent, UnitCreationCardComponent],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LectureUnitManagementComponent);
                comp = fixture.componentInstance;
                comp.lectureUnits = [...lecture.lectureUnits!];
            });
    });
    it('should move down', () => {
        const moveDownSpy = sinon.spy(comp, 'moveDown');
        const moveUpSpy = sinon.spy(comp, 'moveUp');
        fixture.detectChanges();
        const upButton = fixture.debugElement.query(By.css('#up-0'));
        expect(upButton).to.exist;
        upButton.nativeElement.click();
        fixture.detectChanges();
        expect(moveUpSpy).to.not.have.been.calledOnce;
        // not moved as first one
        expect(comp.lectureUnits[0].id).to.equal(42);
        const downButton = fixture.debugElement.query(By.css('#down-0'));
        expect(downButton).to.exist;
        downButton.nativeElement.click();
        fixture.detectChanges();
        expect(moveDownSpy).to.have.been.calledOnce;
        expect(comp.lectureUnits[0].id).to.equal(49);
    });

    it('should move up', () => {
        const moveDownSpy = sinon.spy(comp, 'moveDown');
        const moveUpSpy = sinon.spy(comp, 'moveUp');
        fixture.detectChanges();
        const downButton = fixture.debugElement.query(By.css('#down-1'));
        expect(downButton).to.exist;
        downButton.nativeElement.click();
        fixture.detectChanges();
        expect(moveDownSpy).to.not.have.been.calledOnce;
        // not moved as last one
        expect(comp.lectureUnits[1].id).to.equal(49);

        const upButton = fixture.debugElement.query(By.css('#up-1'));
        expect(upButton).to.exist;
        upButton.nativeElement.click();
        fixture.detectChanges();
        expect(moveUpSpy).to.have.been.calledOnce;
        expect(comp.lectureUnits[1].id).to.equal(42);
    });

    it('should give the correct delete question translation key', () => {
        expect(comp.getDeleteQuestionKey(new AttachmentUnit())).to.equal('artemisApp.attachmentUnit.delete.question');
        expect(comp.getDeleteQuestionKey(new ExerciseUnit())).to.equal('artemisApp.exerciseUnit.delete.question');
    });

    it('should give the correct confirmation text translation key', () => {
        expect(comp.getDeleteConfirmationTextKey(new AttachmentUnit())).to.equal('artemisApp.attachmentUnit.delete.typeNameToConfirm');
        expect(comp.getDeleteConfirmationTextKey(new ExerciseUnit())).to.equal('artemisApp.exerciseUnit.delete.typeNameToConfirm');
    });

    it('should give the correct action type', () => {
        expect(comp.getActionType(new AttachmentUnit())).to.equal(ActionType.Delete);
        expect(comp.getActionType(new ExerciseUnit())).to.equal(ActionType.Unlink);
    });
});
