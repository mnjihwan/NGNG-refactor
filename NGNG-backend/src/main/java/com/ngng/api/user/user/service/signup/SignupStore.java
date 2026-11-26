package com.ngng.api.user.user.service.signup;

import java.util.List;

/** The production implementation executes each transition in one Redis Lua invocation. */
public interface SignupStore {
    List<String> run(String operation, List<String> keys, String... arguments);
}
