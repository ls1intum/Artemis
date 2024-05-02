import { Component, OnDestroy, OnInit } from '@angular/core';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Subscription, forkJoin } from 'rxjs';
import { Exercise } from 'app/entities/exercise.model';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { Competency } from 'app/entities/competency.model';
import { onError } from 'app/shared/util/global.utils';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExercisePerformance } from 'app/overview/course-dashboard/course-exercise-performance/course-exercise-performance.component';

@Component({
    selector: 'jhi-course-dashboard',
    templateUrl: './course-dashboard.component.html',
})
export class CourseDashboardComponent implements OnInit, OnDestroy {
    courseId: number;
    exerciseId: number;
    isLoading = false;

    public competencies: Competency[] = [];
    private prerequisites: Competency[] = [];

    private paramSubscription?: Subscription;
    private courseUpdatesSubscription?: Subscription;
    private courseExercises: Exercise[] = [];
    public course?: Course;
    public data: any;

    public exercisePerformances: ExercisePerformance[] = [
        {
            title: 'W01P01 - Hello World',
            dueDate: '2022-10-30 16:50:00.000000',
            maxPoints: 2.0,
            shortName: 'w01p01',
            exerciseId: 8152,
            releaseDate: '2022-10-19 14:00:00.000000',
            studentPoints: 2.0,
            courseAveragePoints: 1.945945945945946,
        },
        {
            title: 'W01H01 - Hello World II',
            dueDate: '2022-10-30 17:00:00.000000',
            maxPoints: 5.0,
            shortName: 'w01h01',
            exerciseId: 8153,
            releaseDate: '2022-10-20 16:30:00.000000',
            studentPoints: 5.0,
            courseAveragePoints: 4.898792409430707,
        },
        {
            title: 'W02H01 - Meet and Greet in der Antarktis',
            dueDate: '2022-11-06 17:00:00.000000',
            maxPoints: 8.0,
            shortName: 'w02h01',
            exerciseId: 8226,
            releaseDate: '2022-10-27 16:30:00.000000',
            studentPoints: 8.0,
            courseAveragePoints: 7.03698224852071,
        },
        {
            title: 'W02H02 - Penguin Casino',
            dueDate: '2022-11-06 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w02h02',
            exerciseId: 8227,
            releaseDate: '2022-10-27 16:30:00.000000',
            studentPoints: 5.0,
            courseAveragePoints: 3.928200129954516,
        },
        {
            title: 'W02H03 - Wurzelhilfe fuer Fortgeschrittene',
            dueDate: '2022-11-06 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w02h03',
            exerciseId: 8229,
            releaseDate: '2022-10-27 16:30:00.000000',
            studentPoints: 6.0,
            courseAveragePoints: 2.9699922057677317,
        },
        {
            title: 'W03H01 - Array Funktionen',
            dueDate: '2022-11-13 17:00:00.000000',
            maxPoints: 8.0,
            shortName: 'w03h01',
            exerciseId: 8304,
            releaseDate: '2022-11-03 17:30:00.000000',
            studentPoints: 8.0,
            courseAveragePoints: 6.822103274559194,
        },
        {
            title: 'W03H03 - Seam Carving',
            dueDate: '2022-11-13 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w03h03',
            exerciseId: 8310,
            releaseDate: '2022-11-03 17:30:00.000000',
            studentPoints: 6.0,
            courseAveragePoints: 3.5321576763485476,
        },
        {
            title: 'W03H02 - Testing fuer die Formel 1',
            dueDate: '2022-11-13 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w03h02',
            exerciseId: 8312,
            releaseDate: '2022-11-03 17:30:00.000000',
            studentPoints: 6.0,
            courseAveragePoints: 5.624372759856631,
        },
        {
            title: 'W04H01 - Rekursive Pingulogie',
            dueDate: '2022-11-20 17:00:00.000000',
            maxPoints: 8.0,
            shortName: 'w04h01',
            exerciseId: 8386,
            releaseDate: '2022-11-10 17:30:00.000000',
            studentPoints: 8.0,
            courseAveragePoints: 6.975151108126259,
        },
        {
            title: 'W04H02 - Mega Merge Sort',
            dueDate: '2022-11-20 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w04h02',
            exerciseId: 8387,
            releaseDate: '2022-11-10 17:30:00.000000',
            studentPoints: 6.0,
            courseAveragePoints: 3.564814814814815,
        },
        {
            title: 'W04H03 - PenguHull',
            dueDate: '2022-11-20 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w04h03',
            exerciseId: 8388,
            releaseDate: '2022-11-10 17:30:00.000000',
            studentPoints: 1.0,
            courseAveragePoints: 4.536437246963563,
        },
        {
            title: 'W05H01 - Eismobile Modellieren',
            dueDate: '2022-11-27 17:00:00.000000',
            maxPoints: 8.0,
            exerciseId: 8485,
            releaseDate: '2022-11-17 17:30:00.000000',
            studentPoints: 6.0,
            courseAveragePoints: 6.930358350236647,
        },
        {
            title: 'W05H02 - The Pingu Network',
            dueDate: '2022-12-04 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w05h02',
            exerciseId: 8494,
            releaseDate: '2022-11-17 17:30:00.000000',
            studentPoints: 6.0,
            courseAveragePoints: 4.967537050105857,
        },
        {
            title: 'W05H03 - A Messenger is Still on the List',
            dueDate: '2022-12-04 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w05h03',
            exerciseId: 8495,
            releaseDate: '2022-11-17 17:30:00.000000',
            studentPoints: 4.0,
            courseAveragePoints: 3.626865671641791,
        },
        {
            title: 'W06H01 - Listige Launemacher',
            dueDate: '2022-12-04 17:00:00.000000',
            maxPoints: 8.0,
            shortName: 'w06h01',
            exerciseId: 8541,
            releaseDate: '2022-11-24 17:30:00.000000',
            studentPoints: 6.0,
            courseAveragePoints: 6.697080291970803,
        },
        {
            title: 'W07H01 - Text zu UML',
            dueDate: '2022-12-11 17:00:00.000000',
            maxPoints: 8.0,
            exerciseId: 8630,
            releaseDate: '2022-12-01 17:30:00.000000',
            studentPoints: 8.0,
            courseAveragePoints: 7.385869565217392,
        },
        {
            title: 'W06H02 - PUM Server Upgrade',
            dueDate: '2022-12-11 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w06h02',
            exerciseId: 8549,
            releaseDate: '2022-11-24 17:30:00.000000',
            studentPoints: 6.0,
            courseAveragePoints: 4.929012345679013,
        },
        {
            title: 'W06H03 - Fun with Graphs',
            dueDate: '2022-12-11 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w06h03',
            exerciseId: 8553,
            releaseDate: '2022-11-24 17:30:00.000000',
            studentPoints: 3.0,
            courseAveragePoints: 3.823529411764706,
        },
        {
            title: 'W07H02 - Sicherheit Geht Vor',
            dueDate: '2022-12-18 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w07h02',
            exerciseId: 8631,
            releaseDate: '2022-12-01 17:30:00.000000',
            studentPoints: 5.0,
            courseAveragePoints: 4.507518796992481,
        },
        {
            title: 'W07H03 - Pingu Sim',
            dueDate: '2022-12-18 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w07h03',
            exerciseId: 8632,
            releaseDate: '2022-12-01 17:30:00.000000',
            studentPoints: 5.0,
            courseAveragePoints: 3.530556061987238,
        },
        {
            title: 'W08H02 - Effiziente Aufgabenverwaltung',
            dueDate: '2022-12-18 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w08h02',
            exerciseId: 8699,
            releaseDate: '2022-12-08 17:30:00.000000',
            studentPoints: 5.0,
            courseAveragePoints: 5.050724637681159,
        },
        {
            title: 'W08H01 - Simple Generics',
            dueDate: '2023-01-08 17:00:00.000000',
            maxPoints: 8.0,
            shortName: 'w08h01',
            exerciseId: 8698,
            releaseDate: '2022-12-08 17:30:00.000000',
            studentPoints: 8.0,
            courseAveragePoints: 7.7473363774733635,
        },
        {
            title: 'W08B01 - HPC Polymorphism',
            dueDate: '2023-01-08 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w08b01',
            exerciseId: 8701,
            releaseDate: '2022-12-08 17:30:00.000000',
            studentPoints: 6.0,
            courseAveragePoints: 5.319327731092437,
        },
        {
            title: 'W10Xmas01 - Memey Christmas',
            dueDate: '2023-01-15 17:00:00.000000',
            maxPoints: 3.0,
            exerciseId: 8776,
            releaseDate: '2022-12-22 17:30:00.000000',
            studentPoints: 3.0,
            courseAveragePoints: 3.0,
        },
        {
            title: 'W09H02 - Bahn Analyse mit Streams',
            dueDate: '2023-01-15 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w09h02',
            exerciseId: 8741,
            releaseDate: '2022-12-15 17:30:00.000000',
            studentPoints: 1.0,
            courseAveragePoints: 4.311440677966102,
        },
        {
            title: 'W09H01 - Pinguin Ausflug',
            dueDate: '2023-01-15 17:00:00.000000',
            maxPoints: 13.0,
            shortName: 'w09h01',
            exerciseId: 8738,
            releaseDate: '2022-12-15 17:30:00.000000',
            studentPoints: 13.0,
            courseAveragePoints: 12.534536891679748,
        },
        {
            title: 'W10H01 - Pengu Trials',
            dueDate: '2023-01-22 17:00:00.000000',
            maxPoints: 8.0,
            shortName: 'w10h01',
            exerciseId: 8840,
            releaseDate: '2023-01-05 17:30:00.000000',
            studentPoints: 6.0,
            courseAveragePoints: 6.742834394904459,
        },
        {
            title: 'W10H02 - Pinguin Schneeballschlacht',
            dueDate: '2023-01-22 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w10h02',
            exerciseId: 8841,
            releaseDate: '2023-01-05 17:30:00.000000',
            studentPoints: 4.0,
            courseAveragePoints: 4.254787676935886,
        },
        {
            title: 'W11H01 - Baumige Hausaufgabenhilfe',
            dueDate: '2023-01-22 17:00:00.000000',
            maxPoints: 8.0,
            exerciseId: 8868,
            releaseDate: '2023-01-12 17:30:00.000000',
            studentPoints: 5.0,
            courseAveragePoints: 6.998008097165993,
        },
        {
            title: 'W11H02 - Pengu Armstrong',
            dueDate: '2023-01-22 17:00:00.000000',
            maxPoints: 6.0,
            exerciseId: 8870,
            releaseDate: '2023-01-12 17:30:00.000000',
            studentPoints: 6.0,
            courseAveragePoints: 5.798537774167344,
        },
        {
            title: 'W11H03 - PinguChat',
            dueDate: '2023-01-23 21:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w11h03',
            exerciseId: 8876,
            releaseDate: '2023-01-13 17:30:00.000000',
            studentPoints: 0.0,
            courseAveragePoints: 3.829369183040331,
        },
        {
            title: 'W12H01 - Work Life Balance of Threaduins',
            dueDate: '2023-02-05 17:00:00.000000',
            maxPoints: 8.0,
            shortName: 'w12h01',
            exerciseId: 8799,
            releaseDate: '2022-12-22 17:30:00.000000',
            studentPoints: 2.0,
            courseAveragePoints: 7.3930921052631575,
        },
        {
            title: 'W13H01 - Von Teilern und Vielfachen',
            dueDate: '2023-02-12 17:00:00.000000',
            maxPoints: 8.0,
            shortName: 'w13h01',
            exerciseId: 8935,
            releaseDate: '2023-01-26 17:30:00.000000',
            studentPoints: 7.0,
            courseAveragePoints: 8.722318339100346,
        },
        {
            title: 'W13H02 - Kryptographuine',
            dueDate: '2023-02-12 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w13h02',
            exerciseId: 8936,
            releaseDate: '2023-01-26 17:30:00.000000',
            studentPoints: 3.0,
            courseAveragePoints: 4.13921568627451,
        },
        {
            title: 'W13H03 - Pingu JVM',
            dueDate: '2023-02-12 17:00:00.000000',
            maxPoints: 6.0,
            shortName: 'w13h03',
            exerciseId: 9020,
            releaseDate: '2023-01-26 17:30:00.000000',
            studentPoints: 0.0,
            courseAveragePoints: 3.562867256637168,
        },
    ];

