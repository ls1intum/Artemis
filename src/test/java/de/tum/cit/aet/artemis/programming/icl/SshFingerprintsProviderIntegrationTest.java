package de.tum.cit.aet.artemis.programming.icl;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class SshFingerprintsProviderIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    // private static final String TEST_PREFIX = "sshFingerprintsTest";
    //
    // @Mock
    // private SshFingerprintsProviderService fingerprintsProviderService;
    //
    // @Autowired
    // private SshFingerprintsProviderResource sshFingerprintsProviderResource;
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
    // }
    //
    // @Nested
    // class SshFingerprintsProviderShould {
    //
    // @Test
    // @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    // void returnFingerprints() throws Exception {
    // sshFingerprintsProviderResource = new SshFingerprintsProviderResource(fingerprintsProviderService);
    //
    // Map<String, String> expectedFingerprints = new HashMap<>();
    // expectedFingerprints.put("RSA", expectedFingerprint);
    // doReturn(expectedFingerprints).when(fingerprintsProviderService).getSshFingerPrints();
    //
    // var response = request.get("/api/ssh-fingerprints", HttpStatus.OK, Map.class);
    //
    // assertThat(response.get("RSA")).isNotNull();
    // assertThat(response.get("RSA")).isEqualTo(expectedFingerprint);
    // }
    // }
}
