import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Subscription } from 'rxjs/Subscription';
import { StudentExam } from 'app/entities/student-exam.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AlertService } from 'app/core/alert/alert.service';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-student-exams',
    templateUrl: './student-exams.component.html',
})
export class StudentExamsComponent implements OnInit {
    courseId: number;
    examId: number;
    studentExams: StudentExam[];
    course: Course;
    exam: Exam;

    eventSubscriber: Subscription;
    paramSub: Subscription;
    isLoading: boolean;
    filteredStudentExamsSize = 0;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private studentExamService: StudentExamService,
        private courseService: CourseManagementService,
        private jhiAlertService: AlertService,
    ) {}

    /**
     * Initialize the courseId and examId
     */
    ngOnInit(): void {
        this.isLoading = true;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.loadAll();
    }

    private loadAll() {
        this.paramSub = this.route.params.subscribe(() => {
            this.studentExamService.findAllForExam(this.courseId, this.examId).subscribe((res) => {
                this.setStudentExams(res.body);
            });
            this.courseService.find(this.courseId).subscribe((courseResponse) => {
                this.course = courseResponse.body!;
            });
            this.examManagementService.find(this.courseId, this.examId).subscribe((examResponse) => {
                this.exam = examResponse.body!;
            });
            this.isLoading = false;
        });
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    viewAssessment(studentExam: StudentExam) {
        // TODO: go to assessment
    }

    /**
     * Generate all student exams for the exam on the server and handle the result.
     */
    generateStudentExams() {
        this.examManagementService.generateStudentExams(this.courseId, this.examId).subscribe(
            () => this.loadAll(),
            (err) => this.handleError(err.error),
        );
    }

    /**
     * Starts all the exercises of the student exams that belong to the exam
     */
    startExercises() {
        this.examManagementService.startExercises(this.courseId, this.examId).subscribe(
            () => {
                this.loadAll();
            },
            (err) => this.handleError(err.error),
        );
    }

    /**
     * Update the number of filtered participations
     *
     * @param filteredStudentExamsSize Total number of participations after filters have been applied
     */
    handleStudentExamsSizeChange = (filteredStudentExamsSize: number) => {
        this.filteredStudentExamsSize = filteredStudentExamsSize;
    };

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param studentExam
     */
    searchResultFormatter = (studentExam: StudentExam) => {
        if (studentExam.user) {
            return `${studentExam.user.login} (${studentExam.user.name})`;
        }
    };

    /**
     * Converts a student exam object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param studentExam Student exam
     */
    searchTextFromStudentExam = (studentExam: StudentExam): string => {
        return studentExam.user?.login || '';
    };

    private setStudentExams(studentExams: any): void {
        if (studentExams) {
            this.studentExams = studentExams;
        }
    }

    private handleError(error: any): void {
        this.jhiAlertService.error(error.errorKey);
    }
}
