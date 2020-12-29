package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.repository.StatisticsRepository;
import de.tum.in.www1.artemis.service.StatisticsService;
import de.tum.in.www1.artemis.web.rest.StatisticsResource;

public class UserStatisticsIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    StatisticsResource statisticsResource;

    @Autowired
    StatisticsService statisticsService;

    @Autowired
    StatisticsRepository statisticsRepository;

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void GetDataForEachSpanEachGraph() throws Exception {
        SpanType[] spans = SpanType.values();
        GraphType[] graphs = GraphType.values();
        Integer periodIndex = 0;
        for (SpanType span : spans) {
            for (GraphType graph : graphs) {
                Integer[] result = statisticsResource.getChartData(span, periodIndex, graph).getBody();
                assert result != null;
                assertThat(result.length).isNotNull();
            }
        }
    }
}
