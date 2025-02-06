package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCInfoContributor;

class LocalVCInfoContributorTest {

    @Test
    void testContribute() {
        Info.Builder builder = new Info.Builder();
        var repositoryAuthenticationMechanisms = List.of("password", "token", "ssh");
        LocalVCInfoContributor localVCInfoContributor = new LocalVCInfoContributor();
        ReflectionTestUtils.setField(localVCInfoContributor, "orderedRepositoryAuthenticationMechanisms", repositoryAuthenticationMechanisms);

        try {
            localVCInfoContributor.contribute(builder);
        }
        catch (NullPointerException e) {
        }

        Info info = builder.build();
        assertThat(info.getDetails().get("repositoryAuthenticationMechanisms")).isEqualTo(repositoryAuthenticationMechanisms);
    }
}
