package io.github.lizmathewneo.docverify.assist;

/** How much of the AI analysis a reviewer is allowed to see. */
public enum AssistMode {
    /** No AI assistance — the API refuses analysis requests. */
    OFF,
    /** Confidence score + traffic light only. */
    HINT,
    /** Detailed findings, field comparison and a recommended decision. */
    FULL
}
