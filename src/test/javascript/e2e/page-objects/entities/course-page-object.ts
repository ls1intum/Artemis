import { browser, element, by } from 'protractor';

const expect = chai.expect;

export class CoursePage {
    createNewCourse = element(by.className('create-course'));


    async clickOnCreateNewCourse() {
        await this.createNewCourse.click();
    }

    async navigateIntoLastCourseQuizzes() {
        const rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        const courseId = await rows.last().element(by.css('td:nth-child(1) > a')).getText();

        await browser.sleep(1000);

        const exerciseDropdownButton = element(by.id(`exercises-button-${courseId}`));
        expect(exerciseDropdownButton.isPresent());
        await exerciseDropdownButton.click();

        await browser.sleep(1000);

        const quizExerciseButton = element(by.id(`quiz-exercises-button-${courseId}`));
        expect(quizExerciseButton.isPresent());
        await quizExerciseButton.click();

        return courseId;
    }
}

export class NewCoursePage {
    save = element(by.id('save-entity'));
    cancel = element(by.id('cancel-save'));
    title = element(by.id('field_title'));
    shortName = element(by.id('field_shortName'));
    studentGroupName = element(by.id('field_studentGroupName'));
    instructorGroupName = element(by.id('field_instructorGroupName'));

    async setTitle(title: string) {
        await this.title.sendKeys(title);
    }

    async setShortName(shortName: string) {
        await this.shortName.sendKeys(shortName);
    }

    async setStudentGroupName(studentGroupName: string) {
        await this.studentGroupName.sendKeys(studentGroupName);
    }

    async setInstructorGroupName(instructorGroupName: string) {
        await this.instructorGroupName.sendKeys(instructorGroupName);
    }

    async clickSave() {
        await this.save.click();
    }

    async clickCancel() {
        await this.cancel.click();
    }
}
