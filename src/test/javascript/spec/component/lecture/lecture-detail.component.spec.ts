import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, RouterModule } from '@angular/router';
import dayjs from 'dayjs/esm';
import { faFile, faPencilAlt, faPuzzlePiece } from '@fortawesome/free-solid-svg-icons';
import { of } from 'rxjs';
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
import { HttpClientTestingModule } from '@angular/common/http/testing';

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

    beforeEach(async () => {
        mockActivatedRoute = {
            data: of({ lecture: new Lecture() }), // Mock the ActivatedRoute data observable
        };

        await TestBed.configureTestingModule({
            declarations: [LectureDetailComponent, HtmlForMarkdownPipe, MockPipe(ArtemisDatePipe), MockModule(RouterModule), DetailOverviewListComponent, HttpClientTestingModule],
            providers: [
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
        fixture.detectChanges();
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
});
