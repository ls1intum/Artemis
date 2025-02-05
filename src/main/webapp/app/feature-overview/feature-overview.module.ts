import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FeatureOverviewComponent } from 'app/feature-overview/feature-overview.component';
import { featureOverviewState } from 'app/feature-overview/feature-overview.route';

const FEATURE_OVERVIEW_ROUTES = [...featureOverviewState];
@NgModule({
    imports: [RouterModule.forChild(FEATURE_OVERVIEW_ROUTES), FeatureOverviewComponent],
})
export class FeatureOverviewModule {}
