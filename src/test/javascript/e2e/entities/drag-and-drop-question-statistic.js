'use strict';

describe('DragAndDropQuestionStatistic e2e test', function () {

    var username = element(by.id('username'));
    var password = element(by.id('password'));
    var entityMenu = element(by.id('entity-menu'));
    var accountMenu = element(by.id('account-menu'));
    var login = element(by.id('login'));
    var logout = element(by.id('logout'));

    beforeAll(function () {
        browser.get('/');

        accountMenu.click();
        login.click();

        username.sendKeys('admin');
        password.sendKeys('admin');
        element(by.css('button[type=submit]')).click();
    });

    it('should load DragAndDropQuestionStatistics', function () {
        entityMenu.click();
        element.all(by.css('[ui-sref="drag-and-drop-question-statistic"]')).first().click().then(function() {
            element.all(by.css('h2')).first().getAttribute('data-translate').then(function (value) {
                expect(value).toMatch(/arTeMiSApp.dragAndDropQuestionStatistic.home.title/);
            });
        });
    });

    it('should load create DragAndDropQuestionStatistic dialog', function () {
        element(by.css('[ui-sref="drag-and-drop-question-statistic.new"]')).click().then(function() {
            element(by.css('h4.modal-title')).getAttribute('data-translate').then(function (value) {
                expect(value).toMatch(/arTeMiSApp.dragAndDropQuestionStatistic.home.createOrEditLabel/);
            });
            element(by.css('button.close')).click();
        });
    });

    afterAll(function () {
        accountMenu.click();
        logout.click();
    });
});
