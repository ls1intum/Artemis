import { Route } from '@angular/router';
import { ExamRoomsComponent } from 'app/core/admin/exam-rooms/exam-rooms.component';

export const examRoomsRoute: Route = {
    path: 'exam-rooms',
    component: ExamRoomsComponent,
    data: {
        pageTitle: 'artemisApp.examRooms.adminExamRoomUpload.pageTitle',
    },
};
