import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Signal, signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject, of, throwError } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AthenaCourseConfigState, createAthenaCourseConfigState } from 'app/course/manage/services/athena-course-config.state';
import { AlertService } from 'app/foundation/service/alert.service';

describe('AthenaCourseConfigState', () => {
    let athenaCourseConfigService: AthenaCourseConfigService;
    let alertService: AlertService;
    let state: AthenaCourseConfigState;

    const bothDisabled: AthenaCourseConfigDTO = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false };
    const failure = () => new HttpErrorResponse({ status: 400 });

    function mockLoad(answer: Observable<AthenaCourseConfigDTO>) {
        return vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(answer);
    }

    function loadWith(config: AthenaCourseConfigDTO) {
        mockLoad(of(config));
        state.load();
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(AthenaCourseConfigService), MockProvider(AlertService)],
        });
        athenaCourseConfigService = TestBed.inject(AthenaCourseConfigService);
        alertService = TestBed.inject(AlertService);
        state = new AthenaCourseConfigState(5, athenaCourseConfigService, alertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('load', () => {
        it('should show the stored configuration', () => {
            const getSpy = mockLoad(of({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false }));

            state.load();

            expect(getSpy).toHaveBeenCalledExactlyOnceWith(5);
            expect(state.gradingFeedbackEnabled()).toBe(true);
            expect(state.formativeFeedbackEnabled()).toBe(false);
        });

        it('should show both features as disabled and alert when the configuration cannot be loaded', () => {
            mockLoad(throwError(failure));
            const errorSpy = vi.spyOn(alertService, 'error');

            state.load();

            expect(state.config()).toBeUndefined();
            expect(state.masterEnabled()).toBe(false);
            expect(errorSpy).toHaveBeenCalledExactlyOnceWith('error.http.400');
        });
    });

    describe('masterEnabled', () => {
        it.each([
            { config: bothDisabled, expected: false },
            { config: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false }, expected: true },
            { config: { gradingFeedbackEnabled: false, formativeFeedbackEnabled: true }, expected: true },
            { config: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: true }, expected: true },
        ])('should be $expected for $config', ({ config, expected }) => {
            loadWith(config);

            expect(state.masterEnabled()).toBe(expected);
        });
    });

    describe('setEnabled', () => {
        it.each([
            { feature: 'formativeFeedbackEnabled' as const, expected: { gradingFeedbackEnabled: false, formativeFeedbackEnabled: true } },
            { feature: 'gradingFeedbackEnabled' as const, expected: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false } },
        ])('should save $feature without touching the other feature', ({ feature, expected }) => {
            loadWith(bothDisabled);
            const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: expected })));

            state.setEnabled(feature, true);

            // Only the switched feature is sent: restating the other one would write back the value this client last
            // read, undoing a change made elsewhere in the meantime.
            expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { [feature]: true });
            expect(state.config()).toEqual(expected);
        });

        it('should still save a switched feature when no configuration was loaded', () => {
            mockLoad(throwError(failure));
            state.load();
            const updateSpy = vi
                .spyOn(athenaCourseConfigService, 'updateCourseConfig')
                .mockReturnValue(of(new HttpResponse({ body: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false } })));

            state.setEnabled('gradingFeedbackEnabled', true);

            expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { gradingFeedbackEnabled: true });
            expect(state.gradingFeedbackEnabled()).toBe(true);
        });

        it('should not send a request when the feature already has the requested state', () => {
            loadWith({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });
            const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig');

            state.setEnabled('gradingFeedbackEnabled', true);

            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should revert the feature and alert when saving fails', () => {
            loadWith(bothDisabled);
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(throwError(failure));
            const errorSpy = vi.spyOn(alertService, 'error');

            state.setEnabled('gradingFeedbackEnabled', true);

            expect(state.gradingFeedbackEnabled()).toBe(false);
            expect(errorSpy).toHaveBeenCalledExactlyOnceWith('error.http.400');
        });

        it('should only revert the feature whose save failed', () => {
            loadWith(bothDisabled);
            const formative = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValueOnce(formative.asObservable()).mockReturnValueOnce(new Subject<never>().asObservable());

            state.setEnabled('formativeFeedbackEnabled', true);
            state.setEnabled('gradingFeedbackEnabled', true);
            formative.error(failure());

            // Restoring the whole snapshot this switch was made on would also drop the grading switch made after it.
            expect(state.config()).toEqual({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });
        });

        it('should keep showing the stored state when a feature is switched twice and both saves fail', () => {
            loadWith(bothDisabled);
            const first = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
            const second = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValueOnce(first.asObservable()).mockReturnValueOnce(second.asObservable());

            state.setEnabled('gradingFeedbackEnabled', true);
            state.setEnabled('gradingFeedbackEnabled', false);
            first.error(failure());
            second.error(failure());

            // Rolling the second switch back to the state it replaced would restore the first switch, which failed as
            // well, and leave the feature shown as enabled although nothing was ever stored.
            expect(state.gradingFeedbackEnabled()).toBe(false);
        });

        it('should roll a failed switch back to the last state the server stored', () => {
            loadWith(bothDisabled);
            const failing = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig')
                .mockReturnValueOnce(of(new HttpResponse({ body: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false } })))
                .mockReturnValueOnce(failing.asObservable());

            state.setEnabled('gradingFeedbackEnabled', true);
            state.setEnabled('gradingFeedbackEnabled', false);
            failing.error(failure());

            expect(state.gradingFeedbackEnabled()).toBe(true);
        });

        it('should drop an answer that a newer switch of the same feature has replaced', () => {
            loadWith(bothDisabled);
            const first = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValueOnce(first.asObservable()).mockReturnValueOnce(new Subject<never>().asObservable());

            state.setEnabled('gradingFeedbackEnabled', true);
            state.setEnabled('gradingFeedbackEnabled', false);
            first.next(new HttpResponse({ body: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false } }));

            // The instructor last asked for disabled, so the answer to the switch before that is only history.
            expect(state.gradingFeedbackEnabled()).toBe(false);
        });
    });

    describe('load racing a switch', () => {
        let load: Subject<AthenaCourseConfigDTO>;

        beforeEach(() => {
            load = new Subject<AthenaCourseConfigDTO>();
            mockLoad(load.asObservable());
            state.load();
        });

        it('should keep a feature switched while the configuration was still loading', () => {
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(new Subject<never>().asObservable());

            state.setEnabled('gradingFeedbackEnabled', true);
            load.next(bothDisabled);

            // Applying the load now would put the state the server held before the switch back on screen.
            expect(state.gradingFeedbackEnabled()).toBe(true);
        });

        it('should apply the loaded state of the feature that was not switched', () => {
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(new Subject<never>().asObservable());

            state.setEnabled('gradingFeedbackEnabled', true);
            load.next({ gradingFeedbackEnabled: false, formativeFeedbackEnabled: true });

            expect(state.gradingFeedbackEnabled()).toBe(true);
            expect(state.formativeFeedbackEnabled()).toBe(true);
        });

        it('should show the loaded state when the load answers after a failed switch', () => {
            // A feature stored as enabled still shows as disabled while the configuration is loading, so enabling it is
            // what the instructor does. Its save failing confirms nothing, which leaves the load answering afterwards the
            // first thing the server has said about the feature.
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(throwError(failure));

            state.setEnabled('gradingFeedbackEnabled', true);
            load.next({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });

            expect(state.gradingFeedbackEnabled()).toBe(true);
        });

        it('should roll a failed switch back to the state the load supplied while it was in flight', () => {
            const save = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(save.asObservable());

            state.setEnabled('gradingFeedbackEnabled', true);
            load.next({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });
            save.error(failure());

            // The load is not shown while the switch is in flight, but it is what the failed switch falls back to.
            expect(state.gradingFeedbackEnabled()).toBe(true);
        });
    });

    describe('setMasterEnabled', () => {
        it.each([true, false])('should save both features as %s', (enabled) => {
            loadWith({ gradingFeedbackEnabled: !enabled, formativeFeedbackEnabled: !enabled });
            const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(new Subject<never>().asObservable());

            state.setMasterEnabled(enabled);

            expect(updateSpy).toHaveBeenCalledTimes(2);
            expect(updateSpy).toHaveBeenCalledWith(5, { gradingFeedbackEnabled: enabled });
            expect(updateSpy).toHaveBeenCalledWith(5, { formativeFeedbackEnabled: enabled });
            expect(state.masterEnabled()).toBe(enabled);
        });

        it('should only switch the feature that is not yet on', () => {
            loadWith({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });
            const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(new Subject<never>().asObservable());

            state.setMasterEnabled(true);

            expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { formativeFeedbackEnabled: true });
        });

        it('should leave the other feature on when one of the two saves fails', () => {
            loadWith(bothDisabled);
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig')
                .mockReturnValueOnce(of(new HttpResponse({ body: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false } })))
                .mockReturnValueOnce(throwError(failure));

            state.setMasterEnabled(true);

            expect(state.config()).toEqual({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });
            expect(state.masterEnabled()).toBe(true);
        });
    });
});

