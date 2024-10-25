import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, RouterModule } from '@angular/router';
import dayjs from 'dayjs/esm';
import { faFile, faPencilAlt, faPuzzlePiece } from '@fortawesome/free-solid-svg-icons';
import { of, throwError } from 'rxjs';
import { LectureDetailComponent } from 'app/lecture/lecture-detail.component';
import { Lecture } from 'app/entities/lecture.model';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { DetailOverviewListComponent } from 'app/detail-overview-list/detail-overview-list.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LectureService } from 'app/lecture/lecture.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_IRIS } from 'app/app.constants';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { IrisCourseSettings } from 'app/entities/iris/settings/iris-settings.model';

const mockLecture = {
    title: 'Test Lecture',
    description: 'Test Description',
    visibleDate: dayjs(),
    startDate: dayjs(),
    endDate: dayjs(),
    course: {
        id: 32,
        title: 'Test Course',
    },
} as Lecture;

describe('LectureDetailComponent', () => {
    let component: LectureDetailComponent;
    let fixture: ComponentFixture<LectureDetailComponent>;
    let mockActivatedRoute: any;
    let lectureService: LectureService;
    let consoleSpy: jest.SpyInstance;
    let profileService: ProfileService;
    let irisSettingsService: IrisSettingsService;

    beforeEach(async () => {
        mockActivatedRoute = {
            data: of({ lecture: new Lecture() }), // Mock the ActivatedRoute data observable
        };

        await TestBed.configureTestingModule({
            declarations: [LectureDetailComponent, HtmlForMarkdownPipe, MockPipe(ArtemisDatePipe), MockModule(RouterModule), DetailOverviewListComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                MockProvider(SessionStorageService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        })
            .overrideTemplate(DetailOverviewListComponent, '')
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(LectureDetailComponent);
        component = fixture.componentInstance;
        lectureService = TestBed.inject(LectureService);
        fixture.detectChanges();
        consoleSpy = jest.spyOn(console, 'error').mockImplementation();
    });

    it('should initialize lecture when ngOnInit is called', () => {
        mockActivatedRoute.data = of({ lecture: mockLecture }); // Update the ActivatedRoute mock data

        component.ngOnInit();

        expect(component.lecture).toEqual(mockLecture);
        expect(component.detailSections).toBeDefined();
        for (const detail of component.detailSections[0].details) {
            expect(detail).toBeDefined();
        }
    });

    it('should have the correct icons initialized', () => {
        expect(component.faPencilAlt).toEqual(faPencilAlt);
        expect(component.faFile).toEqual(faFile);
        expect(component.faPuzzlePiece).toEqual(faPuzzlePiece);
    });

    it('should have correct lecture-details', () => {
        component.lecture = mockLecture;
        component.getLectureDetailSections();
        for (const section of component.detailSections) {
            expect(section.headline).toBeTruthy();
            for (const detail of section.details) {
                expect(detail).toBeTruthy();
            }
        }
    });
    it('should call the service to ingest lectures when ingestLecturesInPyris is called', () => {
        component.lecture = mockLecture;
        const ingestSpy = jest.spyOn(lectureService, 'ingestLecturesInPyris').mockImplementation(() => of(new HttpResponse<void>({ status: 200 })));
        component.ingestLectureInPyris();
        expect(ingestSpy).toHaveBeenCalledWith(mockLecture.course?.id, mockLecture.id);
        expect(ingestSpy).toHaveBeenCalledOnce();
    });
    it('should log error when error occurs', () => {
        component.lecture = mockLecture;
        jest.spyOn(lectureService, 'ingestLecturesInPyris').mockReturnValue(throwError(() => new Error('Error while ingesting')));
        component.ingestLectureInPyris();
        expect(consoleSpy).toHaveBeenCalledWith('Failed to send Ingestion request', expect.any(Error));
    });
    it('should set lectureIngestionEnabled based on service response', () => {
        component.lecture = mockLecture;
        irisSettingsService = TestBed.inject(IrisSettingsService);
        profileService = TestBed.inject(ProfileService);
        const profileInfoResponse = {
            activeProfiles: [PROFILE_IRIS],
        } as ProfileInfo;
        const irisSettingsResponse = {
            irisLectureIngestionSettings: {
                enabled: true,
            },
        } as IrisCourseSettings;
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of(profileInfoResponse));
        jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockImplementation(() => of(irisSettingsResponse));
        mockActivatedRoute.data = of({ lecture: mockLecture }); // Update the ActivatedRoute mock data
        component.ngOnInit();
        expect(irisSettingsService.getCombinedCourseSettings).toHaveBeenCalledWith(component.lecture.course?.id);
        expect(component.lectureIngestionEnabled).toBeTrue();
    });
});
