package de.tum.cit.aet.artemis.programming.util;

import java.util.Random;

public class ShortNameGenerator {

    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789";

    public static String generateRandomShortName(int length) {
        Random random = new Random();
        StringBuilder prefix = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(CHARACTERS.length());
            prefix.append(CHARACTERS.charAt(index));
        }
        return prefix.toString();
    }
}
