package com.au5tie.hireme.connection;

import org.apache.commons.lang3.StringUtils;

/**
 * The Connection Credential Utils is a collection of utilities designed to support validation/manipulation of the
 * credentials required to establish a secure connection to our external storage provider.
 *
 * Reviewer Note: With the scope of this application so small, I could have very well done this in a few lines or a
 * separate method in the main class. However, when I code, I plan as if this application will grow significantly, thus
 * I am placing these validations here for reuse and cleanliness.
 *
 * @austin Austin Pilz
 */
public class ConnectionCredentialUtils {

    /**
     * Converts the provided command line arguments into {@link ConnectionCredentials}. This assumes the first argument
     * provided is the applicationKeyId and the second is the applicationKey. If either of the arguments are missing or
     * malformed, a null ConnectionCredentials will be returned.
     * @param args Command Line arguments where the first argument is the applicationKeyId and the second is applicationKey.
     * @return ConnectionCredentials, null if either credential argument is not valid.
     * @author Austin Pilz
     */
    public static ConnectionCredentials convertCommandLineToCredentials(String[] args) {

        if (args == null || args.length < 2) {
            // The two required arguments are not present.
            return null;
        }

        if (StringUtils.isBlank(args[0]) || StringUtils.isBlank(args[1])) {
            // One or more required arguments are not present / acceptable for connection use.
            return null;
        }

        return ConnectionCredentials.builder()
                .applicationKeyId(args[0])
                .applicationKey(args[1])
                .build();
    }
}
