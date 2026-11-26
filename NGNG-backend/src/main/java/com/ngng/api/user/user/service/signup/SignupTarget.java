package com.ngng.api.user.user.service.signup;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SignupTarget {
    private static final Pattern LOCAL = Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*");
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private SignupTarget() { }
    private static String spaces(String value) {
        if (value == null) throw SignupFailure.required();
        int start = 0, end = value.length();
        while (start < end && value.charAt(start) == ' ') start++;
        while (end > start && value.charAt(end - 1) == ' ') end--;
        return value.substring(start, end);
    }
    public static String email(String value) {
        String v = spaces(value);
        int at = v.indexOf('@');
        if (v.length() > 254 || at < 1 || at > 64 || at != v.lastIndexOf('@') || !LOCAL.matcher(v.substring(0, at)).matches()) throw SignupFailure.required();
        String domain = v.substring(at + 1);
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) throw SignupFailure.required();
        for (String label : labels) if (!LABEL.matcher(label).matches()) throw SignupFailure.required();
        // Numeric dotted IPv4 is not a DNS mailbox domain.
        if (domain.matches("[0-9.]+")) throw SignupFailure.required();
        return v.substring(0, at) + "@" + domain.toLowerCase(Locale.ROOT);
    }
    public static String phone(String value) {
        String v = spaces(value);
        if (!v.matches("(?:[0-9]{10,11}|[0-9]{3}-[0-9]{3,4}-[0-9]{4})")) throw SignupFailure.required();
        return v.replace("-", "");
    }
}
