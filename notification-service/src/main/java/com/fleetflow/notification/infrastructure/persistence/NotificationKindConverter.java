package com.fleetflow.notification.infrastructure.persistence;

import com.fleetflow.notification.domain.NotificationKind;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores NotificationKind as its name in a plain VARCHAR column, with no enum CHECK constraint. */
@Converter
public class NotificationKindConverter implements AttributeConverter<NotificationKind, String> {
    @Override
    public String convertToDatabaseColumn(NotificationKind kind) {
        return kind == null ? null : kind.name();
    }

    @Override
    public NotificationKind convertToEntityAttribute(String value) {
        return value == null ? null : NotificationKind.valueOf(value);
    }
}
