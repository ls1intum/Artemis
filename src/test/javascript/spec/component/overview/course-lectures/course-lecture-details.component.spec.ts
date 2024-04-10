import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/core/util/alert.service';
import { BehaviorSubject, of } from 'rxjs';
import { CourseLectureDetailsComponent } from '../../../../../../main/webapp/app/overview/course-lectures/course-lecture-details.component';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { CompetenciesPopoverComponent } from 'app/course/competencies/competencies-popover/competencies-popover.component';
import { AlertOverlayComponent } from 'app/shared/alert/alert-overlay.component';
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
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { NgbCollapse, NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ScienceService } from 'app/shared/science/science.service';
import * as DownloadUtils from 'app/shared/util/download.util';
import { ProfileService } from '../../../../../../main/webapp/app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from '../../../../../../main/webapp/app/shared/layouts/profiles/profile-info.model';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';

describe('CourseLectureDetailsComponent', () => {
    let fixture: ComponentFixture<CourseLectureDetailsComponent>;
    let courseLecturesDetailsComponent: CourseLectureDetailsComponent;
    let lecture: Lecture;
    let lectureUnit1: AttachmentUnit;
    let lectureUnit2: AttachmentUnit;
    let lectureUnit3: TextUnit;
    let debugElement: DebugElement;
    let profileService: ProfileService;

    let getProfileInfoMock: jest.SpyInstance;

    beforeEach(() => {
        const releaseDate = dayjs('18-03-2020', 'DD-MM-YYYY');

        const course = new Course();
        course.id = 456;

        lecture = new Lecture();
        lecture.id = 1;
        lecture.startDate = releaseDate;
        lecture.description = 'Test description';
        lecture.title = 'Test lecture';
        lecture.course = course;

        lectureUnit1 = getAttachmentUnit(lecture, 1, releaseDate);
        lectureUnit2 = getAttachmentUnit(lecture, 2, releaseDate);

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

        TestBed.configureTestingModule({
            imports: [RouterTestingModule, MockDirective(NgbTooltip), MockDirective(NgbCollapse), MockDirective(NgbPopover)],
            declarations: [
                CourseLectureDetailsComponent,
                AttachmentUnitComponent,
                ExerciseUnitComponent,
                TextUnitComponent,
                VideoUnitComponent,
                CompetenciesPopoverComponent,
                AlertOverlayComponent,
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
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
                MockComponent(SubmissionResultStatusComponent),
            ],
            providers: [
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
                        params: of({ lectureId: '1', courseId: '1' }),
                    },
                },
                MockProvider(Router),
                MockProvider(ScienceService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseLectureDetailsComponent);
                courseLecturesDetailsComponent = fixture.componentInstance;
                debugElement = fixture.debugElement;

                // mock profileService
                profileService = fixture.debugElement.injector.get(ProfileService);
                getProfileInfoMock = jest.spyOn(profileService, 'getProfileInfo');
                const profileInfo = { inProduction: false } as ProfileInfo;
                const profileInfoSubject = new BehaviorSubject<ProfileInfo | null>(profileInfo);
                getProfileInfoMock.mockReturnValue(profileInfoSubject);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(courseLecturesDetailsComponent).not.toBeNull();
        courseLecturesDetailsComponent.ngOnDestroy();
    });

    it('should display all three lecture units: 2 attachment units and 1 text unit', fakeAsync(() => {
        fixture.detectChanges();

        const attachmentUnits = debugElement.queryAll(By.css('jhi-attachment-unit'));
        const textUnits = debugElement.queryAll(By.css('jhi-text-unit'));
        expect(attachmentUnits).toHaveLength(2);
        expect(textUnits).toHaveLength(1);
    }));

    it('should display download PDF button', fakeAsync(() => {
        fixture.detectChanges();

        const downloadButton = debugElement.query(By.css('#downloadButton'));
        expect(downloadButton).not.toBeNull();
        expect(courseLecturesDetailsComponent.hasPdfLectureUnit).toBeTrue();
    }));

    it('should not display download PDF button', fakeAsync(() => {
        lecture.lectureUnits = [lectureUnit3];
        courseLecturesDetailsComponent.lecture = lecture;
        courseLecturesDetailsComponent.ngOnInit();
        fixture.detectChanges();

        const downloadButton = debugElement.query(By.css('#downloadButton'));
        expect(downloadButton).toBeNull();
        expect(courseLecturesDetailsComponent.hasPdfLectureUnit).toBeFalse();
    }));

    it('should not display manage button when user is only tutor', fakeAsync(() => {
        lecture.course!.isAtLeastTutor = true;
        fixture.detectChanges();

        const manageLectureButton = debugElement.query(By.css('#manageLectureButton'));
        expect(manageLectureButton).toBeNull();
    }));

    it('should display manage button when user is at least editor', fakeAsync(() => {
        lecture.course!.isAtLeastEditor = true;
        fixture.detectChanges();

        const manageLectureButton = debugElement.query(By.css('#manageLectureButton'));
        expect(manageLectureButton).not.toBeNull();
    }));

    it('should not display manage button when user is a student', fakeAsync(() => {
        lecture.course!.isAtLeastTutor = false;
        fixture.detectChanges();

        const manageLectureButton = debugElement.query(By.css('#manageLectureButton'));
        expect(manageLectureButton).toBeNull();
    }));

    it('should redirect to lecture management', fakeAsync(() => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        fixture.detectChanges();

        courseLecturesDetailsComponent.redirectToLectureManagement();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 456, 'lectures', 1]);
    }));

    it('should check attachment release date', fakeAsync(() => {
        const attachment = getAttachmentUnit(lecture, 1, dayjs().add(1, 'day')).attachment!;

        expect(courseLecturesDetailsComponent.attachmentNotReleased(attachment)).toBeTrue();

        attachment.releaseDate = dayjs().subtract(1, 'day');
        expect(courseLecturesDetailsComponent.attachmentNotReleased(attachment)).toBeFalse();

        attachment.releaseDate = undefined;
        expect(courseLecturesDetailsComponent.attachmentNotReleased(attachment)).toBeFalse();
    }));

    it('should get the attachment extension', fakeAsync(() => {
        const attachment = getAttachmentUnit(lecture, 1, dayjs()).attachment!;

        expect(courseLecturesDetailsComponent.attachmentExtension(attachment)).toBe('pdf');

        attachment.link = '/path/to/file/test.test.docx';
        expect(courseLecturesDetailsComponent.attachmentExtension(attachment)).toBe('docx');

        attachment.link = undefined;
        expect(courseLecturesDetailsComponent.attachmentExtension(attachment)).toBe('N/A');
    }));

    it('should download file for attachment', fakeAsync(() => {
        const fileService = TestBed.inject(FileService);
        const downloadFileSpy = jest.spyOn(fileService, 'downloadFile');
        const attachment = getAttachmentUnit(lecture, 1, dayjs()).attachment!;

        courseLecturesDetailsComponent.downloadAttachment(attachment.link);

        expect(downloadFileSpy).toHaveBeenCalledOnce();
        expect(downloadFileSpy).toHaveBeenCalledWith(attachment.link);
        expect(courseLecturesDetailsComponent.isDownloadingLink).toBeUndefined();
    }));

    it('should download PDF file', fakeAsync(() => {
        fixture.detectChanges();

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
        fixture.detectChanges();

        expect(lectureUnit3.completed).toBeFalsy();
        courseLecturesDetailsComponent.completeLectureUnit({ lectureUnit: lectureUnit3, completed: true });
        expect(completeSpy).toHaveBeenCalledExactlyOnceWith(lecture, { lectureUnit: lectureUnit3, completed: true });
    }));
});

const getAttachmentUnit = (lecture: Lecture, id: number, releaseDate: dayjs.Dayjs) => {
    const attachment = new Attachment();
    attachment.id = id;
    attachment.version = 1;
    attachment.attachmentType = AttachmentType.FILE;
    attachment.releaseDate = releaseDate;
    attachment.uploadDate = dayjs().year(2020).month(3).date(5);
    attachment.name = 'test';
    attachment.link = '/path/to/file/test.pdf';

    const attachmentUnit = new AttachmentUnit();
    attachmentUnit.id = id;
    attachmentUnit.name = 'Unit 1';
    attachmentUnit.releaseDate = attachment.releaseDate;
    attachmentUnit.lecture = lecture;
    attachmentUnit.attachment = attachment;
    attachment.attachmentUnit = attachmentUnit;
    return attachmentUnit;
};
