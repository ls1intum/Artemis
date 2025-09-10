package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AndroidApkKeyHashUtilTest {

    @Test
    void getHashFromFingerprint() {
        String fingerprint = "D2:E1:A6:6F:8C:00:55:97:9F:30:2F:3D:79:A9:5D:78:85:1F:C5:21:5A:7F:81:B3:BF:60:22:71:EF:6F:60:24";
        String expectedHash = "android:apk-key-hash:0uGmb4wAVZefMC89ealdeIUfxSFaf4Gzv2Aice9vYCQ";

        String actualHash = AndroidApkKeyHashUtil.getHashFromFingerprint(fingerprint);
        assertThat(actualHash).isEqualTo(expectedHash);
    }
}
