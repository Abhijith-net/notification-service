package com.example.notificationservice.channel;

public record SendResult(boolean success, String externalId, String errorMessage) {

    public static SendResult ok(String externalId) {
        return new SendResult(true, externalId, null);
    }

    public static SendResult failure(String errorMessage) {
        return new SendResult(false, null, errorMessage);
    }
}
