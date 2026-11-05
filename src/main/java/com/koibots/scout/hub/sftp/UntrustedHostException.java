package com.koibots.scout.hub.sftp;

public class UntrustedHostException
    extends SFTPException
{
    private static final long serialVersionUID = 7801344340705956003L;

    private final String host;
    private final String keyType;
    private final String publicKey;
    private final String fingerprint;

    public UntrustedHostException(String host, String keyType, String publicKey, String fingerprint, Throwable cause) {
        super(host + ": " + fingerprint, cause);

        this.host = host;
        this.keyType = keyType;
        this.publicKey = publicKey;
        this.fingerprint = fingerprint;
    }

    public String getHost() {
        return host;
    }

    public String getKeyType() {
        return keyType;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getFingerprint() {
        return fingerprint;
    }
}