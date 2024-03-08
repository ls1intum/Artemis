package de.tum.in.www1.artemis.service.connectors.vcs;

import static de.tum.in.www1.artemis.service.connectors.vcs.VcsTokenManagementService.MAX_LIFETIME;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPersonalAccessTokenListResponseDTO;
import de.tum.in.www1.artemis.user.UserUtilService;

class VcsTokenRenewalServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "vcstokenrenewalservice";

    @Autowired
    private VcsTokenRenewalService vcsTokenRenewalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @BeforeEach
    void setUp() {
        gitlabRequestMockProvider.enableMockingOfRequests();
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 1, 1);
    }

    @AfterEach
    void teardown() throws Exception {
        gitlabRequestMockProvider.reset();
    }

    private record UserData(String initialToken, Long initialLifetimeDays, String updatedToken) {

        public UserData {
        }

        public UserData(String initialToken, Long initialLifetimeDays) {
            this(initialToken, initialLifetimeDays, null);
        }

        public boolean tokenRenewalNecessary() {
            return updatedToken != null;
        }
    }

    static Stream<? extends Arguments> userDataSource() {
        return Stream.of(Arguments.of(List.of(new UserData("uishfi", 234L), new UserData("sdfhsehfe", 2L, "oshdf"), new UserData("ofg4958", 27L, "e9h4th"),
                new UserData("fduvhid", 29L), new UserData("e9tertr", 364L), new UserData("fduvhid", -64L, "sdhfisf"))));
    }

    @ParameterizedTest
    @MethodSource("userDataSource")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenewAllPersonalAccessTokens(List<UserData> userData) throws GitLabApiException {
        final List<User> users = userRepository.findAll().stream().filter(u -> u.getLogin().startsWith(TEST_PREFIX)).toList();
        assertThat(userData.size()).isEqualTo(users.size());

        final ArrayList<org.gitlab4j.api.models.User> gitlabUsers = new ArrayList<>();

        for (int i = 0; i < users.size(); ++i) {
            User user = users.get(i);
            UserData data = userData.get(i);

            user.setVcsAccessToken(data.initialToken());
            user.setVcsAccessTokenExpiryDate(ZonedDateTime.now().plusDays(data.initialLifetimeDays()));
            userRepository.save(user);

            gitlabUsers.add(new org.gitlab4j.api.models.User().withId(user.getId()).withUsername(user.getLogin()));
        }

        final long expectedRenewalCount = userData.stream().filter(UserData::tokenRenewalNecessary).count();

        gitlabRequestMockProvider.mockGetUserApi();
        gitlabRequestMockProvider.mockGetUserID(gitlabUsers);
        gitlabRequestMockProvider.mockCreatePersonalAccessToken(expectedRenewalCount, new HashMap<>() {

            {
                for (int i = 0; i < users.size(); ++i) {
                    put(gitlabUsers.get(i).getUsername(), userData.get(i).updatedToken());
                    put(gitlabUsers.get(i).getId(), userData.get(i).updatedToken());
                }
            }
        });
        gitlabRequestMockProvider.mockListAndRevokePersonalAccessTokens(expectedRenewalCount, new HashMap<>() {

            {
                for (int i = 0; i < users.size(); ++i) {
                    put(gitlabUsers.get(i).getId(), new GitLabPersonalAccessTokenListResponseDTO(null));
                }
            }
        });

        vcsTokenRenewalService.renewAllVcsAccessTokens();

        gitlabRequestMockProvider.verifyMocks();

        for (int i = 0; i < users.size(); ++i) {
            final User updatedUser = userRepository.getUserByLoginElseThrow(users.get(i).getLogin());
            final UserData data = userData.get(i);

            if (userData.get(i).tokenRenewalNecessary()) {
                assertThat(updatedUser.getVcsAccessToken()).isEqualTo(data.updatedToken());
                assertThat(updatedUser.getVcsAccessTokenExpiryDate()).isNotNull();
                assertThat(updatedUser.getVcsAccessTokenExpiryDate()).isAfter(ZonedDateTime.now().plus(MAX_LIFETIME).minusDays(1));
            }
            else {
                final ZonedDateTime initialVcsAccessTokenExpiryDate = users.get(i).getVcsAccessTokenExpiryDate();

                assertThat(updatedUser.getVcsAccessToken()).isEqualTo(data.initialToken());

                // Comparison with epsilon is necessary because storing the datetime value into the database leads to rounding or truncation.
                final int epsilonSeconds = 1;
                assertThat(initialVcsAccessTokenExpiryDate).isNotNull();
                assertThat(updatedUser.getVcsAccessTokenExpiryDate()).isAfter(initialVcsAccessTokenExpiryDate.minusSeconds(epsilonSeconds));
                assertThat(updatedUser.getVcsAccessTokenExpiryDate()).isBefore(initialVcsAccessTokenExpiryDate.plusSeconds(epsilonSeconds));
            }
        }
    }
}
