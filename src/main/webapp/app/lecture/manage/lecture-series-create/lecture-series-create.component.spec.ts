import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LectureSeriesCreateComponent } from './lecture-series-create.component';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { Router } from '@angular/router';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import dayjs, { Dayjs } from 'dayjs/esm';
import { of, throwError } from 'rxjs';
import { Lecture, LectureSeriesCreateLectureDTO } from 'app/lecture/shared/entities/lecture.model';
import { provideNoopAnimationsForTests } from 'test/helpers/animations';

describe('LectureSeriesCreateComponent', () => {
    let fixture: ComponentFixture<LectureSeriesCreateComponent>;
    let component: LectureSeriesCreateComponent;

    let lectureServiceMock: jest.Mocked<Pick<LectureService, 'createSeries'>>;
    let alertServiceMock: jest.Mocked<Pick<AlertService, 'addErrorAlert'>>;
    let routerMock: jest.Mocked<Pick<Router, 'navigate'>>;
    let navigationUtilServiceMock: jest.Mocked<Pick<ArtemisNavigationUtilService, 'navigateBack'>>;

    const testCourseId = 42;

    beforeEach(async () => {
        lectureServiceMock = {
            createSeries: jest.fn(),
        };
        alertServiceMock = {
            addErrorAlert: jest.fn(),
        };
        navigationUtilServiceMock = { navigateBack: jest.fn() };
        routerMock = { navigate: jest.fn() };

        await TestBed.configureTestingModule({
            imports: [LectureSeriesCreateComponent, MockJhiTranslateDirective],
            providers: [
                { provide: LectureService, useValue: lectureServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useValue: routerMock },
                { provide: ArtemisNavigationUtilService, useValue: navigationUtilServiceMock },
                provideNoopAnimationsForTests(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureSeriesCreateComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('existingLectures', []);
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should set isSeriesEndDateInvalid to true when an initial lecture endDate is after or equal to seriesEndDate', async () => {
        const initialLectureEndDate = new Date('2025-01-11T00:00:00Z');
        const seriesEndDate = new Date('2025-01-10T00:00:00Z');

        component.seriesEndDate.set(seriesEndDate);
        const initialLecture = component.initialLectures()[0];
        initialLecture.endDate.set(initialLectureEndDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.isSeriesEndDateInvalid()).toBeTrue();

        initialLecture.endDate.set(seriesEndDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.isSeriesEndDateInvalid()).toBeTrue();
    });

    it('should set isSeriesEndDateInvalid to true when an initial lecture startDate is after or equal to seriesEndDate', async () => {
        const initialLectureStartDate = new Date('2025-01-11T00:00:00Z');
        const seriesEndDate = new Date('2025-01-10T00:00:00Z');

        component.seriesEndDate.set(seriesEndDate);
        const initialLecture = component.initialLectures()[0];
        initialLecture.startDate.set(initialLectureStartDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.isSeriesEndDateInvalid()).toBeTrue();

        initialLecture.startDate.set(seriesEndDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.isSeriesEndDateInvalid()).toBeTrue();
    });

    it('should set isStartDateInvalid to true when same or after seriesEndDate', async () => {
        const initialLectureStartDate = new Date('2025-01-11T00:00:00Z');
        const seriesEndDate = new Date('2025-01-10T00:00:00Z');

        component.seriesEndDate.set(seriesEndDate);
        const initialLecture = component.initialLectures()[0];
        initialLecture.startDate.set(initialLectureStartDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(initialLecture.isStartDateInvalid()).toBeTrue();

        initialLecture.startDate.set(seriesEndDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(initialLecture.isStartDateInvalid()).toBeTrue();
    });

    it('should set isStartDateInvalid to true when same or after endDate', async () => {
        const initialLectureStartDate = new Date('2025-01-11T00:00:00Z');
        const initialLectureEndDate = new Date('2025-01-10T00:00:00Z');

        const initialLecture = component.initialLectures()[0];
        initialLecture.startDate.set(initialLectureStartDate);
        initialLecture.endDate.set(initialLectureEndDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(initialLecture.isStartDateInvalid()).toBeTrue();

        initialLecture.startDate.set(initialLectureEndDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(initialLecture.isStartDateInvalid()).toBeTrue();
    });

    it('should set isEndDateInvalid to true when same or before startDate', async () => {
        const initialLectureStartDate = new Date('2025-01-11T00:00:00Z');
        const initialLectureEndDate = new Date('2025-01-10T00:00:00Z');

        const initialLecture = component.initialLectures()[0];
        initialLecture.startDate.set(initialLectureStartDate);
        initialLecture.endDate.set(initialLectureEndDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(initialLecture.isEndDateInvalid()).toBeTrue();

        initialLecture.endDate.set(initialLectureStartDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(initialLecture.isEndDateInvalid()).toBeTrue();
    });

    it('should set isEndDateInvalid to true when same or after seriesEndDate', async () => {
        const initialLectureEndDate = new Date('2025-01-11T00:00:00Z');
        const seriesEndDate = new Date('2025-01-10T00:00:00Z');

        component.seriesEndDate.set(seriesEndDate);
        const initialLecture = component.initialLectures()[0];
        initialLecture.endDate.set(initialLectureEndDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(initialLecture.isEndDateInvalid()).toBeTrue();

        initialLecture.endDate.set(seriesEndDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(initialLecture.isEndDateInvalid()).toBeTrue();
    });

    it('should set all validation flags to false when dates are in correct order', async () => {
        const initialLectureStartDate = new Date('2025-01-11T11:00:00Z');
        const initialLectureEndDate = new Date('2025-01-11T12:00:00Z');
        const seriesEndDate = new Date('2025-01-25T00:00:00Z');

        component.seriesEndDate.set(seriesEndDate);
        const initialLecture = component.initialLectures()[0];
        initialLecture.startDate.set(initialLectureStartDate);
        initialLecture.endDate.set(initialLectureEndDate);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.isSeriesEndDateInvalid()).toBeFalse();
        expect(initialLecture.isStartDateInvalid()).toBeFalse();
        expect(initialLecture.isEndDateInvalid()).toBeFalse();
    });

    it('should expose correct noDraftsGenerated based on lectureDrafts', async () => {
        expect(component.noDraftsGenerated()).toBeTrue();

        const today = dayjs();
        const seriesEndDate = today.add(30, 'day').toDate();
        const initialLectureStartDate = today.add(1, 'day').toDate();
        component.seriesEndDate.set(seriesEndDate);
        const initialLecture = component.initialLectures()[0];
        initialLecture.startDate.set(initialLectureStartDate);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.noDraftsGenerated()).toBeFalse();
    });

    it('should initialize initialLectures with one element', async () => {
        const initialLectures = component.initialLectures();
        expect(initialLectures).toHaveLength(1);

        const initialLecture = initialLectures[0];
        expect(initialLecture.id).toBeDefined();
        expect(initialLecture.startDate()).toBeUndefined();
        expect(initialLecture.endDate()).toBeUndefined();
    });

    it('should add initialLecture', async () => {
        component.addInitialLecture();
        fixture.detectChanges();
        await fixture.whenStable();

        const initialLectures = component.initialLectures();
        expect(initialLectures).toHaveLength(2);

        const firstInitialLecture = initialLectures[0];
        expect(firstInitialLecture.id).toBeDefined();
        expect(firstInitialLecture.startDate()).toBeUndefined();
        expect(firstInitialLecture.endDate()).toBeUndefined();

        const secondInitialLecture = initialLectures[1];
        expect(secondInitialLecture.id).toBeDefined();
        expect(secondInitialLecture.startDate()).toBeUndefined();
        expect(secondInitialLecture.endDate()).toBeUndefined();
    });

    it('should remove initialLecture', async () => {
        component.addInitialLecture();
        fixture.detectChanges();
        await fixture.whenStable();

        let initialLectures = component.initialLectures();
        expect(initialLectures).toHaveLength(2);
        const firstInitialLectureId = initialLectures[0].id;

        component.removeInitialLecture(initialLectures[1]);
        fixture.detectChanges();
        await fixture.whenStable();

        initialLectures = component.initialLectures();
        expect(initialLectures).toHaveLength(1);
        const firstInitialLecture = initialLectures[0];
        expect(firstInitialLecture.id).toBe(firstInitialLectureId);
    });

    it('should delete lectureDraft', async () => {
        const today = dayjs();
        const seriesEndDate = today.add(14, 'day').toDate();
        const initialLectureStartDate = today.add(3, 'day').toDate();
        component.seriesEndDate.set(seriesEndDate);
        const initialLecture = component.initialLectures()[0];
        initialLecture.startDate.set(initialLectureStartDate);
        fixture.detectChanges();
        await fixture.whenStable();

        let lectureDrafts = component.lectureDrafts();
        expect(lectureDrafts).toHaveLength(2);
        const firstDraftId = lectureDrafts[0].id;

        component.deleteLectureDraft(lectureDrafts[1]);
        fixture.detectChanges();
        await fixture.whenStable();

        lectureDrafts = component.lectureDrafts();
        expect(lectureDrafts).toHaveLength(1);
        const firstDraft = lectureDrafts[0];
        expect(firstDraft.id).toBe(firstDraftId);
    });

    it('should navigate back on cancel', async () => {
        const spy = jest.spyOn(navigationUtilServiceMock, 'navigateBack');
        fixture.componentRef.setInput('courseId', testCourseId);
        fixture.detectChanges();
        await fixture.whenStable();

        component.cancel();
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(['course-management', testCourseId, 'lectures']);
    });

    it('should generate drafts for initialLecture with only startDate', async () => {
        const initialLectures = component.initialLectures();
        expect(initialLectures).toHaveLength(1);

        const now = dayjs();
        const inThreeWeeks = now.add(2, 'weeks');
        const firstInitialLecture = initialLectures[0];
        firstInitialLecture.startDate.set(now.toDate());
        component.seriesEndDate.set(inThreeWeeks.toDate());
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.lectureDrafts()).toHaveLength(3);
    });

    it('should generate drafts for initialLecture with only endDate', async () => {
        const initialLectures = component.initialLectures();
        expect(initialLectures).toHaveLength(1);

        const now = dayjs();
        const inThreeWeeks = now.add(2, 'weeks');
        const firstInitialLecture = initialLectures[0];
        firstInitialLecture.endDate.set(now.toDate());
        component.seriesEndDate.set(inThreeWeeks.toDate());
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.lectureDrafts()).toHaveLength(3);
    });

    describe('save()', () => {
        const todayAt14 = dayjs().hour(14).minute(0).second(0).millisecond(0);
        const seriesEndDate = todayAt14.add(14, 'day').toDate();
        const firstInitialLectureStartDate = todayAt14.add(2, 'day').toDate();
        const firstInitialLectureEndDate = todayAt14.add(2, 'day').add(2, 'hour').toDate();
        const secondInitialLectureStartDate = todayAt14.add(5, 'day').toDate();
        const secondInitialLectureEndDate = todayAt14.add(5, 'day').add(2, 'hour').toDate();
        const createLecture = (id: number, title: string, startDate?: Dayjs, endDate?: Dayjs): Lecture => {
            const lecture = new Lecture();
            lecture.id = id;
            lecture.title = title;
            lecture.startDate = startDate;
            lecture.endDate = endDate;
            return lecture;
        };

        const existingLectures: Lecture[] = [createLecture(1, 'Lecture 1', todayAt14.add(3, 'day'), undefined), createLecture(2, 'Lecture 2', undefined, undefined)];

        const expectedLectureDTOsWithoutExistingLectures: LectureSeriesCreateLectureDTO[] = [
            new LectureSeriesCreateLectureDTO('Lecture 1', dayjs(firstInitialLectureStartDate), dayjs(firstInitialLectureEndDate)),
            new LectureSeriesCreateLectureDTO('Lecture 2', dayjs(secondInitialLectureStartDate), dayjs(secondInitialLectureEndDate)),
            new LectureSeriesCreateLectureDTO('Lecture 3', dayjs(firstInitialLectureStartDate).add(1, 'week'), dayjs(firstInitialLectureEndDate).add(1, 'week')),
            new LectureSeriesCreateLectureDTO('Lecture 4', dayjs(secondInitialLectureStartDate).add(1, 'week'), dayjs(secondInitialLectureEndDate).add(1, 'week')),
        ];

        const expectedLectureDTOsWithExistingLectures: LectureSeriesCreateLectureDTO[] = [
            new LectureSeriesCreateLectureDTO('Lecture 1', dayjs(firstInitialLectureStartDate), dayjs(firstInitialLectureEndDate)),
            new LectureSeriesCreateLectureDTO('Lecture 3', dayjs(secondInitialLectureStartDate), dayjs(secondInitialLectureEndDate)),
            new LectureSeriesCreateLectureDTO('Lecture 4', dayjs(firstInitialLectureStartDate).add(1, 'week'), dayjs(firstInitialLectureEndDate).add(1, 'week')),
            new LectureSeriesCreateLectureDTO('Lecture 5', dayjs(secondInitialLectureStartDate).add(1, 'week'), dayjs(secondInitialLectureEndDate).add(1, 'week')),
        ];

        let isLoadingSpy: jest.SpyInstance<void, [boolean]>;

        beforeEach(async () => {
            isLoadingSpy = jest.spyOn(component.isLoading, 'set');
            fixture.componentRef.setInput('courseId', testCourseId);
            component.addInitialLecture();
            fixture.detectChanges();
            await fixture.whenStable();

            component.seriesEndDate.set(seriesEndDate);
            const initialLectures = component.initialLectures();
            const firstInitialLecture = initialLectures[0];
            firstInitialLecture.startDate.set(firstInitialLectureStartDate);
            firstInitialLecture.endDate.set(firstInitialLectureEndDate);
            const secondInitialLecture = initialLectures[1];
            secondInitialLecture.startDate.set(secondInitialLectureStartDate);
            secondInitialLecture.endDate.set(secondInitialLectureEndDate);
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should save new lectures', async () => {
            lectureServiceMock.createSeries.mockReturnValue(of(void 0));

            component.save();
            fixture.detectChanges();
            await fixture.whenStable();

            expect(lectureServiceMock.createSeries).toHaveBeenCalledOnce();
            const [passedLectureSeriesCreateDTOs, passedCourseId] = lectureServiceMock.createSeries.mock.calls[0];
            expect(passedCourseId).toBe(testCourseId);
            expect(passedLectureSeriesCreateDTOs).toEqual(expectedLectureDTOsWithoutExistingLectures);

            expect(isLoadingSpy).toHaveBeenCalledTimes(2);
            expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
            expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
        });

        it('should add correct alert if creating lectures fails', async () => {
            fixture.componentRef.setInput('existingLectures', existingLectures);
            fixture.detectChanges();
            await fixture.whenStable();

            lectureServiceMock.createSeries.mockReturnValue(throwError(() => new Error('Creation failed')));

            component.save();
            fixture.detectChanges();
            await fixture.whenStable();

            expect(lectureServiceMock.createSeries).toHaveBeenCalledOnce();
            const [passedLectureSeriesCreateDTOs, passedLectureCreateCourseId] = lectureServiceMock.createSeries.mock.calls[0];
            expect(passedLectureCreateCourseId).toBe(testCourseId);
            expect(passedLectureSeriesCreateDTOs).toEqual(expectedLectureDTOsWithExistingLectures);
            expect(alertServiceMock.addErrorAlert).toHaveBeenCalledOnce();
            const alertStringKey = alertServiceMock.addErrorAlert.mock.calls[0][0];
            expect(alertStringKey).toBe('artemisApp.lecture.createSeries.seriesCreationError');

            expect(isLoadingSpy).toHaveBeenCalledTimes(2);
            expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
            expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
        });
    });
});
