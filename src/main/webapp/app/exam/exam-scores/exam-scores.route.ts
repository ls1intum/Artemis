import { Route, Routes } from '@angular/router';
import { ExamScoresComponent } from 'app/exam/exam-scores/exam-scores.component';

export const examScoresRoute: Route[] = [
    {
        path: ':examId/scores',
        component: ExamScoresComponent,
    },
];

const EXAM_SCORES_ROUTES = [...examScoresRoute];

export const examScoresState: Routes = [
    {
        path: '',
        children: EXAM_SCORES_ROUTES,
    },
];
