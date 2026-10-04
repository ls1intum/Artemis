import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { ActivatedRouteSnapshot, Route, convertToParamMap, provideRouter, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { NewStudentParticipationResolver, StudentParticipationResolver } from 'app/text/manage/assess/service/text-submission-assessment-resolve.service';
import { textSubmissionAssessmentRoutes } from 'app/text/manage/assess/text-submission-assessment.route';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

/**
 * The correction round reaches the server from here, before any assessment editor exists. An unvalidated `Number()` of
 * the query parameter sent NaN, a fraction or a negative round to the endpoint, and left the loaded data disagreeing
 * with the round the editor would settle on (issue #13396).
 */
describe('Text submission assessment resolvers', () => {
    /**
     * @param queryParams the query parameters of the route being resolved
     * @param params the path parameters of the route being resolved
     * @returns a route snapshot carrying exactly those parameters
     */
    function routeSnapshot(queryParams: Record<string, string>, params: Record<string, string>): ActivatedRouteSnapshot {
        return { queryParamMap: convertToParamMap(queryParams), paramMap: convertToParamMap(params) } as ActivatedRouteSnapshot;
    }

    describe('NewStudentParticipationResolver', () => {
        let resolver: NewStudentParticipationResolver;
        let submissionService: TextSubmissionService;

        beforeEach(() => {
            TestBed.configureTestingModule({ providers: [NewStudentParticipationResolver, MockProvider(TextSubmissionService)] });
            resolver = TestBed.inject(NewStudentParticipationResolver);
            submissionService = TestBed.inject(TextSubmissionService);
        });

        it('should request the correction round from the parameter', () => {
            const spy = vi.spyOn(submissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(undefined));

            resolver.resolve(routeSnapshot({ 'correction-round': '1' }, { exerciseId: '7' })).subscribe();

            expect(spy).toHaveBeenCalledExactlyOnceWith(7, 'lock', 1);
        });

        it.each([
            { param: undefined, description: 'absent' },
            { param: '', description: 'empty' },
            { param: '   ', description: 'whitespace only' },
            { param: 'abc', description: 'not a number' },
            { param: '1.5', description: 'fractional' },
            { param: '-1', description: 'negative' },
        ])('should request the first correction round for a $description parameter', ({ param }) => {
            const spy = vi.spyOn(submissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(undefined));

            resolver.resolve(routeSnapshot(param === undefined ? {} : { 'correction-round': param }, { exerciseId: '7' })).subscribe();

            expect(spy).toHaveBeenCalledExactlyOnceWith(7, 'lock', 0);
        });

        it.each([
            { param: '1', description: 'a usable' },
            { param: undefined, description: 'an absent' },
            { param: 'abc', description: 'an unusable' },
        ])('should report the very round it requested for $description parameter', ({ param }) => {
            // The page indexes the loaded results by the round, so it takes the round from here instead of parsing the
            // URL a second time. The two must therefore be the same value, not two fallbacks that happen to agree.
            const spy = vi.spyOn(submissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(undefined));
            let reportedCorrectionRound: number | undefined;

            resolver
                .resolve(routeSnapshot(param === undefined ? {} : { 'correction-round': param }, { exerciseId: '7' }))
                .subscribe((routeData) => (reportedCorrectionRound = routeData.correctionRound));

            expect(reportedCorrectionRound).toBe(spy.mock.calls[0][2]);
        });

        it('should still report the round when the load fails', () => {
            // The page renders an explanation instead of the assessment in this case, and still shows which round it is.
            vi.spyOn(submissionService, 'getSubmissionWithoutAssessment').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            let reportedCorrectionRound: number | undefined;

            resolver.resolve(routeSnapshot({ 'correction-round': '1' }, { exerciseId: '7' })).subscribe((routeData) => (reportedCorrectionRound = routeData.correctionRound));

            expect(reportedCorrectionRound).toBe(1);
        });
    });

    describe('StudentParticipationResolver', () => {
        let resolver: StudentParticipationResolver;
        let assessmentService: TextAssessmentService;

        beforeEach(() => {
            TestBed.configureTestingModule({ providers: [StudentParticipationResolver, MockProvider(TextAssessmentService)] });
            resolver = TestBed.inject(StudentParticipationResolver);
            assessmentService = TestBed.inject(TextAssessmentService);
        });

        it('should request the correction round from the parameter', () => {
            const spy = vi.spyOn(assessmentService, 'getFeedbackDataForExerciseSubmission').mockReturnValue(of({} as StudentParticipation));

            resolver.resolve(routeSnapshot({ 'correction-round': '1' }, { submissionId: '42' })).subscribe();

            expect(spy).toHaveBeenCalledExactlyOnceWith(42, 1);
        });

        it.each([
            { param: undefined, description: 'absent' },
            { param: '   ', description: 'whitespace only' },
            { param: '1.5', description: 'fractional' },
        ])('should request the first correction round for a $description parameter', ({ param }) => {
            const spy = vi.spyOn(assessmentService, 'getFeedbackDataForExerciseSubmission').mockReturnValue(of({} as StudentParticipation));

            resolver.resolve(routeSnapshot(param === undefined ? {} : { 'correction-round': param }, { submissionId: '42' })).subscribe();

            expect(spy).toHaveBeenCalledExactlyOnceWith(42, 0);
        });

        it.each([
            { param: '1', description: 'a usable' },
            { param: undefined, description: 'an absent' },
            { param: '-1', description: 'an unusable' },
        ])('should report the very round it requested for $description parameter', ({ param }) => {
            const spy = vi.spyOn(assessmentService, 'getFeedbackDataForExerciseSubmission').mockReturnValue(of({} as StudentParticipation));
            let reportedCorrectionRound: number | undefined;

            resolver
                .resolve(routeSnapshot(param === undefined ? {} : { 'correction-round': param }, { submissionId: '42' }))
                .subscribe((routeData) => (reportedCorrectionRound = routeData.correctionRound));

            expect(reportedCorrectionRound).toBe(spy.mock.calls[0][1]);
        });
    });

    describe('re-resolving when the correction round in the url changes', () => {
        @Component({ template: '' })
        class AssessmentPageStub {}

        /**
         * Takes the resolver and the `runGuardsAndResolvers` policy from the production route, so that the policy those
         * tests pin is the one the application ships. The rest of the route (guards, data, lazily loaded component) is
         * left out because it is not what is under test here.
         *
         * @param path the path of the production route to exercise
         * @returns that route, wired to a stub component
         */
        function productionRoute(path: string): Route {
            const route = textSubmissionAssessmentRoutes.find((candidate) => candidate.path === path);
            expect(route).toBeDefined();
            return { path, component: AssessmentPageStub, resolve: route!.resolve, runGuardsAndResolvers: route!.runGuardsAndResolvers };
        }

        it('should load the newly requested round when only the query parameter changes, and only then', async () => {
            // The round is a query parameter, and the resolver loads the participation of that round. Angular re-runs a
            // resolver only for the changes named by `runGuardsAndResolvers`, and its default of `paramsChange` ignores
            // query parameters, which left the page showing the round it had been opened with (#13396).
            // The repeated navigation matters as much as the changed one: the application configures
            // `onSameUrlNavigation: 'reload'`, so under `always` every repeat would load again and lock a result again.
            TestBed.configureTestingModule({
                providers: [
                    MockProvider(TextAssessmentService),
                    provideRouter([productionRoute('submissions/:submissionId/assessment')], withRouterConfig({ onSameUrlNavigation: 'reload' })),
                ],
            });
            const spy = vi.spyOn(TestBed.inject(TextAssessmentService), 'getFeedbackDataForExerciseSubmission').mockReturnValue(of({} as StudentParticipation));
            const harness = await RouterTestingHarness.create();

            await harness.navigateByUrl('/submissions/42/assessment?correction-round=1');
            await harness.navigateByUrl('/submissions/42/assessment?correction-round=0');
            await harness.navigateByUrl('/submissions/42/assessment?correction-round=0');

            expect(spy.mock.calls.map((call) => call[1])).toEqual([1, 0]);
        });

        it('should not load again when a named result stays the same', async () => {
            // This route resolves by the result id, and a result identifies its own round, so a query parameter that
            // changes must not trigger another load.
            TestBed.configureTestingModule({
                providers: [
                    MockProvider(TextAssessmentService),
                    provideRouter([productionRoute('submissions/:submissionId/assessments/:resultId')], withRouterConfig({ onSameUrlNavigation: 'reload' })),
                ],
            });
            const spy = vi.spyOn(TestBed.inject(TextAssessmentService), 'getFeedbackDataForExerciseSubmission').mockReturnValue(of({} as StudentParticipation));
            const harness = await RouterTestingHarness.create();

            await harness.navigateByUrl('/submissions/42/assessments/7?correction-round=1');
            await harness.navigateByUrl('/submissions/42/assessments/7?correction-round=0');

            expect(spy).toHaveBeenCalledExactlyOnceWith(42, undefined, 7);
        });
    });
});
