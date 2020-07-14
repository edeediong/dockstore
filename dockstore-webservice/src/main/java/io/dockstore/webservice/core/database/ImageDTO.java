package io.dockstore.webservice.core.database;

import io.dockstore.webservice.core.Image;

public class ImageDTO {
    private final Image id;
    private final long versionId;

    public ImageDTO(final Image id, final long versionId) {
        this.id = id;
        this.versionId = versionId;
    }

    public long getVersionId() {
        return versionId;
    }

    public Image getId() {
        return id;
    }
}
