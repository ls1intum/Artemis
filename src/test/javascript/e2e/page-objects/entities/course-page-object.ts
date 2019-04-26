import { browser, element, by } from 'protractor';

const expect = chai.expect;

export class CoursePage {
    createNewCourse = element(by.className('create-course'));

    async clickOnCreateNewCourse() {
        await this.createNewCourse.click();
    }

    async navigateIntoLastCourseExercises() {
        const rows = element.all(by.tagName('tbody')).all(by.tagName('tr'));
        const courseId = await rows
            .last()
            .element(by.css('td:nth-child(1) > a'))
            .getText();

        await browser.sleep(1000);

        const exercisesButton = element(by.id(`exercises-button-${courseId}`));
        expect(exercisesButton.isPresent());
        await exercisesButton.click();

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
    tutorGroupName = element(by.id('field_teachingAssistantGroupName'));

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

    async setTutorGroupName(tutorGroupName: string) {
        await this.tutorGroupName.sendKeys(tutorGroupName);
    }

    async clickSave() {
        await this.save.click();
    }

    async clickCancel() {
        await this.cancel.click();
    }
}
