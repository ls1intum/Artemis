import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { faFile, faPencilAlt, faPuzzlePiece } from '@fortawesome/free-solid-svg-icons';
import { of } from 'rxjs';
import { LectureDetailComponent } from 'app/lecture/manage/lecture-detail/lecture-detail.component';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

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
    setupTestBed({ zoneless: true });

    let component: LectureDetailComponent;
    let fixture: ComponentFixture<LectureDetailComponent>;
    let mockActivatedRoute: any;

    beforeEach(async () => {
        mockActivatedRoute = {
            data: of({ lecture: new Lecture() }),
        };

        await TestBed.configureTestingModule({
            imports: [LectureDetailComponent, MockPipe(ArtemisDatePipe), MockModule(RouterModule), DetailOverviewListComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: mockActivatedRoute },
                MockProvider(SessionStorageService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .overrideTemplate(DetailOverviewListComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(LectureDetailComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize lecture when ngOnInit is called', () => {
        mockActivatedRoute.data = of({ lecture: mockLecture });

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