describe('createAthenaCourseConfigState', () => {
    let athenaCourseConfigService: AthenaCourseConfigService;
    const courseId = signal<number | undefined>(5);
    let state: Signal<AthenaCourseConfigState | undefined>;

    const bothDisabled: AthenaCourseConfigDTO = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(AthenaCourseConfigService), MockProvider(AlertService)],
        });
        athenaCourseConfigService = TestBed.inject(AthenaCourseConfigService);
        courseId.set(5);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function create() {
        state = TestBed.runInInjectionContext(() => createAthenaCourseConfigState(courseId));
        TestBed.tick();
    }

    function switchCourse(id: number | undefined) {
        courseId.set(id);
        TestBed.tick();
    }

    it('should load the configuration of the course as soon as it is created', () => {
        const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false }));

        create();

        expect(getSpy).toHaveBeenCalledExactlyOnceWith(5);
        expect(state()?.gradingFeedbackEnabled()).toBe(true);
    });

    it('should hold no state and load nothing without a course', () => {
        const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig');
        courseId.set(undefined);

        create();

        expect(state()).toBeUndefined();
        expect(getSpy).not.toHaveBeenCalled();
    });

    describe('when the course changes', () => {
        it('should load the configuration of the new course', () => {
            const getSpy = vi
                .spyOn(athenaCourseConfigService, 'getCourseConfig')
                .mockReturnValueOnce(of({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false }))
                .mockReturnValueOnce(of(bothDisabled));
            create();

            switchCourse(6);

            expect(getSpy).toHaveBeenCalledTimes(2);
            expect(getSpy).toHaveBeenLastCalledWith(6);
            expect(state()?.gradingFeedbackEnabled()).toBe(false);
        });

        it('should switch the new course even where the previous one already had the requested state', () => {
            vi.spyOn(athenaCourseConfigService, 'getCourseConfig')
                .mockReturnValueOnce(of({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false }))
                .mockReturnValueOnce(new Subject<AthenaCourseConfigDTO>().asObservable());
            const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(new Subject<never>().asObservable());
            create();
            switchCourse(6);

            state()?.setEnabled('gradingFeedbackEnabled', true);

            // Carrying over the enabled grading feedback of course 5 would have made this switch look like a no-op.
            expect(updateSpy).toHaveBeenCalledExactlyOnceWith(6, { gradingFeedbackEnabled: true });
        });

        it('should ignore answers that arrive for the previous course', () => {
            const load = new Subject<AthenaCourseConfigDTO>();
            const save = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
            vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValueOnce(load.asObservable()).mockReturnValueOnce(of(bothDisabled));
            vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(save.asObservable());
            create();
            state()?.setEnabled('formativeFeedbackEnabled', true);

            switchCourse(6);
            load.next({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: true });
            save.next(new HttpResponse({ body: { gradingFeedbackEnabled: false, formativeFeedbackEnabled: true } }));

            expect(state()?.config()).toEqual(bothDisabled);
        });
    });
});
