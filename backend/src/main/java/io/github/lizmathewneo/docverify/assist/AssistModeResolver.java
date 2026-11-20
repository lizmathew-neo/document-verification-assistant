package io.github.lizmathewneo.docverify.assist;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decides which {@link AssistMode} applies to the current request.
 *
 * <p>Replace the default bean with one that looks up the authenticated user's
 * permission (e.g. from your identity provider or user database). The mode must
 * be decided server-side — never trust a mode sent by the client.
 */
@FunctionalInterface
public interface AssistModeResolver {

    AssistMode resolve(HttpServletRequest request);
}
