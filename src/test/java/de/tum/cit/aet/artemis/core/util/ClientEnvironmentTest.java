package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.core.domain.Language;

class ClientEnvironmentTest {

    @ParameterizedTest
    @CsvSource({ "ENGLISH,an unknown device,Google Chrome on Linux,the Artemis Android App", "GERMAN,einem unbekannten Gerät,Google Chrome unter Linux,der Artemis Android App" })
    void localizedDescriptionsPreferAppThenBrowserAndOperatingSystem(Language language, String unknown, String browserAndSystem, String app) {
        assertThat(new ClientEnvironment(null, null, null).getEnvironmentInfo(language)).isEqualTo(unknown);
        assertThat(new ClientEnvironment(Browser.GOOGLE_CHROME, OperatingSystem.LINUX, null).getEnvironmentInfo(language)).isEqualTo(browserAndSystem);
        assertThat(new ClientEnvironment(Browser.GOOGLE_CHROME, null, null).getEnvironmentInfo(language)).isEqualTo("Google Chrome");
        assertThat(new ClientEnvironment(null, OperatingSystem.LINUX, null).getEnvironmentInfo(language)).isEqualTo("Linux");
        assertThat(new ClientEnvironment(Browser.GOOGLE_CHROME, OperatingSystem.LINUX, ArtemisApp.ANDROID).getEnvironmentInfo(language)).isEqualTo(app);
        assertThat(new ClientEnvironment(null, null, ArtemisApp.ANDROID).getEnvironmentInfo(language)).isEqualTo(app);
    }
}
