package de.tum.cit.aet.artemis.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.service.OIDCExchangeCodeService.ExchangeCodeEntry;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

class OIDCExchangeCodeServiceTest {

    private static final String VALID_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk_valid_verifier";

    private DistributedDataProvider distributedDataProvider;

    private DistributedMap<String, ExchangeCodeEntry> codeToEntryMap;

    private OIDCExchangeCodeService oidcExchangeCodeService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        distributedDataProvider = mock(DistributedDataProvider.class);
        codeToEntryMap = mock(DistributedMap.class);
        when(distributedDataProvider.<String, ExchangeCodeEntry>getExpiringMap(eq("oidcExchangeCodes"), any(Duration.class))).thenReturn(codeToEntryMap);

        oidcExchangeCodeService = new OIDCExchangeCodeService(distributedDataProvider);
        oidcExchangeCodeService.init();
    }

    @Test
    void testRfc7636TestVector() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

        assertThat(OIDCExchangeCodeService.computeSHA256Challenge(verifier)).isEqualTo(expectedChallenge);
    }

    @Test
    void testStoreJwtAndGenerateCode_storesSingleEntryWithTTL() {
        String jwtToken = "mock_jwt_token_123";
        String challenge = OIDCExchangeCodeService.computeSHA256Challenge(VALID_VERIFIER);

        String code = oidcExchangeCodeService.storeJwtAndGenerateCode(jwtToken, challenge);

        assertThat(code).isNotNull().isNotBlank();
        verify(codeToEntryMap).put(eq(code), eq(new ExchangeCodeEntry(jwtToken, challenge)), eq(Duration.ofMinutes(5)));
    }

    @Test
    void testStoreJwtAndGenerateCode_invalidInputs_returnsNull() {
        String validChallenge = OIDCExchangeCodeService.computeSHA256Challenge(VALID_VERIFIER);
        assertThat(oidcExchangeCodeService.storeJwtAndGenerateCode(null, validChallenge)).isNull();
        assertThat(oidcExchangeCodeService.storeJwtAndGenerateCode("jwt", null)).isNull();
        assertThat(oidcExchangeCodeService.storeJwtAndGenerateCode("   ", validChallenge)).isNull();
        assertThat(oidcExchangeCodeService.storeJwtAndGenerateCode("jwt", "too-short-challenge")).isNull();
    }

    @Test
    void testRedeemCode_validCodeAndVerifier_atomicallyRemovesEntryAndReturnsJwt() {
        String code = "valid_exchange_code";
        String jwtToken = "mock_jwt_token_123";
        String challenge = OIDCExchangeCodeService.computeSHA256Challenge(VALID_VERIFIER);
        ExchangeCodeEntry entry = new ExchangeCodeEntry(jwtToken, challenge);

        when(codeToEntryMap.get(code)).thenReturn(entry);
        when(codeToEntryMap.remove(code, entry)).thenReturn(true);

        String redeemedToken = oidcExchangeCodeService.redeemCode(code, VALID_VERIFIER);

        assertThat(redeemedToken).isEqualTo(jwtToken);
        verify(codeToEntryMap).remove(code, entry);
    }

    @Test
    void testRedeemCode_invalidVerifier_doesNotRemoveEntryAndReturnsNull() {
        String code = "valid_exchange_code";
        String jwtToken = "mock_jwt_token_123";
        String challenge = OIDCExchangeCodeService.computeSHA256Challenge(VALID_VERIFIER);
        ExchangeCodeEntry entry = new ExchangeCodeEntry(jwtToken, challenge);

        when(codeToEntryMap.get(code)).thenReturn(entry);

        String invalidVerifier = "wrong_verifier_1234567890123456789012345678901234567890";
        String redeemedToken = oidcExchangeCodeService.redeemCode(code, invalidVerifier);

        assertThat(redeemedToken).isNull();
        verify(codeToEntryMap, never()).remove(eq(code), any());
    }

    @Test
    void testRedeemCode_tooShortVerifier_returnsNull() {
        String code = "valid_exchange_code";
        String redeemedToken = oidcExchangeCodeService.redeemCode(code, "short");
        assertThat(redeemedToken).isNull();
    }

    @Test
    void testRedeemCode_atomicRemoveFailed_returnsNull() {
        String code = "valid_exchange_code";
        String jwtToken = "mock_jwt_token_123";
        String challenge = OIDCExchangeCodeService.computeSHA256Challenge(VALID_VERIFIER);
        ExchangeCodeEntry entry = new ExchangeCodeEntry(jwtToken, challenge);

        when(codeToEntryMap.get(code)).thenReturn(entry);
        when(codeToEntryMap.remove(code, entry)).thenReturn(false);

        String redeemedToken = oidcExchangeCodeService.redeemCode(code, VALID_VERIFIER);

        assertThat(redeemedToken).isNull();
    }

    @Test
    void testRedeemCode_invalidOrNullInputs_returnsNull() {
        assertThat(oidcExchangeCodeService.redeemCode(null, VALID_VERIFIER)).isNull();
        assertThat(oidcExchangeCodeService.redeemCode("   ", VALID_VERIFIER)).isNull();
        assertThat(oidcExchangeCodeService.redeemCode("code", null)).isNull();

        when(codeToEntryMap.get("invalid_code")).thenReturn(null);
        assertThat(oidcExchangeCodeService.redeemCode("invalid_code", VALID_VERIFIER)).isNull();
    }

    @Test
    void testComputeSHA256Challenge_null_throwsException() {
        assertThatIllegalArgumentException().isThrownBy(() -> OIDCExchangeCodeService.computeSHA256Challenge(null));
    }
}
