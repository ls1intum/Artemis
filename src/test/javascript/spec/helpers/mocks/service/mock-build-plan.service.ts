import { of } from 'rxjs';
import { BuildPlan } from 'app/entities/build-plan.model';

export class MockBuildPlanService {
    getBuildPlan = (exerciseId: number) => of({});
    putBuildPlan = (exerciseId: number, buildPlan: BuildPlan) => of({});
}
