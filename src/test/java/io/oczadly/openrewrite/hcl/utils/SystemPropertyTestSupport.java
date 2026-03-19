package io.oczadly.openrewrite.hcl.utils;

public final class SystemPropertyTestSupport {

    private SystemPropertyTestSupport() {
    }

    public static void restoreSystemProperty(String propertyName, String previousPropertyValue) {
        if (previousPropertyValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousPropertyValue);
        }
    }
}

