import { element, by } from 'protractor';

export class CoursePage {
    createNewCourse = element(by.className('create-course'));

    async clickOnCreateNewCourse() {
        await this.createNewCourse.click();
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
