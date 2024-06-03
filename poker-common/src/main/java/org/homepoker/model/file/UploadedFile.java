package org.homepoker.model.file;

import org.springframework.lang.Nullable;

public record UploadedFile(String id, String contentType, byte[] data, @Nullable String userUploadedBy) {
}
