package org.homepoker.model.file;

import org.jspecify.annotations.Nullable;

public record UploadedFile(String id, String contentType, byte[] data, @Nullable String userUploadedBy) {
}
