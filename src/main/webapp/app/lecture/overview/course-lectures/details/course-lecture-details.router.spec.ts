import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { Router, Routes, provideRouter, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { EMPTY, Subject, of } from 'rxjs';
import { CourseLectureDetailsComponent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { LECTURE_DEEP_LINK_NAVIGATION_STATE } from 'app/lecture/overview/course-lectures/lecture-deep-link.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Course } from 'app/course/shared/entities/course.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ScienceService } from 'app/foundation/science/science.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FileService } from 'app/foundation/service/file.service';
import { MockFileService } from 'test/helpers/mocks/service/mock-file.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';

/**
 * The lecture page decides from the router's own ordering whether a navigation is a new deep-link request: the route
 * parameters arrive before `NavigationEnd`, a navigation to the URL already shown still ends with `NavigationEnd`
 * (the application enables `onSameUrlNavigation: 'reload'`), and the navigation is still current when it ends. The
 * component spec drives a mocked router, so none of that is exercised there. These tests use the real one.
 */
describe('CourseLectureDetailsComponent with the real router', () => {
    const routes: Routes = [{ path: 'courses/:courseId', children: [{ path: 'lectures', children: [{ path: ':lectureId', component: CourseLectureDetailsComponent }] }] }];

    const unit = (id: number) => {
        const attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.id = id;
        attachmentVideoUnit.videoSource = 'https://example.com/video.mp4';
        attachmentVideoUnit.attachment = Object.assign(new Attachment(), { link: '/path/to/slides.pdf' });
        return attachmentVideoUnit;
    };

    const lectureWith = (id: number, units: AttachmentVideoUnit[]) => {
        const course = Object.assign(new Course(), { id: 1 });
        return new HttpResponse({ body: Object.assign(new Lecture(), { id, course, lectureUnits: units }), status: 200 });
    };

    let harness: RouterTestingHarness;
    let component: CourseLectureDetailsComponent;
    let router: Router;
    let location: Location;
    let lectureTwoResponse: Subject<HttpResponse<Lecture>>;

    beforeEach(async () => {
        lectureTwoResponse = new Subject<HttpResponse<Lecture>>();

        await TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter(routes, withRouterConfig({ onSameUrlNavigation: 'reload' })),
                provideLocationMocks(),
                MockProvider(LectureService, {
                    findWithDetails: (lectureId: number) => (lectureId === 2 ? lectureTwoResponse : of(lectureWith(1, [unit(7)]))),
                }),
                MockProvider(LectureUnitService),
                MockProvider(AlertService),
                MockProvider(ScienceService),
                MockProvider(IrisSettingsService),
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: FileService, useClass: MockFileService },
                { provide: IrisChatService, useValue: { openChat: vi.fn(), pointOut$: EMPTY } },
            ],
        })
            // The page's own template is not under test, so its many children are dropped rather than mocked one by one.
            .overrideComponent(CourseLectureDetailsComponent, { set: { template: '', templateUrl: undefined, imports: [] } })
            .compileComponents();

        harness = await RouterTestingHarness.create();
        router = TestBed.inject(Router);
        location = TestBed.inject(Location);
        // The application starts this when it bootstraps; without it Back and Forward never reach the router.
        router.setUpLocationChangeListener();
        component = await harness.navigateByUrl('/courses/1/lectures/1', CourseLectureDetailsComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    const cite = (url: string) => router.navigateByUrl(url, { state: LECTURE_DEEP_LINK_NAVIGATION_STATE });

    it('executes a repeated citation to the URL already shown, with a new request identity', async () => {
        await cite('/courses/1/lectures/1?unit=7&page=4');
        const first = component.deepLink();
        await cite('/courses/1/lectures/1?unit=7&page=4');
        const second = component.deepLink();

        expect(first).toEqual(expect.objectContaining({ unitId: 7, page: 4 }));
        expect(second).toEqual(first);
        expect(second).not.toBe(first);
    });

    it('ignores a discussion post being opened and does not replay the citation when Back returns to it', async () => {
        await cite('/courses/1/lectures/1?unit=7&timestamp=20');
        const first = component.deepLink();

        await router.navigateByUrl('/courses/1/lectures/1?unit=7&timestamp=20&postId=5');
        expect(component.deepLink()).toBe(first);

        location.back();
        await vi.waitFor(() => expect(router.url).toBe('/courses/1/lectures/1?unit=7&timestamp=20'));

        expect(component.deepLink()).toBe(first);
    });

    it('holds a jump into another lecture until that lecture has loaded, and publishes it only for that lecture', async () => {
        await cite('/courses/1/lectures/2?unit=9&page=2');

        // The route has changed, but the lecture has not arrived: the units of lecture 1 must not be searched for unit 9.
        expect(component.deepLink()).toBeUndefined();

        lectureTwoResponse.next(lectureWith(2, [unit(9)]));

        expect(component.deepLink()).toEqual(expect.objectContaining({ unitId: 9, page: 2 }));
        expect(component.lecture()?.id).toBe(2);
    });
});
