package org.telegram.othergram;

import java.util.Calendar;

/**
 * Rough registration year of a Telegram account. User ids are issued in increasing order, so the id
 * is interpolated between known (id, date) checkpoints. The result is an estimate (about ±1 year)
 * and is always shown as "approximately".
 */
public class OtherGramAccountAge {

    // id -> year with fraction; ids between ~2.15e9 and 5e9 were never issued to users
    private static final long[] IDS = {
            10_000_000L, 100_000_000L, 200_000_000L, 400_000_000L, 800_000_000L, 1_000_000_000L,
            1_400_000_000L, 1_800_000_000L, 2_150_000_000L, 5_000_000_000L, 5_500_000_000L,
            6_000_000_000L, 6_500_000_000L, 7_000_000_000L, 7_500_000_000L, 8_000_000_000L, 8_500_000_000L
    };
    private static final float[] YEARS = {
            2014.0f, 2015.3f, 2016.3f, 2017.6f, 2019.0f, 2019.8f,
            2020.6f, 2021.2f, 2021.8f, 2021.9f, 2022.6f,
            2023.3f, 2023.9f, 2024.4f, 2024.9f, 2025.4f, 2026.0f
    };

    /** Estimated registration year, or 0 for ids that are not users. */
    public static int estimateYear(long userId) {
        if (userId <= 0) {
            return 0;
        }
        final int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        if (userId <= IDS[0]) {
            return (int) YEARS[0];
        }
        for (int i = 1; i < IDS.length; i++) {
            if (userId <= IDS[i]) {
                final float t = (userId - IDS[i - 1]) / (float) (IDS[i] - IDS[i - 1]);
                return Math.min(currentYear, (int) (YEARS[i - 1] + (YEARS[i] - YEARS[i - 1]) * t));
            }
        }
        return currentYear;
    }
}
