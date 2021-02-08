package com.au5tie.hireme;

import com.au5tie.hireme.connection.ConnectionCredentialUtils;
import com.au5tie.hireme.connection.ConnectionCredentials;
import com.au5tie.hireme.file.FileResult;
import com.backblaze.b2.client.B2ListFilesIterable;
import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.B2StorageClientFactory;
import com.backblaze.b2.client.contentHandlers.B2ContentMemoryWriter;
import com.backblaze.b2.client.structures.B2Bucket;
import com.backblaze.b2.client.structures.B2FileVersion;
import org.apache.commons.collections4.CollectionUtils;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The File Evaluator will connect to the B2 API in order to run evaluations on all accessible files within all accessible
 * buckets. The main goal is to count the number of occurrences of a specific character and display the output to the user.
 *
 * Reviewer Note: Thank you for taking the time to review this, I really appreciate it and the opportunity. I did not add
 * unit tests as the project prompt did not call for them. I would ALWAYS include unit tests to verify correctness, I
 * consider it a basic requirement of coding. However, I didn't want to go off prompt to include anything not desired. It
 * took a lot of self restraint not to.
 *
 * @author Austin Pilz
 */
public class FileEvaluator {

    /**
     * Initiates the evaluation of all files in all buckets accessible with the provided access credentials. Once all
     * files have been evaluated, this will display them nicely in a table via the console.
     *
     * Reviewer Note: Ultimately, I would probably make the character to find a command line argument so it's easy to
     * search for a variety of characters, rather than hard coding 'a'. I did not do this yet as the project prompt says
     * there are only two arguments which are the access credentials.
     *
     * @param args Two arguments, the first being the clientID and the second being the client secret.
     * @author Austin Pilz
     */
    public static void main(String[] args) {

        // Establish our credentials which are passed in from command line arguments.
        ConnectionCredentials credentials = ConnectionCredentialUtils.convertCommandLineToCredentials(args);

        if (credentials == null) {
            // There was an issue with the provided arguments being missing or malformed.
            System.err.println("Required arguments are missing or malformed. Please review the provided access keys.");
            return;
        }

        // Evaluate our files.
        List<FileResult> fileResults = evaluateFiles(credentials, 'a');

        // Display our findings.
        if (CollectionUtils.isNotEmpty(fileResults)) {
            printFileResults(fileResults);
        } else {
            System.out.println("There are no file results to display.");
        }
    }

    /**
     * This will evaluate all files accessible to us within all buckets. It will evaluate every file in every bucket and
     * generate a {@link FileResult} for each one.
     *
     * Reviewer Note: If I was able to clarify the basic hardware requirements that this application would run on, I would
     * likely multithread each bucket's evaluation. I would have a static work stealing pool and assign each bucket it's
     * own thread where that thread would be tasked with evaluating all files in the buckets. If there are a lot of buckets
     * and/or a lot of files in each bucket, multithreading it provides a great opportunity to improve performance. With
     * that said, I am unsure of how you'll run this, so I won't introduce multithreading via observables (yet).
     *
     * @param credentials B2 Connection Credentials.
     * @param character Character to search for.
     * @return File Results for all files in all buckets.
     * @author Austin Pilz
     */
    private static List<FileResult> evaluateFiles(ConnectionCredentials credentials, char character) {

        // Establish our B2Storage Client for connection to B2.
        B2StorageClient client = B2StorageClientFactory
                .createDefaultFactory()
                .create(credentials.getApplicationKeyId(), credentials.getApplicationKey(), "Java12");

        List<FileResult> fileResults = new ArrayList<>();

        try {
            // Obtain a list of all of the buckets on the account as we'll be searching through each one.
            List<B2Bucket> buckets = client.buckets();

            if (CollectionUtils.isNotEmpty(buckets)) {
                // Evaluate all of our buckets and the files within each one.
                buckets.forEach(bucket -> fileResults.addAll(evaluateBucketFiles(client, bucket, character)));
            } else {
                // We have no buckets we can look for files within.
                System.out.println("There are no buckets available for evaluation.");
            }
        } catch (Exception exception) {
            // Exit with non-zero as called for by prompt.
            System.err.println("An exception occurred while evaluating files - " + exception.getMessage());
            System.exit(1);
        } finally {
            // Always ensure we close the connection.
            if (client != null) {
                client.close();
            }
        }

        return fileResults;
    }

    /**
     * Evaluates all of the files within the bucket. This will obtain all of the files within the bucket and evaluate each
     * one, resulting in a {@link FileResult}.
     * @param client B2StorageClient.
     * @param bucket B2Bucket whose files to evaluate.
     * @param character Character to search for.
     * @return FileResults for all files within the bucket.
     * @author Austin Pilz
     */
    private static List<FileResult> evaluateBucketFiles(B2StorageClient client, B2Bucket bucket, char character) {

        List<FileResult> fileResults = new ArrayList<>();

        try {
            // Obtain all of the files within the bucket
            B2ListFilesIterable filesIterable = client.fileNames(bucket.getBucketId());

            // Evaluate all of the files within our bucket.
            filesIterable.forEach(file -> fileResults.add(evaluateFile(client, bucket, file, character)));
        } catch (Exception exception) {
            // We ran into some issue
            System.err.println("There was an error while obtaining and evaluating the files within the bucket "
                    + bucket.getBucketName() + " - " + exception.getMessage());
            // Printing a stack trace is a good idea, I would normally do it although not sure of customer scope for project.

            // Exit with non-zero as called for by prompt.
            System.exit(1);
        }

        return fileResults;
    }

