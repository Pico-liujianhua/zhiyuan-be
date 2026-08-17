package com.tongji.auth.model;

public enum IdentifierType {
    PHONE,
    EMAIL,
    USERNAME;

    public static IdentifierType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("identifier type required");
        }
        return switch (value.toLowerCase()) {
            case "phone", "mobile" -> PHONE;
            case "email" -> EMAIL;
            case "username", "user", "account" -> USERNAME;
            default -> throw new IllegalArgumentException("Unsupported identifier type: " + value);
        };
    }
}
