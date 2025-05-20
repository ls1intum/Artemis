package de.tum.cit.aet.artemis.core.util;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

public class CookieParserTestUtil {

    private CookieParserTestUtil() {
        // Utility class, no instantiation
    }

    public static ResponseCookie parseSetCookieHeader(String setCookieHeader) {
        String[] parts = setCookieHeader.split(";");
        String[] nameValue = parts[0].trim().split("=", 2);
        String name = nameValue[0];
        String value = nameValue.length > 1 ? nameValue[1] : "";

        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value);

        for (int i = 1; i < parts.length; i++) {
            String[] attr = parts[i].trim().split("=", 2);
            String key = attr[0].trim().toLowerCase();
            String val = attr.length > 1 ? attr[1].trim() : "";

            switch (key) {
                case "path":
                    builder.path(val);
                    break;
                case "max-age":
                    builder.maxAge(Duration.ofSeconds(Long.parseLong(val)));
                    break;
                case "expires":
                    // ResponseCookie does not directly support `Expires`, prefer Max-Age
                    break;
                case "domain":
                    builder.domain(val);
                    break;
                case "secure":
                    builder.secure(true);
                    break;
                case "httponly":
                    builder.httpOnly(true);
                    break;
                case "samesite":
                    builder.sameSite(val);
                    break;
                default:
                    // Unknown attribute
                    break;
            }
        }

        return builder.build();
    }

}
