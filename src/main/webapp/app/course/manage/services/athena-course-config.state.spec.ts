import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AthenaCourseConfigState } from 'app/course/manage/services/athena-course-config.state';
import { AlertService } from 'app/foundation/service/alert.service';

describe('AthenaCourseConfigState', () => {
    const bothDisabled: AthenaCourseConfigDTO = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false };

    function createState() {
        const athenaCourseConfigService = {
            getCourseConfig: vi.fn(),
            updateCourseConfig: vi.fn(),
        } as unknown as AthenaCourseConfigService;
        const alertService = { error: vi.fn() } as unknown as AlertService;
        const state = new AthenaCourseConfigState(5, athenaCourseConfigService, alertService);
        return { state, athenaCourseConfigService, alertService };
    }

    function initWith(state: AthenaCourseConfigState, service: AthenaCourseConfigService, config: AthenaCourseConfigDTO) {
        vi.spyOn(service, 'getCourseConfig').mockReturnValue(of(config));
        state.load();
    }

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load the stored configuration', () => {
        const { state, athenaCourseConfigService } = createState();
        const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false }));

        state.load();

        expect(getSpy).toHaveBeenCalledExactlyOnceWith(5);
        expect(state.gradingFeedbackEnabled()).toBe(true);
        expect(state.formativeFeedbackEnabled()).toBe(false);
    });

    it('should also load the allowed feedback requests reported alongside the switches', () => {
        const { state, athenaCourseConfigService } = createState();
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of({ ...bothDisabled, allowedFeedbackRequests: 10 }));

        state.load();

        expect(state.allowedFeedbackRequests()).toBe(10);
    });

    it('should show both features as disabled and alert when the configuration cannot be loaded', () => {
        const { state, athenaCourseConfigService, alertService } = createState();
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const errorSpy = vi.spyOn(alertService, 'error');

        state.load();

        expect(state.config()).toBeUndefined();
        expect(state.gradingFeedbackEnabled()).toBe(false);
        expect(state.formativeFeedbackEnabled()).toBe(false);
        expect(errorSpy).toHaveBeenCalledExactlyOnceWith('error.http.400');
    });

    describe('isLoaded / ensureLoaded', () => {
        it('should be false before the first load answers', () => {
            const { state, athenaCourseConfigService } = createState();
            vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(new Subject<AthenaCourseConfigDTO>().asObservable());

            state.load();

            expect(state.isLoaded()).toBe(false);
        });

        it('should be true once the load answers successfully', () => {
            const { state, athenaCourseConfigService } = createState();
            initWith(state, athenaCourseConfigService, bothDisabled);

            expect(state.isLoaded()).toBe(true);
        });

        it('should be true once the load answers with an error, so a caller does not wait forever', () => {
            const { state, athenaCourseConfigService } = createState();
            vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

            state.load();

            expect(state.isLoaded()).toBe(true);
        });

        it('should only fetch once no matter how many callers call ensureLoaded', () => {
            const { state, athenaCourseConfigService } = createState();
            const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));

            state.ensureLoaded();
            state.ensureLoaded();
            state.ensureLoaded();

            expect(getSpy).toHaveBeenCalledOnce();
        });
    });

    it.each([
        { feature: 'formativeFeedbackEnabled' as const, expected: { gradingFeedbackEnabled: false, formativeFeedbackEnabled: true } },
        { feature: 'gradingFeedbackEnabled' as const, expected: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false } },
    ])('should save $feature without touching the other feature', ({ feature, expected }) => {
        const { state, athenaCourseConfigService } = createState();
        initWith(state, athenaCourseConfigService, bothDisabled);
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: expected })));

        state.setEnabled(feature, true);

        // Only the switched feature is sent: restating the other one would write back the value this state last read,
        // undoing a change made elsewhere in the meantime.
        expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { [feature]: true });
        expect(state.config()).toEqual(expected);
    });

    it('should not send a request when the feature already has the requested state', () => {
        const { state, athenaCourseConfigService } = createState();
        initWith(state, athenaCourseConfigService, { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });
        const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig');

        state.setEnabled('gradingFeedbackEnabled', true);

        expect(updateSpy).not.toHaveBeenCalled();
    });

    it('should revert the feature and alert when saving fails', () => {
        const { state, athenaCourseConfigService, alertService } = createState();
        initWith(state, athenaCourseConfigService, bothDisabled);
        vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const errorSpy = vi.spyOn(alertService, 'error');

        state.setEnabled('gradingFeedbackEnabled', true);

        expect(state.gradingFeedbackEnabled()).toBe(false);
        expect(errorSpy).toHaveBeenCalledExactlyOnceWith('error.http.400');
    });

    it('should only revert the feature whose save failed', () => {
        const { state, athenaCourseConfigService } = createState();
        initWith(state, athenaCourseConfigService, bothDisabled);
        const formative = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
        vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValueOnce(formative.asObservable()).mockReturnValueOnce(new Subject<never>().asObservable());

        state.setEnabled('formativeFeedbackEnabled', true);
        state.setEnabled('gradingFeedbackEnabled', true);
        formative.error(new HttpErrorResponse({ status: 400 }));

        // Restoring the whole snapshot this switch was clicked on would also drop the grading switch made after it.
        expect(state.config()).toEqual({ gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });
    });

    it('should keep showing the stored state when a feature is switched twice and both saves fail', () => {
        const { state, athenaCourseConfigService } = createState();
        initWith(state, athenaCourseConfigService, bothDisabled);
        const first = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
        const second = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
        vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValueOnce(first.asObservable()).mockReturnValueOnce(second.asObservable());

        state.setEnabled('gradingFeedbackEnabled', true);
        state.setEnabled('gradingFeedbackEnabled', false);
        first.error(new HttpErrorResponse({ status: 400 }));
        second.error(new HttpErrorResponse({ status: 400 }));

        // Rolling the second switch back to the state it replaced would restore the first switch, which failed as
        // well, and leave the feature shown as enabled although nothing was ever stored.
        expect(state.gradingFeedbackEnabled()).toBe(false);
    });

    it('should drop an answer that a newer switch of the same feature has replaced', () => {
        const { state, athenaCourseConfigService } = createState();
        initWith(state, athenaCourseConfigService, bothDisabled);
        const first = new Subject<HttpResponse<AthenaCourseConfigDTO>>();
        vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValueOnce(first.asObservable()).mockReturnValueOnce(new Subject<never>().asObservable());

        state.setEnabled('gradingFeedbackEnabled', true);
        state.setEnabled('gradingFeedbackEnabled', false);
        first.next(new HttpResponse({ body: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false } }));

        // The instructor last asked for disabled, so the answer to the switch before that is only history.
        expect(state.gradingFeedbackEnabled()).toBe(false);
    });

    describe('masterEnabled', () => {
        it('should be false when both features are off', () => {
            const { state, athenaCourseConfigService } = createState();
            initWith(state, athenaCourseConfigService, bothDisabled);

            expect(state.masterEnabled()).toBe(false);
        });

        it.each([
            { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false },
            { gradingFeedbackEnabled: false, formativeFeedbackEnabled: true },
            { gradingFeedbackEnabled: true, formativeFeedbackEnabled: true },
        ])('should be true when either feature is on (%j)', (config) => {
            const { state, athenaCourseConfigService } = createState();
            initWith(state, athenaCourseConfigService, config);

            expect(state.masterEnabled()).toBe(true);
        });

        it('should turn both features on', () => {
            const { state, athenaCourseConfigService } = createState();
            initWith(state, athenaCourseConfigService, bothDisabled);
            const updateSpy = vi
                .spyOn(athenaCourseConfigService, 'updateCourseConfig')
                .mockReturnValue(of(new HttpResponse({ body: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: true } })));

            state.setMasterEnabled(true);

            expect(updateSpy).toHaveBeenCalledWith(5, { gradingFeedbackEnabled: true });
            expect(updateSpy).toHaveBeenCalledWith(5, { formativeFeedbackEnabled: true });
            expect(state.gradingFeedbackEnabled()).toBe(true);
            expect(state.formativeFeedbackEnabled()).toBe(true);
            expect(state.masterEnabled()).toBe(true);
        });

        it('should turn both features off', () => {
            const { state, athenaCourseConfigService } = createState();
            initWith(state, athenaCourseConfigService, { gradingFeedbackEnabled: true, formativeFeedbackEnabled: true });
            const updateSpy = vi.spyOn(athenaCourseConfigService, 'updateCourseConfig').mockReturnValue(of(new HttpResponse({ body: bothDisabled })));

            state.setMasterEnabled(false);

            expect(updateSpy).toHaveBeenCalledWith(5, { gradingFeedbackEnabled: false });
            expect(updateSpy).toHaveBeenCalledWith(5, { formativeFeedbackEnabled: false });
            expect(state.masterEnabled()).toBe(false);
        });

        it('should only send a request for the feature that does not already have the requested state', () => {
            // Turning "on" a course that already runs grading-only should not resend a value the server already
            // has for that feature - only formative actually changes.
            const { state, athenaCourseConfigService } = createState();
            initWith(state, athenaCourseConfigService, { gradingFeedbackEnabled: true, formativeFeedbackEnabled: false });
            const updateSpy = vi
                .spyOn(athenaCourseConfigService, 'updateCourseConfig')
                .mockReturnValue(of(new HttpResponse({ body: { gradingFeedbackEnabled: true, formativeFeedbackEnabled: true } })));

            state.setMasterEnabled(true);

            expect(updateSpy).toHaveBeenCalledExactlyOnceWith(5, { formativeFeedbackEnabled: true });
        });
    });
});
