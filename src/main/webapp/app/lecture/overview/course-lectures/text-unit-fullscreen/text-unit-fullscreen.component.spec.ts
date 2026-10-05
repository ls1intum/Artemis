import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockScienceService } from 'test/helpers/mocks/service/mock-science-service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ScienceService } from 'app/foundation/science/science.service';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { TextUnitFullscreenComponent } from 'app/lecture/overview/course-lectures/text-unit-fullscreen/text-unit-fullscreen.component';

describe('TextUnitFullscreenComponent', () => {
    let fixture: ComponentFixture<TextUnitFullscreenComponent>;
    let lectureService: LectureService;
    let scienceService: ScienceService;
    let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

    const textUnit = { id: 7, type: LectureUnitType.TEXT, name: 'Isolated text', content: '# Sample Markdown' } as TextUnit;
    const lecture = { id: 5, title: 'Lecture', lectureUnits: [{ id: 3, type: LectureUnitType.ONLINE, name: 'Other' }, textUnit] } as Lecture;

    const host = () => fixture.nativeElement as HTMLElement;
    const byTestId = (id: string) => host().querySelector(`[data-testid="${id}"]`);

    beforeEach(async () => {
        paramMap$ = new BehaviorSubject(convertToParamMap({ lectureId: '5', unitId: '7' }));

        await TestBed.configureTestingModule({
            imports: [TextUnitFullscreenComponent],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ScienceService, useClass: MockScienceService },
                { provide: AlertService, useValue: { error: vi.fn() } },
                MockProvider(LectureService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: paramMap$,
                        // the course id is a parameter of a route above the one this component is rendered on
                        pathFromRoot: [{ snapshot: { paramMap: convertToParamMap({ courseId: '42' }) } }, { snapshot: { paramMap: convertToParamMap({}) } }],
                    },
                },
            ],
        }).compileComponents();

        lectureService = TestBed.inject(LectureService);
        scienceService = TestBed.inject(ScienceService);
        vi.spyOn(lectureService, 'findWithDetails').mockReturnValue(of(new HttpResponse({ body: lecture })));
        vi.spyOn(scienceService, 'logEvent');

        fixture = TestBed.createComponent(TextUnitFullscreenComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render the content of the requested text unit', () => {
        fixture.detectChanges();

        expect(lectureService.findWithDetails).toHaveBeenCalledExactlyOnceWith(5);
        expect(byTestId('text-unit-fullscreen-title')?.textContent).toContain('Isolated text');
        expect(byTestId('text-unit-fullscreen-content')?.innerHTML).toBe('<h1>Sample Markdown</h1>');
        expect(byTestId('text-unit-fullscreen-not-found')).toBeNull();
    });

    it('should link back to the lecture it belongs to', () => {
        fixture.detectChanges();

        const back = byTestId('text-unit-fullscreen-back-button');
        // the unit id lets the lecture page bring the student back to the unit
        expect(back?.getAttribute('href')).toBe('/courses/42/lectures/5?unit=7');
    });

    it('should log that the unit was opened', () => {
        fixture.detectChanges();

        expect(scienceService.logEvent).toHaveBeenCalledExactlyOnceWith(ScienceEventType.LECTURE__OPEN_UNIT, textUnit.id);
    });

    it('should show a message when the lecture has no text unit with that id', () => {
        paramMap$.next(convertToParamMap({ lectureId: '5', unitId: '3' }));

        fixture.detectChanges();

        expect(byTestId('text-unit-fullscreen-not-found')).not.toBeNull();
        expect(byTestId('text-unit-fullscreen-content')).toBeNull();
        expect(scienceService.logEvent).not.toHaveBeenCalled();
    });

    it('should keep loading while the request for the newly requested unit is running', () => {
        const first = new Subject<HttpResponse<Lecture>>();
        const second = new Subject<HttpResponse<Lecture>>();
        vi.spyOn(lectureService, 'findWithDetails').mockReturnValueOnce(first).mockReturnValueOnce(second);
        fixture.detectChanges();
        expect(fixture.componentInstance.isLoading()).toBe(true);

        // switching the unit cancels the first request, whose end must not end the loading state of the second
        paramMap$.next(convertToParamMap({ lectureId: '5', unitId: '3' }));
        fixture.detectChanges();

        expect(fixture.componentInstance.isLoading()).toBe(true);
        expect(byTestId('text-unit-fullscreen-not-found')).toBeNull();

        second.next(new HttpResponse({ body: { ...lecture, lectureUnits: [{ id: 3, type: LectureUnitType.TEXT, name: 'Other', content: 'Other' } as TextUnit] } }));
        second.complete();
        fixture.detectChanges();

        expect(fixture.componentInstance.isLoading()).toBe(false);
        expect(byTestId('text-unit-fullscreen-title')?.textContent).toContain('Other');
    });

    it('should alert and stop loading when the lecture cannot be fetched', () => {
        vi.spyOn(lectureService, 'findWithDetails').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

        fixture.detectChanges();

        expect(TestBed.inject(AlertService).error).toHaveBeenCalledWith('error.http.403');
        expect(fixture.componentInstance.isLoading()).toBe(false);
        expect(byTestId('text-unit-fullscreen-content')).toBeNull();
    });
});
