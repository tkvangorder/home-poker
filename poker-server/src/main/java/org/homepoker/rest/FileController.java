package org.homepoker.rest;

import org.homepoker.file.FileService;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.file.UploadedFile;
import org.homepoker.model.user.User;
import org.homepoker.security.SecurityUtilities;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/files")
public class FileController {

  private static final int MAX_FILE_SIZE_BYTES = 1024 * 1024 * 2;
  private final FileService fileService;

  public FileController(FileService fileService) {
    this.fileService = fileService;
  }

  @PostMapping("")
  public String uploadFile(@RequestParam("file") MultipartFile file) {
    if (file.getSize() > MAX_FILE_SIZE_BYTES) {
      throw new ValidationException("The file is too large.");
    }
    User user = SecurityUtilities.getCurrentUser();
    try {
      UploadedFile newFile = fileService.saveFile(new UploadedFile(null, file.getContentType(), file.getBytes(), user.id()));
      return newFile.id();
    } catch (IOException exception) {
      throw new ValidationException("An error occurred while reading the file.");
    }
  }

  @GetMapping("/{fileId}")
  public ResponseEntity<ByteArrayResource> download(@PathVariable String fileId) {
    UploadedFile file = fileService.getFile(fileId);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.id() + "\"")
        .body(new ByteArrayResource(file.data()));
  }
}
