package com.au5tie.hireme.file;

import com.backblaze.b2.client.structures.B2Bucket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * The File Result represents a files character count once the file has been evaluated. The result represents an individual
 * file within a bucket and the occurrence count of the specific character.
 * @author Austin Pilz
 */
@AllArgsConstructor
@Builder
@Data
public class FileResult {

    private final B2Bucket bucket;
    private final String fileName;
    private final char character;
    private final long characterCount;
    private final boolean success;
    private final String errorMessage;
}
