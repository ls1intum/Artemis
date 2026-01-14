import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseOperationProgressComponent } from './course-operation-progress.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseOperationProgressDTO, CourseOperationStatus, CourseOperationType } from 'app/core/course/shared/entities/course-operation-progress.model';
import dayjs from 'dayjs/esm';
import { MockComponent, MockModule } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CommonModule } from '@angular/common';
import { DialogModule } from 'primeng/dialog';
import { ProgressBarModule } from 'primeng/progressbar';

describe('CourseOperationProgressComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseOperationProgressComponent;
    let fixture: ComponentFixture<CourseOperationProgressComponent>;

    const BUFFER_MS = 200;
    const flushBuffer = async () => vi.advanceTimersByTimeAsync(BUFFER_MS);

    const createProgressDTO = (overrides: Partial<CourseOperationProgressDTO> = {}): CourseOperationProgressDTO => ({
        operationType: CourseOperationType.DELETE,
        status: CourseOperationStatus.IN_PROGRESS,
        currentStep: 'Deleting exercises',
        stepsCompleted: 2,
        totalSteps: 10,
        itemsProcessed: 5,
        totalItems: 20,
        failed: 0,
        startedAt: dayjs().subtract(10, 'seconds'),
        weightedProgressPercent: 20,
        ...overrides,
    });

    beforeEach(async () => {
        vi.useFakeTimers();
        await TestBed.configureTestingModule({
            imports: [CourseOperationProgressComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(CourseOperationProgressComponent, {
                set: {
                    imports: [CommonModule, MockModule(DialogModule), MockModule(ProgressBarModule), MockComponent(FaIconComponent)],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CourseOperationProgressComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should not show dialog when progress is undefined', () => {
        fixture.componentRef.setInput('progress', undefined);
        fixture.detectChanges();

        expect(component.dialogVisible()).toBe(false);
    });

    it('should show dialog when progress is defined', async () => {
        fixture.componentRef.setInput('progress', createProgressDTO());
        fixture.detectChanges();
        await flushBuffer(); // Allow buffer time
        fixture.detectChanges();

        expect(component.dialogVisible()).toBe(true);
    });

    describe('status indicators', () => {
        it('should show spinner icon when in progress', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ status: CourseOperationStatus.IN_PROGRESS }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.isInProgress()).toBe(true);
            expect(component.isCompleted()).toBe(false);
            expect(component.isFailed()).toBe(false);
        });

        it('should show check icon when completed', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ status: CourseOperationStatus.COMPLETED }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.isInProgress()).toBe(false);
            expect(component.isCompleted()).toBe(true);
            expect(component.isFailed()).toBe(false);
        });

        it('should show warning icon when failed', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ status: CourseOperationStatus.FAILED }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.isInProgress()).toBe(false);
            expect(component.isCompleted()).toBe(false);
            expect(component.isFailed()).toBe(true);
        });
    });

    describe('progress percentage', () => {
        it('should return weighted progress percentage', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ weightedProgressPercent: 50 }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.progressPercentage()).toBe(50);
        });

        it('should return 0 when weightedProgressPercent is 0', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ weightedProgressPercent: 0 }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.progressPercentage()).toBe(0);
        });

        it('should return 0 when progress is undefined', () => {
            fixture.componentRef.setInput('progress', undefined);
            fixture.detectChanges();

            expect(component.progressPercentage()).toBe(0);
        });

        it('should round weighted percentage to nearest integer', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ weightedProgressPercent: 33.7 }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.progressPercentage()).toBe(34);
        });
    });

    describe('progress style class', () => {
        it('should return empty string when in progress', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ status: CourseOperationStatus.IN_PROGRESS }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.progressStyleClass()).toBe('');
        });

        it('should return progress-success when completed', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ status: CourseOperationStatus.COMPLETED }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.progressStyleClass()).toBe('progress-success');
        });

        it('should return progress-danger when failed', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ status: CourseOperationStatus.FAILED }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.progressStyleClass()).toBe('progress-danger');
        });
    });

    describe('ETA calculation', () => {
        it('should return undefined when progress is undefined', () => {
            fixture.componentRef.setInput('progress', undefined);
            fixture.detectChanges();

            expect(component.eta()).toBeUndefined();
        });

        it('should return undefined when weightedProgressPercent is 0', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ weightedProgressPercent: 0 }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.eta()).toBeUndefined();
        });

        it('should return undefined when weightedProgressPercent is 100', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ weightedProgressPercent: 100 }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.eta()).toBeUndefined();
        });

        it('should return undefined when startedAt is not set', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ startedAt: undefined }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.eta()).toBeUndefined();
        });

        it('should calculate ETA correctly based on weighted progress', async () => {
            const startedAt = dayjs().subtract(20, 'seconds');
            fixture.componentRef.setInput(
                'progress',
                createProgressDTO({
                    weightedProgressPercent: 20,
                    startedAt: startedAt,
                }),
            );
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            const eta = component.eta();
            expect(eta).toBeDefined();
            expect(eta).toContain('s');
        });

        it('should format ETA with hours when applicable', async () => {
            const startedAt = dayjs().subtract(2, 'hours');
            fixture.componentRef.setInput(
                'progress',
                createProgressDTO({
                    weightedProgressPercent: 10,
                    startedAt: startedAt,
                }),
            );
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            const eta = component.eta();
            expect(eta).toBeDefined();
            expect(eta).toContain('h');
        });
    });

    describe('close functionality', () => {
        it('should emit closeOverlay when onClose is called', () => {
            const closeOverlaySpy = vi.spyOn(component.closeOverlay, 'emit');
            component.onClose();

            expect(closeOverlaySpy).toHaveBeenCalled();
        });
    });

    describe('buffered updates', () => {
        it('should have bufferedProgress signal that reflects progress input', async () => {
            // Initially undefined
            expect(component.bufferedProgress()).toBeUndefined();

            // Set progress and wait for effect to run
            fixture.componentRef.setInput('progress', createProgressDTO({ stepsCompleted: 5, weightedProgressPercent: 50 }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            // Buffered progress should now have the value
            expect(component.bufferedProgress()).toBeDefined();
            expect(component.bufferedProgress()?.stepsCompleted).toBe(5);
        });

        it('should immediately apply status changes', async () => {
            fixture.componentRef.setInput('progress', createProgressDTO({ status: CourseOperationStatus.IN_PROGRESS }));
            fixture.detectChanges();
            await flushBuffer();
            fixture.detectChanges();

            expect(component.isInProgress()).toBe(true);

            // Status change should be applied immediately
            fixture.componentRef.setInput('progress', createProgressDTO({ status: CourseOperationStatus.COMPLETED }));
            fixture.detectChanges();

            expect(component.isCompleted()).toBe(true);
        });
    });

    describe('operation type display', () => {
        it.each([
            { operationType: CourseOperationType.DELETE, expectedTranslation: 'artemisApp.course.operationProgress.delete' },
            { operationType: CourseOperationType.RESET, expectedTranslation: 'artemisApp.course.operationProgress.reset' },
            { operationType: CourseOperationType.ARCHIVE, expectedTranslation: 'artemisApp.course.operationProgress.archive' },
        ])(
            'should return correct title key for $operationType operation',
            async ({ operationType, expectedTranslation }: { operationType: CourseOperationType; expectedTranslation: string }) => {
                fixture.componentRef.setInput('progress', createProgressDTO({ operationType }));
                fixture.detectChanges();
                await flushBuffer();
                fixture.detectChanges();

                expect(component.operationTitle()).toBe(expectedTranslation);
            },
        );
    });
});
