package com.example.common.config;

public class TransferConstants {
    public static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;
    public static final int DEFAULT_RETRY_COUNT = 3;
    public static final long DEFAULT_RETRY_INTERVAL = 5000;
    public static final long DEFAULT_RECONNECT_INTERVAL = 5000;
    public static final int DEFAULT_SERVER_PORT = 8080;
    public static final String DEFAULT_VALIDATE_FLAG = "FILE_TRANSFER_TOKEN";
    
    public static final String DEFAULT_SIGNATURE_KEY = "FILE_TRANSFER_SECRET_KEY_V1";
    public static final String DEFAULT_SIGNATURE_ALGORITHM = "HmacSHA256";
    public static final boolean DEFAULT_SIGNATURE_ENABLED = true;
    public static final long SIGNATURE_TIMESTAMP_TOLERANCE = 300000;
}