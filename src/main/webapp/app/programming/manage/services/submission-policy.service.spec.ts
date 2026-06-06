import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { LockRepositoryPolicy, SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { take } from 'rxjs/operators';
import { provideHttpClient } from '@angular/common/http';

describe('Submission Policy Service', () => {
    setupTestBed({ zoneless: true });

    let httpMock: HttpTestingController;
    let submissionPolicyService: SubmissionPolicyService;
    let lockRepositoryPolicy: LockRepositoryPolicy;
    let programmingExercise: ProgrammingExercise;
    const expectedUrl = 'api/programming/programming-exercises/1/submission-policy';
    const statusOk = { status: 200 };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SubmissionPolicyService, useClass: SubmissionPolicyService }],
        });
        httpMock = TestBed.inject(HttpTestingController);
        submissionPolicyService = TestBed.inject(SubmissionPolicyService);
        lockRepositoryPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 5 } as LockRepositoryPolicy;
        programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.id = 1;
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Invoke submission policy service methods', () => {
        it('should add submission policy to exercise', () => {
            const addPolicySubscription = submissionPolicyService
                .addSubmissionPolicyToProgrammingExercise(lockRepositoryPolicy, programmingExercise.id!)
                .pipe(take(1))
                .subscribe((submissionPolicy) => {
                    expect(submissionPolicy).toBe(lockRepositoryPolicy);
                });

            const request = httpMock.expectOne({ method: 'POST', url: expectedUrl });
            expect(lockRepositoryPolicy.active).toBe(false);
            request.flush(lockRepositoryPolicy);

            addPolicySubscription.unsubscribe();
        });

        it('should update submission policy of exercise', () => {
            const addPolicySubscription = submissionPolicyService
                .updateSubmissionPolicyToProgrammingExercise(lockRepositoryPolicy, programmingExercise.id!)
                .pipe(take(1))
                .subscribe((submissionPolicy) => {
                    expect(submissionPolicy).toBe(lockRepositoryPolicy);
                });

            const request = httpMock.expectOne({ method: 'PATCH', url: expectedUrl });
            request.flush(lockRepositoryPolicy);

            addPolicySubscription.unsubscribe();
        });

        // Using functions to avoid a serialization error
        it.each([() => ({ input: null, expected: undefined }), () => ({ input: lockRepositoryPolicy, expected: lockRepositoryPolicy })])(
            'should get submission policy from exercise',
            (fun: any) => {
                const { input, expected } = fun();
                const getPolicySubscription = submissionPolicyService
                    .getSubmissionPolicyOfProgrammingExercise(programmingExercise.id!)
                    .pipe(take(1))
                    .subscribe((submissionPolicy) => {
                        expect(submissionPolicy).toBe(expected);
                    });

                const request = httpMock.expectOne({ method: 'GET', url: expectedUrl });
                request.flush(input);

                getPolicySubscription.unsubscribe();
            },
        );

        it('should issue delete request', () => {
            const removePolicySubscription = submissionPolicyService.removeSubmissionPolicyFromProgrammingExercise(programmingExercise.id!).subscribe((response) => {
                expect(response.ok).toBe(true);
            });

            const request = httpMock.expectOne({ method: 'DELETE', url: expectedUrl });
            request.flush(statusOk);

            removePolicySubscription.unsubscribe();
        });

        it('should issue enable request', () => {
            const removePolicySubscription = submissionPolicyService.enableSubmissionPolicyOfProgrammingExercise(programmingExercise.id!).subscribe((response) => {
                expect(response.ok).toBe(true);
            });

            const request = httpMock.expectOne({ method: 'PUT', url: expectedUrl + '?activate=true' });
            request.flush(statusOk);
            removePolicySubscription.unsubscribe();
        });

        it('should issue disable request', () => {
            const removePolicySubscription = submissionPolicyService.disableSubmissionPolicyOfProgrammingExercise(programmingExercise.id!).subscribe((response) => {
                expect(response.ok).toBe(true);
            });

            const request = httpMock.expectOne({ method: 'PUT', url: expectedUrl + '?activate=false' });
            request.flush(statusOk);
            removePolicySubscription.unsubscribe();
        });
    });
});
