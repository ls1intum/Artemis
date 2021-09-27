import { NgModule } from '@angular/core';
import { MetricsComponent } from './metrics.component';
import { JvmMemoryComponent } from './blocks/jvm-memory/jvm-memory.component';
import { JvmThreadsComponent } from './blocks/jvm-threads/jvm-threads.component';
import { MetricsCacheComponent } from './blocks/metrics-cache/metrics-cache.component';
import { MetricsDatasourceComponent } from './blocks/metrics-datasource/metrics-datasource.component';
import { MetricsEndpointsRequestsComponent } from './blocks/metrics-endpoints-requests/metrics-endpoints-requests.component';
import { MetricsGarbageCollectorComponent } from './blocks/metrics-garbagecollector/metrics-garbagecollector.component';
import { MetricsModalThreadsComponent } from './blocks/metrics-modal-threads/metrics-modal-threads.component';
import { MetricsRequestComponent } from './blocks/metrics-request/metrics-request.component';
import { MetricsSystemComponent } from './blocks/metrics-system/metrics-system.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [
        MetricsComponent,
        JvmMemoryComponent,
        JvmThreadsComponent,
        MetricsCacheComponent,
        MetricsDatasourceComponent,
        MetricsEndpointsRequestsComponent,
        MetricsGarbageCollectorComponent,
        MetricsModalThreadsComponent,
        MetricsRequestComponent,
        MetricsSystemComponent,
    ],
})
export class MetricsModule {}