    constructor(
        private courseStorageService: CourseStorageService,
        private alertService: AlertService,
        private route: ActivatedRoute,
        private competencyService: CompetencyService,
    ) {}

    ngOnInit(): void {
        this.paramSubscription = this.route.parent?.parent?.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });
        this.setCourse(this.courseStorageService.getCourse(this.courseId));

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.setCourse(course);
        });
    }

    private onCourseLoad(): void {
        if (this.course?.exercises) {
            this.courseExercises = this.course.exercises;
        }
    }
    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.courseUpdatesSubscription?.unsubscribe();
    }

    /**
     * Loads all prerequisites and competencies for the course
     */
    loadCompetencies() {
        this.isLoading = true;
        forkJoin([this.competencyService.getAllForCourse(this.courseId), this.competencyService.getAllPrerequisitesForCourse(this.courseId)]).subscribe({
            next: ([competencies, prerequisites]) => {
                this.competencies = competencies.body!;
                this.prerequisites = prerequisites.body!;
                // Also update the course, so we do not need to fetch again next time
                if (this.course) {
                    this.course.competencies = this.competencies;
                    this.course.prerequisites = this.prerequisites;
                }
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    private setCourse(course?: Course) {
        this.course = course;
        this.onCourseLoad();
        // Note: this component is only shown if there are at least 1 competencies or at least 1 prerequisites, so if they do not exist, we load the data from the server
        if (this.course && ((this.course.competencies && this.course.competencies.length > 0) || (this.course.prerequisites && this.course.prerequisites.length > 0))) {
            this.competencies = this.course.competencies || [];
            this.prerequisites = this.course.prerequisites || [];
        } else {
            this.loadCompetencies();
        }
    }

    protected readonly FeatureToggle = FeatureToggle;
}
