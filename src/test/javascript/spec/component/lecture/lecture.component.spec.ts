import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Lecture } from 'app/entities/lecture.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LectureComponent, LectureDateFilter } from 'app/lecture/lecture.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LectureService } from 'app/lecture/lecture.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ArtemisTestModule } from '../../test.module';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { SortDirective } from 'app/shared/sort/sort.directive';

describe('Lecture', () => {
    let lectureComponentFixture: ComponentFixture<LectureComponent>;
    let lectureComponent: LectureComponent;
    let lectureService: LectureService;
    let modalService: NgbModal;

    let pastLecture: Lecture;
    let pastLecture2: Lecture;
    let currentLecture: Lecture;
    let currentLecture2: Lecture;
    let currentLecture3: Lecture;
    let futureLecture: Lecture;
    let futureLecture2: Lecture;
    let unspecifiedLecture: Lecture;

    beforeEach(() => {
        const lastWeek = dayjs().subtract(1, 'week');
        const yesterday = dayjs().subtract(1, 'day');
        const tomorrow = dayjs().add(1, 'day');
        const nextWeek = dayjs().add(1, 'week');

        pastLecture = new Lecture();
        pastLecture.id = 6;
        pastLecture.endDate = yesterday;
        pastLecture.title = 'introduction to Computer Science';

        pastLecture2 = new Lecture();
        pastLecture2.id = 7;
        pastLecture2.startDate = lastWeek;
        pastLecture2.endDate = yesterday;
        pastLecture2.title = 'Data Structures';

        currentLecture = new Lecture();
        currentLecture.id = 4;
        currentLecture.startDate = yesterday;
        currentLecture.endDate = tomorrow;
        currentLecture.title = 'Algorithms';

        currentLecture2 = new Lecture();
        currentLecture2.id = 5;
        currentLecture2.startDate = yesterday;
        currentLecture2.title = 'operating Systems';

        currentLecture3 = new Lecture();
        currentLecture3.id = 3;
        currentLecture3.endDate = tomorrow;
        currentLecture3.title = 'computer Networks';

        futureLecture = new Lecture();
        futureLecture.id = 2;
        futureLecture.startDate = tomorrow;
        futureLecture.title = 'Database Systems';

        futureLecture2 = new Lecture();
        futureLecture2.id = 8;
        futureLecture2.startDate = tomorrow;
        futureLecture2.endDate = nextWeek;
        futureLecture2.title = 'Artificial Intelligence';

        unspecifiedLecture = new Lecture();
        unspecifiedLecture.id = 1;
        unspecifiedLecture.title = 'machine Learning';

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                LectureComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(DocumentationButtonComponent),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                        },
                    },
                },
                MockProvider(LectureService, {
                    findAllByCourseId: () => {
                        return of(
                            new HttpResponse({
                                body: [pastLecture, pastLecture2, currentLecture, currentLecture2, currentLecture3, futureLecture, futureLecture2, unspecifiedLecture],
                                status: 200,
                            }),
                        );
                    },
                    import: (courseId, lectureId) => {
                        return of(
                            new HttpResponse({
                                body: { id: lectureId } as Lecture,
                                status: 200,
                            }),
                        );
                    },
                    delete: () => {
                        return of(new HttpResponse({ status: 200 }));
                    },
                }),
            ],
        })
            .compileComponents()
            .then(() => {
                lectureComponentFixture = TestBed.createComponent(LectureComponent);
                lectureComponent = lectureComponentFixture.componentInstance;
                lectureService = TestBed.inject(LectureService);
                modalService = TestBed.inject(NgbModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should fetch lectures when initialized', () => {
        const findAllSpy = jest.spyOn(lectureService, 'findAllByCourseId');

        lectureComponentFixture.detectChanges();

        expect(findAllSpy).toHaveBeenCalledOnce();
        expect(findAllSpy).toHaveBeenCalledWith(1);
        expect(lectureComponent.lectures).toBeArrayOfSize(8);
    });

    it('should delete lecture', () => {
        const deleteSpy = jest.spyOn(lectureService, 'delete');

        lectureComponentFixture.detectChanges();
        lectureComponent.deleteLecture(pastLecture.id!);

        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith(pastLecture.id!);
        expect(lectureComponent.lectures).toBeArrayOfSize(7);
        expect(lectureComponent.lectures).not.toContain(pastLecture);
        expect(lectureComponent.filteredLectures).toEqual(lectureComponent.lectures);
    });

    it('should import lecture', async () => {
        const promise = new Promise((resolve) => {
            resolve({ id: 123 } as Lecture);
        });
        const openSpy = jest.spyOn(modalService, 'open').mockReturnValue({ result: promise } as NgbModalRef);
        const importSpy = jest.spyOn(lectureService, 'import');

        lectureComponentFixture.detectChanges();
        lectureComponent.openImportModal();

        expect(openSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledWith(LectureImportComponent, { size: 'lg', backdrop: 'static' });

        await expect(promise)
            .toResolve()
            .then(() => {
                expect(importSpy).toHaveBeenCalledOnce();
                expect(importSpy).toHaveBeenCalledWith(1, 123);
                expect(lectureComponent.lectures).toBeArrayOfSize(9);
            });
    });

    it('should show all lectures sorted', () => {
        lectureComponentFixture.detectChanges();

        // No filters selected
        lectureComponent.toggleFilters([]);

        const filteredLectures = lectureComponent.filteredLectures;
        expect(lectureComponent.filteredLectures).toContainAllValues(lectureComponent.lectures);
        expect(lectureComponent.filteredLectures.map((lecture) => lecture.id)).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);

        // Apply all filters
        lectureComponent.toggleFilters([LectureDateFilter.PAST, LectureDateFilter.CURRENT, LectureDateFilter.FUTURE, LectureDateFilter.UNSPECIFIED]);
        expect(lectureComponent.activeFilters.size).toBe(4);
        expect(lectureComponent.filteredLectures).toContainAllValues(lectureComponent.lectures);

        // Check that no filters is equal to all filters
        expect(lectureComponent.filteredLectures).toEqual(filteredLectures);
    });

    it('should filter for past lectures', () => {
        lectureComponentFixture.detectChanges();
        lectureComponent.toggleFilters([LectureDateFilter.PAST]);
        expect(lectureComponent.filteredLectures).toBeArrayOfSize(2);
        expect(lectureComponent.filteredLectures).toContainAllValues([pastLecture, pastLecture2]);
    });

    it('should filter for current lectures', () => {
        lectureComponentFixture.detectChanges();
        lectureComponent.toggleFilters([LectureDateFilter.CURRENT]);
        expect(lectureComponent.filteredLectures).toBeArrayOfSize(3);
        expect(lectureComponent.filteredLectures).toContainAllValues([currentLecture, currentLecture2, currentLecture3]);
    });

    it('should filter for future lectures', () => {
        lectureComponentFixture.detectChanges();
        lectureComponent.toggleFilters([LectureDateFilter.FUTURE]);
        expect(lectureComponent.filteredLectures).toBeArrayOfSize(2);
        expect(lectureComponent.filteredLectures).toContainAllValues([futureLecture, futureLecture2]);
    });

    it('should filter for lectures without dates', () => {
        lectureComponentFixture.detectChanges();
        lectureComponent.toggleFilters([LectureDateFilter.UNSPECIFIED]);
        expect(lectureComponent.filteredLectures).toBeArrayOfSize(1);
        expect(lectureComponent.filteredLectures).toContainEqual(unspecifiedLecture);
    });

    it.each([
        { predicate: 'id', ascending: false, expected: [8, 7, 6, 5, 4, 3, 2, 1] },
        { predicate: 'title', ascending: true, expected: [4, 8, 3, 7, 2, 6, 1, 5] },
    ])('should sort rows', ({ predicate, ascending, expected }) => {
        lectureComponent.filteredLectures = [pastLecture, pastLecture2, currentLecture, currentLecture2, currentLecture3, futureLecture, futureLecture2, unspecifiedLecture];
        lectureComponent.predicate = predicate;
        lectureComponent.ascending = ascending;
        lectureComponent.sortRows();
        expect(lectureComponent.filteredLectures.map((lecture) => lecture.id)).toEqual(expected);
    });
});
