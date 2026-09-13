import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AthenaCourseConfigStore } from 'app/course/manage/services/athena-course-config.state';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockProvider } from 'ng-mocks';

describe('AthenaCourseConfigStore', () => {
    let store: AthenaCourseConfigStore;
    let athenaCourseConfigService: AthenaCourseConfigService;

    const bothDisabled: AthenaCourseConfigDTO = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(AthenaCourseConfigService), MockProvider(AlertService)],
        });

        store = TestBed.inject(AthenaCourseConfigStore);
        athenaCourseConfigService = TestBed.inject(AthenaCourseConfigService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should return the same instance for the same course id', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));

        const first = store.getOrCreate(5);
        const second = store.getOrCreate(5);

        expect(second).toBe(first);
    });

    it('should return a different instance for a different course id', () => {
        vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));

        const first = store.getOrCreate(5);
        const second = store.getOrCreate(6);

        expect(second).not.toBe(first);
    });

    it('should not fetch anything just by creating the state', () => {
        // getOrCreate is called from a computed (see createAthenaCourseConfigState), which must stay free of side
        // effects such as an HTTP request.
        const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(of(bothDisabled));

        store.getOrCreate(5);

        expect(getSpy).not.toHaveBeenCalled();
    });

    it('should only fetch once when the same course is asked for by more than one caller', () => {
        // Both callers ask for the state before the request either of them triggers has answered, as two components
        // mounting for the same course at roughly the same time do.
        const getSpy = vi.spyOn(athenaCourseConfigService, 'getCourseConfig').mockReturnValue(new Subject<AthenaCourseConfigDTO>().asObservable());

        const first = store.getOrCreate(5);
        first.ensureLoaded();
        const second = store.getOrCreate(5);
        second.ensureLoaded();

        expect(getSpy).toHaveBeenCalledOnce();
    });
});
