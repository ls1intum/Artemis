package de.tum.cit.aet.artemis.core.config.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class IndexCatalogTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void readsEveryColumnOfAnIndexInIndexOrder() {
        Map<String, List<TableIndex>> indexes = new IndexCatalog(dataSource).indexes();

        assertThat(indexes.get("saved_post")).anySatisfy(index -> {
            assertThat(index.columns()).containsExactly("user_id", "post_id", "post_type");
            assertThat(index.unique()).isTrue();
        });
        assertThat(indexes.get("jhi_user")).anySatisfy(index -> assertThat(index.columns()).containsExactly("id"));
        assertThat(indexes.values()).allSatisfy(tableIndexes -> assertThat(tableIndexes)
                .allSatisfy(index -> assertThat(index.columns()).isNotEmpty().allSatisfy(column -> assertThat(column).matches("[a-z_][a-z0-9_]*"))));
    }
}
