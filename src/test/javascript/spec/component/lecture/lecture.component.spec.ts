import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Lecture } from 'app/entities/lecture.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LectureComponent, LectureDateFilter } from 'app/lecture/lecture.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LectureService } from 'app/lecture/lecture.service';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ArtemisTestModule } from '../../test.module';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';

describe('Lecture', () => {
    let lectureComponentFixture: ComponentFixture<LectureComponent>;
    let lectureComponent: LectureComponent;
    let lectureService: LectureService;
    let modalService: NgbModal;

    let lecture1: Lecture;
    let lecture2: Lecture;
    let lecture3: Lecture;
    let lecture4: Lecture;

    beforeEach(() => {
        const tomorrow = dayjs().add(1, 'day');
        const yesterday = dayjs().subtract(1, 'day');

        lecture1 = new Lecture();
        lecture1.id = 1;
        lecture1.endDate = yesterday;

        lecture2 = new Lecture();
        lecture2.id = 2;
        lecture2.startDate = yesterday;
        lecture2.endDate = tomorrow;

        lecture3 = new Lecture();
        lecture3.id = 3;
        lecture3.startDate = tomorrow;

        lecture4 = new Lecture();
        lecture4.id = 4;

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                LectureComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
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
                                body: [lecture2, lecture3, lecture1, lecture4],
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

    it('should initialize', () => {
        const findAllSpy = jest.spyOn(lectureService, 'findAllByCourseId');

        lectureComponentFixture.detectChanges();
        expect(lectureComponent).not.toBe(null);

        expect(findAllSpy).toHaveBeenCalledOnce();
        expect(findAllSpy).toHaveBeenCalledWith(1);
        expect(lectureComponent.lectures).toBeArrayOfSize(4);
    });

    it('should delete lecture', () => {
        const deleteSpy = jest.spyOn(lectureService, 'delete');

        lectureComponentFixture.detectChanges();
        lectureComponent.deleteLecture(lecture1.id!);

        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith(1);
        expect(lectureComponent.lectures).toBeArrayOfSize(3);
        expect(lectureComponent.lectures).not.toContain(lecture1);
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
                expect(lectureComponent.lectures).toBeArrayOfSize(5);
            });
    });

    it('should show all lectures sorted', () => {
        lectureComponentFixture.detectChanges();

        // No filters selected
        lectureComponent.toggleFilters([]);

        const filteredLectures = lectureComponent.filteredLectures;
        expect(lectureComponent.filteredLectures).toContainAllValues(lectureComponent.lectures);
        expect(lectureComponent.filteredLectures.map((lecture) => lecture.id)).toEqual([1, 2, 3, 4]);

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
        expect(lectureComponent.filteredLectures).toContainEqual(lecture1);
    });

    it('should filter for current lectures', () => {
        lectureComponentFixture.detectChanges();
        lectureComponent.toggleFilters([LectureDateFilter.CURRENT]);
        expect(lectureComponent.filteredLectures).toContainEqual(lecture2);
    });

    it('should filter for past lectures', () => {
        lectureComponentFixture.detectChanges();
        lectureComponent.toggleFilters([LectureDateFilter.FUTURE]);
        expect(lectureComponent.filteredLectures).toContainEqual(lecture3);
    });

    it('should filter for lectures without dates', () => {
        lectureComponentFixture.detectChanges();
        lectureComponent.toggleFilters([LectureDateFilter.UNSPECIFIED]);
        expect(lectureComponent.filteredLectures).toContain(lecture4);
    });
});
