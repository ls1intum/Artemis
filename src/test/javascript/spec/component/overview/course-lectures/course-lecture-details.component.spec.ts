import { DebugElement } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbAlert, NgbCollapse, NgbDropdownModule, NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import * as moment from 'moment';
import { JhiTranslateDirective } from 'ng-jhipster';
import { AlertService } from 'app/core/util/alert.service';
import { of } from 'rxjs';
import * as chai from 'chai';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';

import { CourseLectureDetailsComponent } from 'app/overview/course-lectures/course-lecture-details.component';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { LearningGoalsPopoverComponent } from 'app/course/learning-goals/learning-goals-popover/learning-goals-popover.component';
import { AlertComponent } from 'app/shared/alert/alert.component';

import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { Lecture } from 'app/entities/lecture.model';
import { Course } from 'app/entities/course.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { FileService } from 'app/shared/http/file.service';
import { LectureService } from 'app/lecture/lecture.service';

import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag.component';
import { DifficultyBadgeComponent } from 'app/exercises/shared/exercise-headers/difficulty-badge.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { MockFileService } from '../../../helpers/mocks/service/mock-file.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseLectureDetails', () => {
    let fixture: ComponentFixture<CourseLectureDetailsComponent>;
    let courseLecturesDetailsComponent: CourseLectureDetailsComponent;
    let lecture: Lecture;
    let lectureUnit1: AttachmentUnit;
    let lectureUnit2: AttachmentUnit;
    let lectureUnit3: TextUnit;
    let mockRouter: any;
    let debugElement: DebugElement;

    beforeEach(() => {
        mockRouter = sinon.createStubInstance(Router);

        const releaseDate = moment('18-03-2020', 'DD-MM-YYYY');

        const course = new Course();
        course.id = 456;

        lecture = new Lecture();
        lecture.id = 1;
        lecture.startDate = releaseDate;
        lecture.description = 'Test desciption';
        lecture.title = 'Test lecture';
        lecture.course = course;

        lectureUnit1 = getAttachmentUnit(lecture, 1, releaseDate);
        lectureUnit2 = getAttachmentUnit(lecture, 2, releaseDate);

        lectureUnit3 = new TextUnit();
        lectureUnit3.id = 3;
        lectureUnit3.name = 'Unit 3';
        lectureUnit3.releaseDate = releaseDate;
        lectureUnit3.lecture = lecture;

        lecture.lectureUnits = [lectureUnit1, lectureUnit2, lectureUnit3];

        TestBed.configureTestingModule({
            imports: [NgbDropdownModule, RouterTestingModule],
            declarations: [
                CourseLectureDetailsComponent,
                AttachmentUnitComponent,
                ExerciseUnitComponent,
                TextUnitComponent,
                VideoUnitComponent,
                LearningGoalsPopoverComponent,
                AlertComponent,
                NotReleasedTagComponent,
                DifficultyBadgeComponent,
                IncludedInScoreBadgeComponent,
                NgbTooltip,
                NgbCollapse,
                NgbPopover,
                NgbAlert,
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(SidePanelComponent),
                MockComponent(FaIconComponent),
                MockDirective(JhiTranslateDirective),
                MockComponent(SubmissionResultStatusComponent),
            ],
            providers: [
                MockProvider(LectureService, {
                    find: () => {
                        let headers = new HttpHeaders();
                        headers = headers.set('Content-Type', 'application/json; charset=utf-8');
                        return of(new HttpResponse({ body: lecture, headers, status: 200 }));
                    },
                }),
                MockProvider(AlertService),
                { provide: FileService, useClass: MockFileService },
                { provide: Router, useValue: mockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ lectureId: '1', courseId: '1' }),
                    },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseLectureDetailsComponent);
                courseLecturesDetailsComponent = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(courseLecturesDetailsComponent).to.be.ok;
    });

    it('should display all three lecture units: 2 attachment units and 1 text unit', fakeAsync(() => {
        fixture.detectChanges();

        const attachmentUnits = debugElement.queryAll(By.css('jhi-attachment-unit'));
        const textUnits = debugElement.queryAll(By.css('jhi-text-unit'));
        expect(attachmentUnits).to.have.lengthOf(2);
        expect(textUnits).to.have.lengthOf(1);
    }));

    it('should display download PDF button', fakeAsync(() => {
        fixture.detectChanges();

        const downloadButton = debugElement.query(By.css('#downloadButton'));
        expect(downloadButton).to.exist;
        expect(courseLecturesDetailsComponent.hasPdfLectureUnit).to.be.true;
    }));

    it('should not display download PDF button', fakeAsync(() => {
        lecture.lectureUnits = [lectureUnit3];
        courseLecturesDetailsComponent.lecture = lecture;
        courseLecturesDetailsComponent.ngOnInit();
        fixture.detectChanges();

        const downloadButton = debugElement.query(By.css('#downloadButton'));
        expect(downloadButton).to.not.exist;
        expect(courseLecturesDetailsComponent.hasPdfLectureUnit).to.be.false;
    }));

    it('should download PDF file', fakeAsync(() => {
        fixture.detectChanges();

        const downloadAttachmentStub = sinon.stub(courseLecturesDetailsComponent, 'downloadMergedFiles');
        const downloadButton = debugElement.query(By.css('#downloadButton'));
        expect(downloadButton).to.exist;

        downloadButton.nativeElement.click();
        expect(downloadAttachmentStub).to.have.been.calledOnce;
    }));
});

const getAttachmentUnit = (lecture: Lecture, id: number, releaseDate: moment.Moment) => {
    const attachment = new Attachment();
    attachment.id = id;
    attachment.version = 1;
    attachment.attachmentType = AttachmentType.FILE;
    attachment.releaseDate = moment({ years: 2020, months: 3, date: 5 });
    attachment.uploadDate = moment({ years: 2020, months: 3, date: 5 });
    attachment.name = 'test';
    attachment.link = '/path/to/file/test.pdf';

    const attachmentUnit = new AttachmentUnit();
    attachmentUnit.id = id;
    attachmentUnit.name = 'Unit 1';
    attachmentUnit.releaseDate = releaseDate;
    attachmentUnit.lecture = lecture;
    attachmentUnit.attachment = attachment;
    attachment.attachmentUnit = attachmentUnit;
    return attachmentUnit;
};
