package org.homepoker.file;

import org.homepoker.model.file.UploadedFile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadedFileRepository extends MongoRepository<UploadedFile, String> {
}
