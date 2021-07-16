import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SERVER_API_URL } from 'app/app.constants';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';

import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateTestingModule } from '../helpers/mocks/service/mock-translate.service';
import { Notification } from 'app/entities/notification.model';
import { LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE } from 'app/shared/notification/notification.constants';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { MockRouter } from '../helpers/mocks/mock-router';

chai.use(sinonChai);
const expect = chai.expect;

describe('Logs Service', () => {
    let notificationService: NotificationService;
    let httpMock: HttpTestingController;

    const router = new MockRouter();
    const navigateSpy = sinon.spy(router, 'navigate');

    const generateQuizNotification = (id: number) => {
        const generatedNotification = { id, title: 'Quiz started', text: 'Quiz "Proxy pattern" just started.' } as Notification;
        generatedNotification.target = JSON.stringify({ mainPage: 'courses', course: 1, entity: 'exercise', id: 1 });
        return generatedNotification;
    };
    const quizNotification = generateQuizNotification(1);

    const generateExamExerciseUpdateNotification = () => {
        const generatedNotification = { title: LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE, text: 'Fixed mistake' } as Notification;
        generatedNotification.target = JSON.stringify({ mainPage: 'courses', course: 1, entity: 'exams', exam: 1, exercise: 7, problemStatement: 'Fixed Problem Statement' });
        return generatedNotification;
    };
    const examExerciseUpdateNotification = generateExamExerciseUpdateNotification();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, TranslateTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .compileComponents()
            .then(() => {
                notificationService = TestBed.inject(NotificationService);
                httpMock = TestBed.inject(HttpTestingController);
            });
    });

    afterEach(() => {
        httpMock.verify();
        sinon.restore();
    });

    describe('Service methods', () => {
        it('should call correct URL', () => {
            notificationService.query().subscribe(() => {});
            const req = httpMock.expectOne({ method: 'GET' });
            const infoUrl = SERVER_API_URL + 'api/notifications';
            expect(req.request.url).to.equal(infoUrl);
        });

        it('should navigate to notification target', () => {
            notificationService.interpretNotification(quizNotification);
            //expect(navigateSpy).to.have.been.called;
            expect(navigateSpy).to.have.been.calledOnce;
        });
    });
});
