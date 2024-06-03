package org.homepoker.file;

import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.file.UploadedFile;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class FileService {

  private final UploadedFileRepository uploadedFileRepository;

  public FileService(UploadedFileRepository uploadedFileRepository) {
    this.uploadedFileRepository = uploadedFileRepository;
  }

  public UploadedFile saveFile(UploadedFile uploadedFile) {
    Assert.hasText(uploadedFile.contentType(), "The file content type is required.");
    Assert.isNull(uploadedFile.id(), "The file ID will be assigned after the file has been saved.");

    return uploadedFileRepository.save(uploadedFile);
  }

  public UploadedFile getFile(String id) {
    return uploadedFileRepository.findById(id)
        .orElseThrow(() -> new ValidationException("The file does not exist."));
  }

}
