import { DebugElement, ElementRef, signal } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockInstance, MockPipe, MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';
import { CourseLectureDetailsComponent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { AttachmentVideoUnitComponent } from 'app/lecture/overview/course-lectures/attachment-video-unit/attachment-video-unit.component';
import { ExerciseUnitComponent } from 'app/lecture/overview/course-lectures/exercise-unit/exercise-unit.component';
import { TextUnitComponent } from 'app/lecture/overview/course-lectures/text-unit/text-unit.component';
import { CompetenciesPopoverComponent } from 'app/atlas/shared/competencies-popover/competencies-popover.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { SubmissionResultStatusComponent } from 'app/core/course/overview/submission-result-status/submission-result-status.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/core/course/overview/exercise-details/student-actions/exercise-details-student-actions.component';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag/not-released-tag.component';
import { DifficultyBadgeComponent } from 'app/exercise/exercise-headers/difficulty-badge/difficulty-badge.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { CourseExerciseRowComponent } from 'app/core/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { MockFileService } from 'test/helpers/mocks/service/mock-file.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { ScienceService } from 'app/shared/science/science.service';
import * as DownloadUtils from 'app/shared/util/download.util';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { OnlineUnitComponent } from 'app/lecture/overview/course-lectures/online-unit/online-unit.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NgbCollapse, NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { FileService } from 'app/shared/service/file.service';
import { InformationBoxComponent } from 'app/shared/information-box/information-box.component';

describe('CourseLectureDetailsComponent', () => {
    let fixture: ComponentFixture<CourseLectureDetailsComponent>;
    let courseLecturesDetailsComponent: CourseLectureDetailsComponent;
    let lecture: Lecture;
    let course: Course;
    let lectureUnit1: AttachmentVideoUnit;
    let lectureUnit2: AttachmentVideoUnit;
    let lectureUnit3: TextUnit;
    let debugElement: DebugElement;
    let lectureService: LectureService;

    MockInstance(DiscussionSectionComponent, 'content', signal(new ElementRef(document.createElement('div'))));
    MockInstance(DiscussionSectionComponent, 'messages', signal([new ElementRef(document.createElement('div'))]));
    // @ts-ignore
    MockInstance(DiscussionSectionComponent, 'postCreateEditModal', signal(new ElementRef(document.createElement('div'))));

    beforeEach(async () => {
        const releaseDate = dayjs('18-03-2020 13:30', 'DD-MM-YYYY HH:mm');
        const endDate = dayjs('18-03-2020 15:30', 'DD-MM-YYYY HH:mm');

        course = new Course();
        course.id = 456;
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;

        lecture = new Lecture();
        lecture.id = 1;
        lecture.startDate = releaseDate;
        lecture.endDate = endDate;
        lecture.description = 'Test description';
        lecture.title = 'Test lecture';
        lecture.course = course;

        lectureUnit1 = getAttachmentVideoUnit(lecture, 1, releaseDate);
        lectureUnit2 = getAttachmentVideoUnit(lecture, 2, releaseDate);

        lectureUnit3 = new TextUnit();
        lectureUnit3.id = 3;
        lectureUnit3.name = 'Unit 3';
        lectureUnit3.releaseDate = releaseDate;
        lectureUnit3.lecture = lecture;
        lectureUnit3.visibleToStudents = true;

        lecture.lectureUnits = [lectureUnit1, lectureUnit2, lectureUnit3];

        let headers = new HttpHeaders();
        headers = headers.set('Content-Type', 'application/json; charset=utf-8');
        const response = of(new HttpResponse({ body: lecture, headers, status: 200 }));

        await TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip), MockDirective(NgbCollapse), MockDirective(NgbPopover), FaIconComponent],
            declarations: [
                CourseLectureDetailsComponent,
                MockComponent(AttachmentVideoUnitComponent),
                MockComponent(ExerciseUnitComponent),
                MockComponent(TextUnitComponent),
                MockComponent(OnlineUnitComponent),
                CompetenciesPopoverComponent,
                NotReleasedTagComponent,
                DifficultyBadgeComponent,
                IncludedInScoreBadgeComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(SidePanelComponent),
                MockDirective(TranslateDirective),
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(DiscussionSectionComponent),
                MockComponent(InformationBoxComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(LectureService, {
                    find: () => {
                        return response;
                    },
                    findWithDetails: () => {
                        return response;
                    },
                }),
                MockProvider(LectureUnitService),
                MockProvider(AlertService),
                { provide: FileService, useClass: MockFileService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ lectureId: '1' }),
                        parent: {
                            parent: {
                                params: of({ courseId: '1' }),
                            },
                        },
                    },
                },
                MockProvider(Router),
                MockProvider(ScienceService),
            ],
        }).compileComponents();

        lectureService = TestBed.inject(LectureService);
        jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(response);
        jest.spyOn(lectureService, 'find').mockReturnValue(response);

        fixture = TestBed.createComponent(CourseLectureDetailsComponent);
        courseLecturesDetailsComponent = fixture.componentInstance;
        debugElement = fixture.debugElement;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.changeDetectorRef.detectChanges();
        expect(courseLecturesDetailsComponent).not.toBeNull();
        courseLecturesDetailsComponent.ngOnDestroy();
    });

    it('should render information boxes for lecture start/end date', fakeAsync(() => {
        fixture.changeDetectorRef.detectChanges();

        const boxes = debugElement.queryAll(By.css('jhi-information-box'));
        expect(boxes).toHaveLength(2);
    }));

    it('should display all three lecture units: 2 attachment video units and 1 text unit', fakeAsync(() => {
        fixture.changeDetectorRef.detectChanges();

        const attachmentVideoUnits = debugElement.queryAll(By.css('jhi-attachment-video-unit'));
        const textUnits = debugElement.queryAll(By.css('jhi-text-unit'));
        expect(attachmentVideoUnits).toHaveLength(2);
        expect(textUnits).toHaveLength(1);
    }));

    it('should display download PDF button', fakeAsync(() => {
        fixture.changeDetectorRef.detectChanges();

        const downloadButton = debugElement.query(By.css('#downloadButton'));
        expect(downloadButton).not.toBeNull();
        expect(courseLecturesDetailsComponent.hasPdfLectureUnit).toBeTrue();
    }));

    it('should not display download PDF button', fakeAsync(() => {
        lecture.lectureUnits = [lectureUnit3];
        courseLecturesDetailsComponent.lecture = lecture;
        courseLecturesDetailsComponent.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        const downloadButton = debugElement.query(By.css('#downloadButton'));
        expect(downloadButton).toBeNull();
        expect(courseLecturesDetailsComponent.hasPdfLectureUnit).toBeFalse();
    }));

    it('should not display manage button when user is only tutor', fakeAsync(() => {
        lecture.course!.isAtLeastTutor = true;
        fixture.changeDetectorRef.detectChanges();

        const manageLectureButton = debugElement.query(By.css('#manageLectureButton'));
        expect(manageLectureButton).toBeNull();
    }));

    it('should display manage button when user is at least editor', fakeAsync(() => {
        lecture.course!.isAtLeastEditor = true;
        fixture.changeDetectorRef.detectChanges();

        const manageLectureButton = debugElement.query(By.css('#manageLectureButton'));
        expect(manageLectureButton).not.toBeNull();
    }));

    it('should not display manage button when user is a student', fakeAsync(() => {
        lecture.course!.isAtLeastTutor = false;
        fixture.changeDetectorRef.detectChanges();

        const manageLectureButton = debugElement.query(By.css('#manageLectureButton'));
        expect(manageLectureButton).toBeNull();
    }));

    it('should redirect to lecture management', fakeAsync(() => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        fixture.changeDetectorRef.detectChanges();

        courseLecturesDetailsComponent.redirectToLectureManagement();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 456, 'lectures', 1]);
    }));

    it('should check attachment release date', fakeAsync(() => {
        const attachment = getAttachmentVideoUnit(lecture, 1, dayjs().add(1, 'day')).attachment!;

        expect(courseLecturesDetailsComponent.attachmentNotReleased(attachment)).toBeTrue();

        attachment.releaseDate = dayjs().subtract(1, 'day');
        expect(courseLecturesDetailsComponent.attachmentNotReleased(attachment)).toBeFalse();

        attachment.releaseDate = undefined;
        expect(courseLecturesDetailsComponent.attachmentNotReleased(attachment)).toBeFalse();
    }));

    it('should get the attachment extension', fakeAsync(() => {
        const attachment = getAttachmentVideoUnit(lecture, 1, dayjs()).attachment!;

        expect(courseLecturesDetailsComponent.attachmentExtension(attachment)).toBe('pdf');

        attachment.link = '/path/to/file/test.test.docx';
        expect(courseLecturesDetailsComponent.attachmentExtension(attachment)).toBe('docx');

        attachment.link = undefined;
        expect(courseLecturesDetailsComponent.attachmentExtension(attachment)).toBe('N/A');
    }));

    it('should show discussion section when communication is enabled', fakeAsync(() => {
        fixture.changeDetectorRef.detectChanges();

        const discussionSection = fixture.nativeElement.querySelector('jhi-discussion-section');
        expect(discussionSection).toBeTruthy();
    }));

    it('should not show discussion section when communication is disabled', fakeAsync(() => {
        const lecture = {
            ...lectureUnit3.lecture,
            course: { courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED },
        };
        const response = of(new HttpResponse({ body: { ...lecture }, status: 200 }));
        jest.spyOn(TestBed.inject(LectureService), 'findWithDetails').mockReturnValue(response);

        fixture.changeDetectorRef.detectChanges();

        const discussionSection = fixture.nativeElement.querySelector('jhi-discussion-section');
        expect(discussionSection).toBeFalsy();
    }));

    it('should download file for attachment', fakeAsync(() => {
        const fileService = TestBed.inject(FileService);
        const downloadFileSpy = jest.spyOn(fileService, 'downloadFileByAttachmentName');
        const attachment = getAttachmentVideoUnit(lecture, 1, dayjs()).attachment!;

        courseLecturesDetailsComponent.downloadAttachment(attachment.link, attachment.name);

        expect(downloadFileSpy).toHaveBeenCalledOnce();
        expect(downloadFileSpy).toHaveBeenCalledWith(attachment.link, attachment.name);
        expect(courseLecturesDetailsComponent.isDownloadingLink).toBeUndefined();
    }));

    it('should download PDF file', fakeAsync(() => {
        fixture.changeDetectorRef.detectChanges();

        const downloadAttachmentStub = jest.spyOn(courseLecturesDetailsComponent, 'downloadMergedFiles');
        const downloadStreamStub = jest.spyOn(DownloadUtils, 'downloadStream').mockImplementation(() => {});
        const downloadButton = debugElement.query(By.css('#downloadButton'));
        expect(downloadButton).not.toBeNull();

        downloadButton.nativeElement.click();
        tick();
        expect(downloadAttachmentStub).toHaveBeenCalledOnce();
        expect(downloadStreamStub).toHaveBeenCalledExactlyOnceWith(null, 'application/pdf', 'Test lecture');
    }));

    it('should set lecture unit as completed', fakeAsync(() => {
        const lectureUnitService = TestBed.inject(LectureUnitService);
        const completeSpy = jest.spyOn(lectureUnitService, 'completeLectureUnit');

        courseLecturesDetailsComponent.lecture = lecture;
        courseLecturesDetailsComponent.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(lectureUnit3.completed).toBeFalsy();
        courseLecturesDetailsComponent.completeLectureUnit({ lectureUnit: lectureUnit3, completed: true });
        expect(completeSpy).toHaveBeenCalledExactlyOnceWith(lecture, { lectureUnit: lectureUnit3, completed: true });
    }));
});

const getAttachmentVideoUnit = (lecture: Lecture, id: number, releaseDate: dayjs.Dayjs) => {
    const attachment = new Attachment();
    attachment.id = id;
    attachment.version = 1;
    attachment.attachmentType = AttachmentType.FILE;
    attachment.releaseDate = releaseDate;
    attachment.uploadDate = dayjs().year(2020).month(3).date(5);
    attachment.name = 'test';
    attachment.link = '/path/to/file/test.pdf';

    const attachmentVideoUnit = new AttachmentVideoUnit();
    attachmentVideoUnit.id = id;
    attachmentVideoUnit.name = 'Unit 1';
    attachmentVideoUnit.releaseDate = attachment.releaseDate;
    attachmentVideoUnit.lecture = lecture;
    attachmentVideoUnit.attachment = attachment;
    attachment.attachmentVideoUnit = attachmentVideoUnit;
    return attachmentVideoUnit;
};
