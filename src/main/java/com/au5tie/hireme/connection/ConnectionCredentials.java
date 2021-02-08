package com.au5tie.hireme.connection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * The Connection Credentials represent a set of credentials to be used for connection authentication with the external
 * storage provider.
 * @author Austin Pilz
 */
@AllArgsConstructor
@Builder
@Data
public class ConnectionCredentials {

    private final String applicationKeyId;
    private final String applicationKey;
}
