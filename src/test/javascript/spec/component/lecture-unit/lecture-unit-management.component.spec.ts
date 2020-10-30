import { LectureService } from 'app/lecture/lecture.service';
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

chai.use(sinonChai);
const expect = chai.expect;
describe('LectureUnitManagementComponent', () => {
    let comp: LectureUnitManagementComponent;
    let fixture: ComponentFixture<LectureUnitManagementComponent>;
    let lectureService: LectureService;

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
        },
        {
            id: 49,
            name: 'B',
            attachment: attachments[1],
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
            });
    });

    it('should move down', () => {
        comp.lectureUnits = lecture.lectureUnits!;
        fixture.detectChanges();
        const upButton = fixture.debugElement.query(By.css('#up-0'));
        expect(upButton).to.exist;
        upButton.nativeElement.click();
        fixture.detectChanges();
        // not moved as first one
        expect(comp.lectureUnits[0].id).to.equal(42);
        const downButton = fixture.debugElement.query(By.css('#down-0'));
        expect(downButton).to.exist;
        downButton.nativeElement.click();
        fixture.detectChanges();
        expect(comp.lectureUnits[0].id).to.equal(49);
    });

    it('should move up', () => {
        comp.lectureUnits = lecture.lectureUnits!;
        fixture.detectChanges();
        const downButton = fixture.debugElement.query(By.css('#down-1'));
        expect(downButton).to.exist;
        downButton.nativeElement.click();
        fixture.detectChanges();
        // not moved as last one
        expect(comp.lectureUnits[1].id).to.equal(49);

        const upButton = fixture.debugElement.query(By.css('#up-1'));
        expect(upButton).to.exist;
        upButton.nativeElement.click();
        fixture.detectChanges();
        expect(comp.lectureUnits[1].id).to.equal(42);
    });
});
