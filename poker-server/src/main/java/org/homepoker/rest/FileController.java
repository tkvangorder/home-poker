package org.homepoker.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Files", description = "File upload and download")
public class FileController {

  private static final int MAX_FILE_SIZE_BYTES = 1024 * 1024 * 2;
  private final FileService fileService;

  public FileController(FileService fileService) {
    this.fileService = fileService;
  }

  @PostMapping("")
  @Operation(summary = "Upload a file", description = "Upload a file (max 2MB). Returns the file ID that can be used to download it later.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "File uploaded successfully, returns the file ID"),
      @ApiResponse(responseCode = "400", description = "File is too large or could not be read"),
      @ApiResponse(responseCode = "401", description = "Not authenticated")
  })
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
  @Operation(summary = "Download a file", description = "Download a previously uploaded file by its ID.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "File returned as an attachment"),
      @ApiResponse(responseCode = "401", description = "Not authenticated"),
      @ApiResponse(responseCode = "404", description = "File not found")
  })
  public ResponseEntity<ByteArrayResource> download(@Parameter(description = "ID of the file to download") @PathVariable String fileId) {
    UploadedFile file = fileService.getFile(fileId);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.id() + "\"")
        .body(new ByteArrayResource(file.data()));
  }
}
