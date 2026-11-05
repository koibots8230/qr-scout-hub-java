package com.koibots.scout.hub.sftp;

/**
 * An exception representing an error performing an SFTP operation.
 *
 * This class exists to insulate client code from the underlying SFTP
 * implementation.
 */
public class SFTPException
    extends Exception
{
    private static final long serialVersionUID = -6779796122652559181L;

    public SFTPException(String message, Throwable cause) {
        super(message, cause);
    }
}