package com.example.common.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FileMetadataTest {

    private FileMetadata fileMetadata;

    @BeforeEach
    public void setUp() {
        fileMetadata = new FileMetadata();
    }

    @Test
    public void testDefaultConstructor() {
        assertNotNull(fileMetadata);
        assertNull(fileMetadata.getFileName());
        assertEquals(0, fileMetadata.getFileSize());
        assertNull(fileMetadata.getFileType());
        assertEquals(0, fileMetadata.getCreateTime());
        assertEquals(0, fileMetadata.getLastModified());
        assertNull(fileMetadata.getMd5());
    }

    @Test
    public void testParameterizedConstructor() {
        FileMetadata metadata = new FileMetadata("test.txt", 1024, "txt", 1234567890L, 1234567890L, "abc123");
        assertEquals("test.txt", metadata.getFileName());
        assertEquals(1024, metadata.getFileSize());
        assertEquals("txt", metadata.getFileType());
        assertEquals(1234567890L, metadata.getCreateTime());
        assertEquals(1234567890L, metadata.getLastModified());
        assertEquals("abc123", metadata.getMd5());
    }

    @Test
    public void testSettersAndGetters() {
        fileMetadata.setFileName("test.pdf");
        fileMetadata.setFileSize(2048);
        fileMetadata.setFileType("pdf");
        fileMetadata.setCreateTime(9876543210L);
        fileMetadata.setLastModified(9876543210L);
        fileMetadata.setMd5("def456");

        assertEquals("test.pdf", fileMetadata.getFileName());
        assertEquals(2048, fileMetadata.getFileSize());
        assertEquals("pdf", fileMetadata.getFileType());
        assertEquals(9876543210L, fileMetadata.getCreateTime());
        assertEquals(9876543210L, fileMetadata.getLastModified());
        assertEquals("def456", fileMetadata.getMd5());
    }

    @Test
    public void testToString() {
        fileMetadata.setFileName("test.txt");
        fileMetadata.setFileSize(1024);
        
        String result = fileMetadata.toString();
        assertTrue(result.contains("fileName='test.txt'"));
        assertTrue(result.contains("fileSize=1024"));
    }
}