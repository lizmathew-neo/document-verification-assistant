package io.github.lizmathewneo.docverify.analysis;

import java.time.LocalDate;
import java.util.Map;

/**
 * What the document is supposed to be, and what it should say.
 *
 * @param requirementCode        e.g. PASSPORT, ID_CARD, PHOTO, MEDICAL, CERTIFICATE, PAYMENT_PROOF, FORM
 * @param requirementLabel       human-readable name of the requirement
 * @param requirementDescription what an acceptable document looks like
 * @param expectedFields         values on record to compare against, e.g. fullName, dateOfBirth,
 *                               passportNumber, passportExpiry, nationality
 * @param referenceDate          date used for expiry checks (defaults to today)
 */
public record AnalysisRequest(
        String requirementCode,
        String requirementLabel,
        String requirementDescription,
        Map<String, String> expectedFields,
        LocalDate referenceDate
) {}
