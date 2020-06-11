import { Component, Input, OnInit } from '@angular/core';
import * as moment from 'moment';
import { SafeHtml } from '@angular/platform-browser';
import { Exam } from 'app/entities/exam.model';

import { ArtemisMarkdownService } from 'app/shared/markdown.service';

import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-exam-cover',
    templateUrl: './exam-cover.component.html',
    styles: [],
})
export class ExamCoverComponent implements OnInit {
    @Input() exam: Exam;
    @Input() startView: boolean;

    course: Course;
    courseId = 0;

    title: string;
    submitEnabled: boolean;
    confirmed: boolean;

    formattedGeneralInformation: SafeHtml | null;
    formattedConfirmationText: SafeHtml | null;

    constructor(private courseService: CourseManagementService, private route: ActivatedRoute, private artemisMarkdown: ArtemisMarkdownService) {}

    /**
     * initializes the component
     */
    ngOnInit(): void {
        // mocks values
        this.mock();

        this.confirmed = false;
        this.submitEnabled = false;

        if (this.startView) {
            this.formattedGeneralInformation = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.startText);
            this.formattedConfirmationText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationStartText);
        } else {
            this.formattedGeneralInformation = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.endText);
            this.formattedConfirmationText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationEndText);
        }
    }

    /**
     * checks whether confirmation checkbox has been checked and change it's value
     * TODO add alerts for when exam has started but no confirmation etc.?
     */
    updateConfirmation() {
        this.confirmed = !this.confirmed;
    }

    /**
     * temporary, find more efficient way of enabling button after exam has started, e.g. timeout
     */
    enableButton() {
        if (this.confirmed) {
            if (this.exam && this.exam.startDate && this.exam.startDate.isBefore(moment())) {
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    /**
     * TODO: add session management, this function is bound to the start exam button
     */
    startExam() {}

    // TODO remove
    mock() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.courseService.find(this.courseId).subscribe((courseResponse) => (this.course = courseResponse.body!));

        this.title = 'ARTEMIS 101 ENDTERM';
        this.startView = true;
        this.exam = {
            id: 1,
            startText:
                '- *You can answer exam questions either in German or English. Once you have selected a language, you cannot change it.*\n' +
                '- Physically turn off all electronic devices, put them into your bag and close the bag.\n' +
                '- ```No aids are permitted except one handwritten DIN A4 sheet of paper and a language dictionary without any notes.``` We reserve the right to check this.\n' +
                '- No questions related to the exam content will be answered.\n' +
                '- The exam consists of questions worth 60 points and is 90 minutes long. Use the amount of points and the text of the question to determine the appropriate length of your answer. Please dont give too long answers...\n' +
                '- Explain concepts and differences in your own words. Answering an exam question only with a\n' +
                'definition from the slides **will not** give any points.',
            endText: 'You can answer exam questions either in G o E.',
            confirmationStartText: 'Hereby I confirm that I will obey the stated rules and will write this exam alone with no outside help.',
            confirmationEndText: 'Hereby I confirm that I will obey the stated rules',
            startDate: moment().add(10, 'minutes'),
            endDate: moment(),
            visibleDate: null,
            maxPoints: null,
            numberOfExercisesInExam: null,
            randomizeExerciseOrder: true,
            course: this.course,
            exerciseGroups: null,
            studentExams: null,
        };
    }
}
