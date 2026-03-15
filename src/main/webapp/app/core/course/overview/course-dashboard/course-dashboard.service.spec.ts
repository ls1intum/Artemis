import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CourseDashboardService } from 'app/core/course/overview/course-dashboard/course-dashboard.service';
import { provideHttpClient } from '@angular/common/http';
import { take } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { CompetencyJol } from 'app/atlas/shared/entities/competency.model';

describe('CourseDashboardService', () => {
    setupTestBed({ zoneless: true });

    let service: CourseDashboardService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(CourseDashboardService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        httpMock.verify();
    });

    it('should convert metrics in response', () => {
        const rawResponse = {
            exerciseMetrics: {
                exerciseInformation: {
                    1: {
                        id: 1,
                        title: 'Exercise 1',
                        shortName: 'E1',
                        start: '2024-01-01T00:00:00Z',
                        due: '2024-01-10T00:00:00Z',
                        type: 'de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise',
                    },
                    2: {
                        id: 2,
                        title: 'Exercise 2',
                        shortName: 'E2',
                        start: '2024-01-02T00:00:00Z',
                        type: 'de.tum.cit.aet.artemis.modeling.domain.ModelingExercise',
                    },
                    3: {
                        id: 3,
                        title: 'Exercise 3',
                        shortName: 'E3',
                        start: '2024-01-03T00:00:00Z',
                        due: '2024-01-12T00:00:00Z',
                        type: 'de.tum.cit.aet.artemis.quiz.domain.QuizExercise',
                    },
                    4: {
                        id: 4,
                        title: 'Exercise 4',
                        shortName: 'E4',
                        start: '2024-01-04T00:00:00Z',
                        due: '2024-01-14T00:00:00Z',
                        type: 'de.tum.cit.aet.artemis.text.domain.TextExercise',
                    },
                    5: {
                        id: 5,
                        title: 'Exercise 5',
                        shortName: 'E5',
                        start: '2024-01-05T00:00:00Z',
                        due: '2024-01-15T00:00:00Z',
                        type: 'de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise',
                    },
                },
                categories: {
                    1: [JSON.stringify({ category: 'Basics', color: '#123' })],
                    3: [JSON.stringify({ category: 'Quiz', color: '#456' })],
                },
                teamId: {
                    3: 42,
                },
            },
            lectureUnitStudentMetricsDTO: {
                lectureUnitInformation: {
                    10: {
                        id: 10,
                        name: 'Attachment',
                        releaseDate: '2024-02-01T00:00:00Z',
                        type: 'de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit',
                    },
                    11: {
                        id: 11,
                        name: 'Exercise',
                        releaseDate: '2024-02-02T00:00:00Z',
                        type: 'de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit',
                    },
                    12: {
                        id: 12,
                        name: 'Text',
                        releaseDate: '2024-02-03T00:00:00Z',
                        type: 'de.tum.cit.aet.artemis.lecture.domain.TextUnit',
                    },
                    13: {
                        id: 13,
                        name: 'Online',
                        releaseDate: '2024-02-04T00:00:00Z',
                        type: 'de.tum.cit.aet.artemis.lecture.domain.OnlineUnit',
                    },
                },
            },
            competencyMetrics: {
                competencyInformation: {
                    5: {
                        id: 5,
                        title: 'Competency 5',
                        softDueDate: '2024-03-01T00:00:00Z',
                    },
                    6: {
                        id: 6,
                        title: 'Competency 6',
                    },
                },
                progress: {
                    5: 0.5,
                },
                confidence: {
                    5: 0.8,
                },
                currentJolValues: {
                    5: {
                        competencyId: 5,
                        jolValue: 2,
                        judgementTime: '2024-03-01T00:00:00Z',
                        competencyProgress: 0.5,
                        competencyConfidence: 0.8,
                    } as CompetencyJol,
                    6: {
                        competencyId: 6,
                        jolValue: 1,
                        judgementTime: '2024-03-02T00:00:00Z',
                        competencyProgress: 0.2,
                        competencyConfidence: 0.2,
                    } as CompetencyJol,
                },
            },
        };

        service
            .getCourseMetricsForUser(123)
            .pipe(take(1))
            .subscribe((response) => {
                const exerciseInfo = response.body?.exerciseMetrics?.exerciseInformation;
                expect(exerciseInfo?.['1'].type).toBe(ExerciseType.PROGRAMMING);
                expect(exerciseInfo?.['2'].type).toBe(ExerciseType.MODELING);
                expect(exerciseInfo?.['3'].type).toBe(ExerciseType.QUIZ);
                expect(exerciseInfo?.['4'].type).toBe(ExerciseType.TEXT);
                expect(exerciseInfo?.['5'].type).toBe(ExerciseType.FILE_UPLOAD);
                expect(dayjs.isDayjs(exerciseInfo?.['1'].startDate)).toBe(true);
                expect(dayjs.isDayjs(exerciseInfo?.['1'].dueDate)).toBe(true);
                expect(exerciseInfo?.['2'].dueDate).toBeUndefined();
                expect(exerciseInfo?.['3'].studentAssignedTeamId).toBe(42);
                expect(exerciseInfo?.['1'].studentAssignedTeamId).toBeUndefined();
                const categories = exerciseInfo?.['1'].categories ?? [];
                expect(categories).toHaveLength(1);
                expect((categories[0] as ExerciseCategory).category).toBe('Basics');

                const lectureUnits = response.body?.lectureUnitStudentMetricsDTO?.lectureUnitInformation;
                expect(lectureUnits?.['10'].type).toBe(LectureUnitType.ATTACHMENT_VIDEO);
                expect(lectureUnits?.['11'].type).toBe(LectureUnitType.EXERCISE);
                expect(lectureUnits?.['12'].type).toBe(LectureUnitType.TEXT);
                expect(lectureUnits?.['13'].type).toBe(LectureUnitType.ONLINE);
                expect(dayjs.isDayjs(lectureUnits?.['10'].releaseDate)).toBe(true);

                const competencies = response.body?.competencyMetrics?.competencyInformation;
                expect(dayjs.isDayjs(competencies?.['5'].softDueDate)).toBe(true);
                expect(competencies?.['6'].softDueDate).toBeUndefined();

                const jolValues = response.body?.competencyMetrics?.currentJolValues ?? {};
                expect(Object.keys(jolValues)).toEqual(['5']);
            });

        const req = httpMock.expectOne({ method: 'GET', url: 'api/atlas/metrics/course/123/student' });
        req.flush(rawResponse);
    });

    it('should handle response without body', () => {
        service
            .getCourseMetricsForUser(321)
            .pipe(take(1))
            .subscribe((response) => {
                expect(response.body).toBeNull();
            });

        const req = httpMock.expectOne({ method: 'GET', url: 'api/atlas/metrics/course/321/student' });
        req.flush(null);
    });

    it('should throw for unknown exercise type', () => {
        const rawResponse = {
            exerciseMetrics: {
                exerciseInformation: {
                    1: {
                        id: 1,
                        title: 'Exercise 1',
                        shortName: 'E1',
                        start: '2024-01-01T00:00:00Z',
                        type: 'com.example.UnknownExercise',
                    },
                },
            },
        };
        const errorSpy = vi.fn();

        service.getCourseMetricsForUser(10).subscribe({ error: errorSpy });

        const req = httpMock.expectOne({ method: 'GET', url: 'api/atlas/metrics/course/10/student' });
        req.flush(rawResponse);

        expect(errorSpy).toHaveBeenCalledOnce();
        expect((errorSpy.mock.calls[0][0] as Error).message).toBe('Unknown exercise type: com.example.UnknownExercise');
    });

    it('should throw for unknown lecture unit type', () => {
        const rawResponse = {
            lectureUnitStudentMetricsDTO: {
                lectureUnitInformation: {
                    10: {
                        id: 10,
                        name: 'Unit',
                        releaseDate: '2024-02-01T00:00:00Z',
                        type: 'com.example.UnknownLectureUnit',
                    },
                },
            },
        };
        const errorSpy = vi.fn();

        service.getCourseMetricsForUser(11).subscribe({ error: errorSpy });

        const req = httpMock.expectOne({ method: 'GET', url: 'api/atlas/metrics/course/11/student' });
        req.flush(rawResponse);

        expect(errorSpy).toHaveBeenCalledOnce();
        expect((errorSpy.mock.calls[0][0] as Error).message).toBe('Unknown lecture unit type: com.example.UnknownLectureUnit');
    });
});
