import { findParamInRouteHierarchy } from 'app/shared/util/navigation.utils';
import { ActivatedRoute } from '@angular/router';

export type GradingKeyUrlParams = {
    courseId: number;
    examId?: number;
    isExam: boolean;
    forBonus: boolean;
    studentGradeOrBonusPointsOrGradeBonus?: string;
};

export function loadGradingKeyUrlParams(route: ActivatedRoute): GradingKeyUrlParams {
    // Note: This component is used in multiple routes, so it can be lazy loaded. Also, courseId and examId can be
    // found on different levels of hierarchy tree (on the same level or a parent or a grandparent, etc.).
    const courseId = Number(findParamInRouteHierarchy(route, 'courseId'));
    let examId = undefined;
    let isExam = false;

    const examIdParam = findParamInRouteHierarchy(route, 'examId');
    if (examIdParam) {
        examId = Number(examIdParam);
        isExam = true;
    }
    const forBonus = !!route.snapshot.data['forBonus'];

    /** If needed queryParam is available, it is available on {@link GradingKeyOverviewComponent} so no need to traverse the hierarchy like params above. */
    const studentGradeOrBonusPointsOrGradeBonus = route.snapshot.queryParams['grade'];

    return {
        courseId,
        examId,
        forBonus,
        isExam,
        studentGradeOrBonusPointsOrGradeBonus: studentGradeOrBonusPointsOrGradeBonus,
    };
}
