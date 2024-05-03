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

interface ExerciseData {
    exerciseId: number;
    title: string;
    startDate: string;
    endDate: string;
    maxPoints: number;
    studentPoints: number;
    courseAveragePoints: number;
    studentCompletionDate: string;
    courseAverageCompletionDate: string;
}

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

    public exerciseData: ExerciseData[] = [
        {
            title: 'W01P01 - Hello World',
            endDate: '2022-10-30 16:50:00.000000',
            maxPoints: 2.0,
            startDate: '2022-10-19 14:00:00.000000',
            exerciseId: 8152,
            studentPoints: 2.0,
            courseAveragePoints: 1.945945945945946,
            studentCompletionDate: '2022-10-19 16:28:42.000000',
            courseAverageCompletionDate: '2022-10-23 03:07:37.832736',
        },
        {
            title: 'W01H01 - Hello World II',
            endDate: '2022-10-30 17:00:00.000000',
            maxPoints: 5.0,
            startDate: '2022-10-20 16:30:00.000000',
            exerciseId: 8153,
            studentPoints: 5.0,
            courseAveragePoints: 4.898792409430707,
            studentCompletionDate: '2022-10-31 11:42:40.000000',
            courseAverageCompletionDate: '2022-11-01 05:31:31.098518',
        },
        {
            title: 'W02H01 - Meet and Greet in der Antarktis',
            endDate: '2022-11-06 17:00:00.000000',
            maxPoints: 8.0,
            startDate: '2022-10-27 16:30:00.000000',
            exerciseId: 8226,
            studentPoints: 8.0,
            courseAveragePoints: 7.03698224852071,
            studentCompletionDate: '2022-11-09 14:14:02.000000',
            courseAverageCompletionDate: '2022-11-10 20:45:43.584923',
        },
        {
            title: 'W02H02 - Penguin Casino',
            endDate: '2022-11-06 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-10-27 16:30:00.000000',
            exerciseId: 8227,
            studentPoints: 5.0,
            courseAveragePoints: 3.928200129954516,
            studentCompletionDate: '2022-11-06 19:12:40.000000',
            courseAverageCompletionDate: '2022-11-09 22:59:27.025812',
        },
        {
            title: 'W02H03 - Wurzelhilfe fuer Fortgeschrittene',
            endDate: '2022-11-06 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-10-27 16:30:00.000000',
            exerciseId: 8229,
            studentPoints: 6.0,
            courseAveragePoints: 2.9699922057677317,
            studentCompletionDate: '2022-11-09 00:27:33.000000',
            courseAverageCompletionDate: '2022-11-10 03:52:43.119022',
        },
        {
            title: 'W03H01 - Array Funktionen',
            endDate: '2022-11-13 17:00:00.000000',
            maxPoints: 8.0,
            startDate: '2022-11-03 17:30:00.000000',
            exerciseId: 8304,
            studentPoints: 8.0,
            courseAveragePoints: 6.822103274559194,
            studentCompletionDate: '2022-11-13 17:08:31.000000',
            courseAverageCompletionDate: '2022-11-14 07:21:37.618478',
        },
        {
            title: 'W03H03 - Seam Carving',
            endDate: '2022-11-13 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-11-03 17:30:00.000000',
            exerciseId: 8310,
            studentPoints: 6.0,
            courseAveragePoints: 3.5321576763485476,
            studentCompletionDate: '2022-11-20 18:06:33.000000',
            courseAverageCompletionDate: '2022-11-20 22:42:17.820643',
        },
        {
            title: 'W03H02 - Testing fuer die Formel 1',
            endDate: '2022-11-13 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-11-03 17:30:00.000000',
            exerciseId: 8312,
            studentPoints: 6.0,
            courseAveragePoints: 5.624372759856631,
            studentCompletionDate: '2022-11-13 19:04:27.000000',
            courseAverageCompletionDate: '2022-11-13 23:35:12.856908',
        },
        {
            title: 'W04H01 - Rekursive Pingulogie',
            endDate: '2022-11-20 17:00:00.000000',
            maxPoints: 8.0,
            startDate: '2022-11-10 17:30:00.000000',
            exerciseId: 8386,
            studentPoints: 8.0,
            courseAveragePoints: 6.975151108126259,
            studentCompletionDate: '2022-11-23 18:59:53.000000',
            courseAverageCompletionDate: '2022-11-24 21:40:15.676333',
        },
        {
            title: 'W04H02 - Mega Merge Sort',
            endDate: '2022-11-20 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-11-10 17:30:00.000000',
            exerciseId: 8387,
            studentPoints: 6.0,
            courseAveragePoints: 3.564814814814815,
            studentCompletionDate: '2022-11-21 01:05:50.000000',
            courseAverageCompletionDate: '2022-11-22 01:29:54.782358',
        },
        {
            title: 'W04H03 - PenguHull',
            endDate: '2022-11-20 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-11-10 17:30:00.000000',
            exerciseId: 8388,
            studentPoints: 1.0,
            courseAveragePoints: 4.536437246963563,
            studentCompletionDate: '2022-11-24 16:27:16.000000',
            courseAverageCompletionDate: '2022-11-24 15:47:30.609312',
        },
        {
            title: 'W05H01 - Eismobile Modellieren',
            endDate: '2022-11-27 17:00:00.000000',
            maxPoints: 8.0,
            startDate: '2022-11-17 17:30:00.000000',
            exerciseId: 8485,
            studentPoints: 6.0,
            courseAveragePoints: 6.930358350236647,
            studentCompletionDate: '2022-12-01 14:44:44.000000',
            courseAverageCompletionDate: '2022-12-02 22:02:24.620690',
        },
        {
            title: 'W05H02 - The Pingu Network',
            endDate: '2022-12-04 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-11-17 17:30:00.000000',
            exerciseId: 8494,
            studentPoints: 6.0,
            courseAveragePoints: 4.967537050105857,
            studentCompletionDate: '2022-11-28 11:30:50.000000',
            courseAverageCompletionDate: '2022-11-28 19:40:32.865914',
        },
        {
            title: 'W05H03 - A Messenger is Still on the List',
            endDate: '2022-12-04 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-11-17 17:30:00.000000',
            exerciseId: 8495,
            studentPoints: 4.0,
            courseAveragePoints: 3.626865671641791,
            studentCompletionDate: '2022-11-28 17:14:59.000000',
            courseAverageCompletionDate: '2022-11-29 10:29:36.531509',
        },
        {
            title: 'W06H01 - Listige Launemacher',
            endDate: '2022-12-04 17:00:00.000000',
            maxPoints: 8.0,
            startDate: '2022-11-24 17:30:00.000000',
            exerciseId: 8541,
            studentPoints: 6.0,
            courseAveragePoints: 6.697080291970803,
            studentCompletionDate: '2022-12-06 18:56:06.000000',
            courseAverageCompletionDate: '2022-12-08 23:09:50.174428',
        },
        {
            title: 'W07H01 - Text zu UML',
            endDate: '2022-12-11 17:00:00.000000',
            maxPoints: 8.0,
            startDate: '2022-12-01 17:30:00.000000',
            exerciseId: 8630,
            studentPoints: 8.0,
            courseAveragePoints: 7.385869565217392,
            studentCompletionDate: '2022-12-19 08:26:19.000000',
            courseAverageCompletionDate: '2022-12-16 23:10:54.093151',
        },
        {
            title: 'W06H02 - PUM Server Upgrade',
            endDate: '2022-12-11 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-11-24 17:30:00.000000',
            exerciseId: 8549,
            studentPoints: 6.0,
            courseAveragePoints: 4.929012345679013,
            studentCompletionDate: '2022-12-05 16:09:20.000000',
            courseAverageCompletionDate: '2022-12-07 04:43:30.874421',
        },
        {
            title: 'W06H03 - Fun with Graphs',
            endDate: '2022-12-11 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-11-24 17:30:00.000000',
            exerciseId: 8553,
            studentPoints: 3.0,
            courseAveragePoints: 3.823529411764706,
            studentCompletionDate: '2022-12-06 06:18:36.000000',
            courseAverageCompletionDate: '2022-12-07 20:29:51.523012',
        },
        {
            title: 'W07H02 - Sicherheit Geht Vor',
            endDate: '2022-12-18 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-12-01 17:30:00.000000',
            exerciseId: 8631,
            studentPoints: 5.0,
            courseAveragePoints: 4.507518796992481,
            studentCompletionDate: '2022-12-12 15:19:04.000000',
            courseAverageCompletionDate: '2022-12-12 18:46:42.495920',
        },
        {
            title: 'W07H03 - Pingu Sim',
            endDate: '2022-12-18 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-12-01 17:30:00.000000',
            exerciseId: 8632,
            studentPoints: 5.0,
            courseAveragePoints: 3.530556061987238,
            studentCompletionDate: '2022-12-17 18:06:35.000000',
            courseAverageCompletionDate: '2022-12-18 04:59:47.701366',
        },
        {
            title: 'W08H02 - Effiziente Aufgabenverwaltung',
            endDate: '2022-12-18 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-12-08 17:30:00.000000',
            exerciseId: 8699,
            studentPoints: 5.0,
            courseAveragePoints: 5.050724637681159,
            studentCompletionDate: '2022-12-22 08:28:33.000000',
            courseAverageCompletionDate: '2022-12-22 21:57:17.201383',
        },
        {
            title: 'W08H01 - Simple Generics',
            endDate: '2023-01-08 17:00:00.000000',
            maxPoints: 8.0,
            startDate: '2022-12-08 17:30:00.000000',
            exerciseId: 8698,
            studentPoints: 8.0,
            courseAveragePoints: 7.7473363774733635,
            studentCompletionDate: '2022-12-19 02:08:10.000000',
            courseAverageCompletionDate: '2022-12-19 07:55:41.151403',
        },
        {
            title: 'W08B01 - HPC Polymorphism',
            endDate: '2023-01-08 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-12-08 17:30:00.000000',
            exerciseId: 8701,
            studentPoints: 6.0,
            courseAveragePoints: 5.319327731092437,
            studentCompletionDate: '2022-12-19 03:08:32.000000',
            courseAverageCompletionDate: '2022-12-19 05:19:50.488255',
        },
        {
            title: 'W10Xmas01 - Memey Christmas',
            endDate: '2023-01-15 17:00:00.000000',
            maxPoints: 3.0,
            startDate: '2022-12-22 17:30:00.000000',
            exerciseId: 8776,
            studentPoints: 3.0,
            courseAveragePoints: 3.0,
            studentCompletionDate: '2023-01-19 01:15:05.000000',
            courseAverageCompletionDate: '2023-01-18 14:55:15.318630',
        },
        {
            title: 'W09H02 - Bahn Analyse mit Streams',
            endDate: '2023-01-15 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2022-12-15 17:30:00.000000',
            exerciseId: 8741,
            studentPoints: 1.0,
            courseAveragePoints: 4.311440677966102,
            studentCompletionDate: '2023-01-09 01:11:57.000000',
            courseAverageCompletionDate: '2023-01-09 20:16:16.222738',
        },
        {
            title: 'W09H01 - Pinguin Ausflug',
            endDate: '2023-01-15 17:00:00.000000',
            maxPoints: 13.0,
            startDate: '2022-12-15 17:30:00.000000',
            exerciseId: 8738,
            studentPoints: 13.0,
            courseAveragePoints: 12.534536891679748,
            studentCompletionDate: '2023-01-09 02:09:06.000000',
            courseAverageCompletionDate: '2023-01-08 12:18:35.481592',
        },
        {
            title: 'W10H01 - Pengu Trials',
            endDate: '2023-01-22 17:00:00.000000',
            maxPoints: 8.0,
            startDate: '2023-01-05 17:30:00.000000',
            exerciseId: 8840,
            studentPoints: 6.0,
            courseAveragePoints: 6.742834394904459,
            studentCompletionDate: '2023-01-16 10:46:52.000000',
            courseAverageCompletionDate: '2023-01-16 15:55:57.381282',
        },
        {
            title: 'W10H02 - Pinguin Schneeballschlacht',
            endDate: '2023-01-22 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2023-01-05 17:30:00.000000',
            exerciseId: 8841,
            studentPoints: 4.0,
            courseAveragePoints: 4.254787676935886,
            studentCompletionDate: '2023-01-16 01:16:49.000000',
            courseAverageCompletionDate: '2023-01-16 14:17:16.289764',
        },
        {
            title: 'W11H01 - Baumige Hausaufgabenhilfe',
            endDate: '2023-01-22 17:00:00.000000',
            maxPoints: 8.0,
            startDate: '2023-01-12 17:30:00.000000',
            exerciseId: 8868,
            studentPoints: 5.0,
            courseAveragePoints: 6.998008097165993,
            studentCompletionDate: '2023-01-26 10:43:14.474000',
            courseAverageCompletionDate: '2023-01-27 22:03:31.899859',
        },
        {
            title: 'W11H02 - Pengu Armstrong',
            endDate: '2023-01-22 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2023-01-12 17:30:00.000000',
            exerciseId: 8870,
            studentPoints: 6.0,
            courseAveragePoints: 5.798537774167344,
            studentCompletionDate: '2023-01-25 18:27:02.132000',
            courseAverageCompletionDate: '2023-01-27 09:30:04.678150',
        },
        {
            title: 'W11H03 - PinguChat',
            endDate: '2023-01-23 21:00:00.000000',
            maxPoints: 6.0,
            startDate: '2023-01-13 17:30:00.000000',
            exerciseId: 8876,
            studentPoints: 0.0,
            courseAveragePoints: 3.829369183040331,
            studentCompletionDate: '2023-02-11 17:16:31.763000',
            courseAverageCompletionDate: '2023-02-14 01:47:15.700378',
        },
        {
            title: 'W12H01 - Work Life Balance of Threaduins',
            endDate: '2023-02-05 17:00:00.000000',
            maxPoints: 8.0,
            startDate: '2022-12-22 17:30:00.000000',
            exerciseId: 8799,
            studentPoints: 2.0,
            courseAveragePoints: 7.3930921052631575,
            studentCompletionDate: '2023-01-30 09:24:22.742000',
            courseAverageCompletionDate: '2023-01-30 16:34:04.129355',
        },
        {
            title: 'W13H01 - Von Teilern und Vielfachen',
            endDate: '2023-02-12 17:00:00.000000',
            maxPoints: 8.0,
            startDate: '2023-01-26 17:30:00.000000',
            exerciseId: 8935,
            studentPoints: 7.0,
            courseAveragePoints: 8.722318339100346,
            studentCompletionDate: '2023-02-06 22:00:58.317000',
            courseAverageCompletionDate: '2023-02-07 02:04:16.120872',
        },
        {
            title: 'W13H02 - Kryptographuine',
            endDate: '2023-02-12 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2023-01-26 17:30:00.000000',
            exerciseId: 8936,
            studentPoints: 3.0,
            courseAveragePoints: 4.13921568627451,
            studentCompletionDate: '2023-02-06 22:00:04.883000',
            courseAverageCompletionDate: '2023-02-07 07:19:30.730250',
        },
        {
            title: 'W13H03 - Pingu JVM',
            endDate: '2023-02-12 17:00:00.000000',
            maxPoints: 6.0,
            startDate: '2023-01-26 17:30:00.000000',
            exerciseId: 9020,
            studentPoints: 0.0,
            courseAveragePoints: 3.562867256637168,
            studentCompletionDate: '2023-02-06 02:02:45.889000',
            courseAverageCompletionDate: '2023-02-06 06:04:34.567881',
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