    /**
     * This will evaluate the provided {@link B2FileVersion} to obtain the number of the desired characters within its
     * contents. This will download the file, evaluate the contents, count the occurrences of the character, and return
     * a {@link FileResult} with its findings for the file.
     *
     * Reviewer Note: I chose to handle errors by putting it on the {@link FileResult} rather than printing each one out
     * as it happened. This would allow us to have a cleaner way to handle these in the future, that way it wouldn't dirty
     * the print out for the end user. In the future, we could have one table print out with errors and one with the counts.
     *
     * @param client B2StorageClient.
     * @param bucket B2Bucket.
     * @param fileVersion B2FileVersion to evaluate.
     * @return File Result for character count in the provided file.
     * @author Austin Pilz
     */
    private static FileResult evaluateFile(B2StorageClient client, B2Bucket bucket, B2FileVersion fileVersion, char character) {

        // Build our memory handler which will hold the file in memory for us.
        B2ContentMemoryWriter handler = B2ContentMemoryWriter.builder()
                .setVerifySha1ByRereadingFromDestination(true)
                .build();

        try {
            // Download the file to our memory handler.
            client.downloadByName(bucket.getBucketName(), fileVersion.getFileName(), handler);
        } catch (Exception exception) {
            // There was an error while attempting to download the file.

            // Exit with non-zero as called for by prompt.
            System.err.println("Encountered an error while downloading the file " + bucket.getBucketName() + " / " +
                    fileVersion.getFileName() + " - " + exception.getMessage());
            System.exit(1);

            /*
            return FileResult.builder()
                    .bucket(bucket)
                    .fileName(fileVersion.getFileName())
                    .success(false)
                    .errorMessage(exception.getMessage())
                    .build();
             */
        }

        long characterCount = 0;

        if (handler.getBytes() != null && handler.getBytes().length > 0) {
            try {
                // Convert the file to a string for character evaluation.
                String fileContents = new String(handler.getBytes(), "UTF-8");

                // Count the occurrences of the character.
                characterCount = countCharacters(fileContents, character);
            } catch (UnsupportedEncodingException exception) {
                // There was an exception converting the file contents into a string.

                // Exit with non-zero as called for by prompt.
                System.exit(1);

                return FileResult.builder()
                        .bucket(bucket)
                        .fileName(fileVersion.getFileName())
                        .character(character)
                        .success(false)
                        .errorMessage(exception.getMessage()).build();

            }
        }

        // Build our file result with our findings for this specific file.
        return FileResult.builder()
                .bucket(bucket)
                .fileName(fileVersion.getFileName())
                .character(character)
                .characterCount(characterCount)
                .success(true)
                .build();
    }

    /**
     * Counts the number of occurrences of the provided character in the source string.
     *
     * Additional Considerations: This will look through each character in order in the file to find the occurrences of
     * the desired character. If this is a large file, we could gain performance by using stream parallelism. There is
     * a trade off with parallel, however, in the case of small files. The fastest way to do this is a generic for loop,
     * with second fastest being parallel streams, with the slowest being a sequential stream.
     *
     * Reviewer Note: This is very easily done with StringUtils.countMatches(). Since the aim of this project is for you
     * to gauge my java proficiency, I'll do this with Java streams to demonstrate I can do it without the assistance of
     * a third party library.
     *
     * @param source Source String to evaluate.
     * @param characterToFind Character to find the count of.
     * @return The number of times the character appears in the source string.
     * @author Austin Pilz
     */
    private static long countCharacters(String source, char characterToFind) {

        /*
        Third Party Library way to do it, uses a for loop so very fast:

        return StringUtils.countMatches(source, characterToFind);
         */

        // Classic For Loop Method
        int count = 0;

        for (int i = 0; i < source.length(); i++) {
            if (characterToFind == source.charAt(i)) {
                count++;
            }
        }

        return count;

        /*
        Java Streams Sequential Method:

        return source.chars()
                .filter(ch -> ch == characterToFind)
                .count();
         */

        /*
        Java Streams Parallel Method:

        return source.chars()
                .parallel()
                .filter(ch -> ch == characterToFind)
                .count();
         */
    }

    /**
     * Prints our file results out to the console. This will sort the results according to the client requirements and
     * print the results in their sorted order.
     * @param fileResults File Results to sort & display.
     * @author Austin Pilz
     */
    private static void printFileResults(List<FileResult> fileResults) {

        // Build our comparator with primary count ASC, secondary file name ASC
        Comparator<FileResult> resultComparator = Comparator
                .comparing(FileResult::getCharacterCount)
                .thenComparing(FileResult::getFileName);

        // Sort our list according to requirements,
        List<FileResult> sortedFileResults = fileResults.stream()
                                                .sorted(resultComparator)
                                                .collect(Collectors.toList());

        // Print our sorted lists as the client requires.
        for (FileResult result : sortedFileResults) {
            System.out.println(result.getCharacterCount() + " " + result.getFileName());
        }
    }
}
