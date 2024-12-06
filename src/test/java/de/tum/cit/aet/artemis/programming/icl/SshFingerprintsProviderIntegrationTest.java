package de.tum.cit.aet.artemis.programming.icl;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class SshFingerprintsProviderIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {
    //
    // private static final String TEST_PREFIX = "sshFingerprintsTest";
    //
    // @MockBean
    // private SshServer sshServer;
    //
    // @Mock
    // private KeyPairProvider keyPairProvider;
    //
    // private String expectedFingerprint;
    //
    // @BeforeEach
    // void setup() throws Exception {
    // KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    // keyPairGenerator.initialize(2048);
    // KeyPair testKeyPair = keyPairGenerator.generateKeyPair();
    //
    // expectedFingerprint = HashUtils.getSha256Fingerprint(testKeyPair.getPublic());
    // doReturn(keyPairProvider).when(sshServer).getKeyPairProvider();
    // doReturn(Collections.singleton(testKeyPair)).when(keyPairProvider).loadKeys(null);
    // }
    //
    // @Nested
    // class SshFingerprintsProvider {
    //
    // @Test
    // @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    // void shouldReturnFingerprints() throws Exception {
    // var response = request.get("/api/ssh-fingerprints", HttpStatus.OK, Map.class);
    // assertThat(response.get("RSA")).isNotNull();
    // assertThat(response.get("RSA")).isEqualTo(expectedFingerprint);
    // }
    //
    // @Test
    // @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    // void shouldReturnNoFingerprintsWithoutKeyProviderSetup() throws Exception {
    // doReturn(null).when(sshServer).getKeyPairProvider();
    //
    // var response = request.get("/api/ssh-fingerprints", HttpStatus.OK, Map.class);
    // assertThat(response.isEmpty()).isTrue();
    // }
    //
    // @Test
    // @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    // void shouldReturnBadRequestWhenLoadKeysThrowsException() throws Exception {
    // doThrow(new IOException("Test exception")).when(keyPairProvider).loadKeys(null);
    //
    // request.get("/api/ssh-fingerprints", HttpStatus.BAD_REQUEST, Map.class);
    // }
    // }
}
