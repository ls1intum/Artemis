import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Subscription } from 'rxjs/Subscription';
import { StudentExam } from "app/entities/student-exam.model";
import { CourseManagementService } from "app/course/manage/course-management.service";
import { Course } from "app/entities/course.model";

@Component({
    selector: 'jhi-student-exams',
    templateUrl: './student-exams.component.html',
})
export class StudentExamsComponent implements OnInit {
    courseId: number;
    examId: number;
    studentExams: StudentExam[];
    course: Course;

    eventSubscriber: Subscription;
    paramSub: Subscription;
    isLoading: boolean;
    filteredStudentExamsSize = 0;

    constructor(private route: ActivatedRoute, private studentExamService: StudentExamService, private courseService: CourseManagementService) {}

    /**
     * Initialize the courseId and examId
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.loadAll();
    }

    loadAll() {
        this.paramSub = this.route.params.subscribe(() => {
            this.isLoading = true;
            this.studentExamService.findAllForExam(this.courseId, this.examId).subscribe((studentExamResponse) => {
                this.studentExams = studentExamResponse.body!;
            });
            this.courseService.find(this.courseId).subscribe((courseResponse) => {
                this.course = courseResponse.body!;
            });
        });
    }

    viewStudentExam(studentExam: StudentExam) {
        //TODO: go to student exam view
    }

    viewAssessment(studentExam: StudentExam) {
        //TODO: go to assessment
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
        if (studentExam.student) {
            const { login, name } = studentExam.student;
            return `${login} (${name})`;
        }
    };

    /**
     * Converts a participation object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param studentExam Student exam
     */
    searchTextFromParticipation = (studentExam: StudentExam): string => {
        return studentExam.student?.login || '';
    };
}
