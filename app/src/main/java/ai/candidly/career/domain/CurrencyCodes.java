package ai.candidly.career.domain;

import java.util.Currency;
import java.util.Locale;

/**
 * ISO 4217 validation/normalization shared by every money field's currency code ({@link
 * Candidate#getPreferredCurrency()}, {@link JobPosting#getCurrency()}, {@code Offer}). Money
 * is always stored as integer minor units and never converted - there's no FX-rate source
 * wired into this codebase, so a currency code labels what an amount is denominated in; it
 * doesn't translate it into another currency. Two amounts in different currencies are
 * therefore not comparable (see {@code EligibilityGateService}'s comp check).
 */
public final class CurrencyCodes {

    /** What a null/legacy currency column reads as - every amount stored before currency
     * codes existed was entered against USD-labelled UI. */
    public static final String DEFAULT = "USD";

    private CurrencyCodes() {
    }

    /** Trims and upper-cases {@code code}, rejecting anything that isn't a real ISO 4217 code. */
    public static String normalize(String code, String field) {
        String upper = code.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(upper);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be an ISO 4217 currency code (e.g. USD, INR), got: " + code);
        }
        return upper;
    }

    public static boolean sameCurrency(String a, String b) {
        return a.equalsIgnoreCase(b);
    }

    /** Converts a whole-number major-unit amount (e.g. an annual salary quoted in whole
     * dollars/rupees by a discovery source) into minor units using the currency's own
     * fraction-digit count - mirrors the frontend's {@code toMinorUnits} (lib/currency.ts),
     * which explicitly avoids assuming every currency uses a factor of 100. */
    public static long toMinorUnits(long majorUnits, String code) {
        int fractionDigits = Currency.getInstance(code).getDefaultFractionDigits();
        return majorUnits * (long) Math.pow(10, Math.max(fractionDigits, 0));
    }
}
