package com.ngng.api.isolation;

import org.mockito.Answers;

import static org.mockito.Mockito.mock;

/** RF016 exercises user/point charging only; payment access must fail before external work. */
public final class Rf016UnusedPaymentCollaborator {
    private Rf016UnusedPaymentCollaborator() { }

    public static <T> T unused(Class<T> type) {
        return mock(type, invocation -> {
            if (invocation.getMethod().getDeclaringClass() == Object.class) {
                return Answers.RETURNS_DEFAULTS.answer(invocation);
            }
            throw new AssertionError("RF016 unexpected payment access: " + type.getSimpleName()
                    + "." + invocation.getMethod().getName());
        });
    }
}
