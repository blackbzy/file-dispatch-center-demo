package com.example.common.message;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TransferMessageTest {

    @Test
    public void testAuthRequest() {
        TransferMessage msg = TransferMessage.authRequest("test-token");
        assertEquals(TransferMessage.MessageType.AUTH_REQUEST, msg.getMessageType());
        assertEquals("test-token", msg.getValidateFlag());
        assertEquals(TransferMessage.TransferCategory.CONTROL_COMMAND.getValue(), msg.getTransferType());
    }

    @Test
    public void testAuthResponse() {
        TransferMessage msg = TransferMessage.authResponse(true, "success");
        assertEquals(TransferMessage.MessageType.AUTH_RESPONSE, msg.getMessageType());
        assertTrue(msg.isSuccess());
        assertEquals("success", msg.getErrorMessage());
    }

    @Test
    public void testFileChunk() {
        byte[] data = "test data".getBytes();
        TransferMessage msg = TransferMessage.fileChunk(data, 0, 1, "test.txt", 1234567890L);
        
        assertEquals(TransferMessage.MessageType.FILE_CHUNK, msg.getMessageType());
        assertEquals(TransferMessage.TransferCategory.FILE_DATA.getValue(), msg.getTransferType());
        assertArrayEquals(data, msg.getData());
        assertEquals(0, msg.getChunkIndex());
        assertEquals(1, msg.getTotalChunks());
        assertEquals("test.txt", msg.getFileName());
        
        assertNotNull(msg.getChunkParams());
        assertEquals(0, msg.getChunkParams().getChunkIndex());
        assertEquals(1, msg.getChunkParams().getTotalChunks());
        
        assertNotNull(msg.getFileMetadata());
        assertEquals("test.txt", msg.getFileMetadata().getFileName());
    }

    @Test
    public void testFileChunkWithMetadata() {
        byte[] data = "chunk data".getBytes();
        ChunkParams chunkParams = new ChunkParams(1, 5, 1024, 1024);
        FileMetadata fileMetadata = new FileMetadata("test.bin", 5120, "bin", 1234567890L, 1234567890L, "md5hash");
        
        TransferMessage msg = TransferMessage.fileChunkWithMetadata(data, chunkParams, fileMetadata);
        
        assertEquals(TransferMessage.MessageType.FILE_CHUNK, msg.getMessageType());
        assertEquals(chunkParams, msg.getChunkParams());
        assertEquals(fileMetadata, msg.getFileMetadata());
        assertEquals(1, msg.getChunkIndex());
        assertEquals(5, msg.getTotalChunks());
    }

    @Test
    public void testFileMetadataMessage() {
        FileMetadata metadata = new FileMetadata("test.txt", 1024, "txt", 1234567890L, 1234567890L, "hash123");
        TransferMessage msg = TransferMessage.fileMetadata(metadata);
        
        assertEquals(TransferMessage.MessageType.FILE_METADATA, msg.getMessageType());
        assertEquals(TransferMessage.TransferCategory.METADATA.getValue(), msg.getTransferType());
        assertEquals(metadata, msg.getFileMetadata());
    }

    @Test
    public void testAck() {
        TransferMessage msg = TransferMessage.ack(5, "test.txt");
        assertEquals(TransferMessage.MessageType.ACK, msg.getMessageType());
        assertEquals(5, msg.getChunkIndex());
        assertEquals("test.txt", msg.getFileName());
    }

    @Test
    public void testEnd() {
        TransferMessage msg = TransferMessage.end("test.txt");
        assertEquals(TransferMessage.MessageType.END, msg.getMessageType());
        assertTrue(msg.isEndFlag());
        assertEquals("test.txt", msg.getFileName());
    }

    @Test
    public void testEndWithMetadata() {
        FileMetadata metadata = new FileMetadata("test.txt", 1024, "txt", 1234567890L, 1234567890L, "hash");
        TransferMessage msg = TransferMessage.endWithMetadata("test.txt", metadata);
        
        assertEquals(TransferMessage.MessageType.END, msg.getMessageType());
        assertTrue(msg.isEndFlag());
        assertEquals(metadata, msg.getFileMetadata());
    }

    @Test
    public void testError() {
        TransferMessage msg = TransferMessage.error("error message");
        assertEquals(TransferMessage.MessageType.ERROR, msg.getMessageType());
        assertFalse(msg.isSuccess());
        assertEquals("error message", msg.getErrorMessage());
    }

    @Test
    public void testHeartbeat() {
        TransferMessage msg = TransferMessage.heartbeat();
        assertEquals(TransferMessage.MessageType.HEARTBEAT, msg.getMessageType());
        assertEquals(TransferMessage.TransferCategory.CONTROL_COMMAND.getValue(), msg.getTransferType());
    }

    @Test
    public void testBackwardCompatibility() {
        TransferMessage msg = new TransferMessage();
        msg.setMessageType(TransferMessage.MessageType.FILE_CHUNK);
        msg.setChunkIndex(2);
        msg.setTotalChunks(10);
        msg.setFileName("legacy.txt");
        
        assertEquals(2, msg.getChunkIndex());
        assertEquals(10, msg.getTotalChunks());
        assertEquals("legacy.txt", msg.getFileName());
        
        assertNull(msg.getChunkParams());
        assertNull(msg.getFileMetadata());
    }

    @Test
    public void testTransferCategoryValues() {
        assertEquals("NORMAL_DATA", TransferMessage.TransferCategory.NORMAL_DATA.getValue());
        assertEquals("FILE_DATA", TransferMessage.TransferCategory.FILE_DATA.getValue());
        assertEquals("CONTROL_COMMAND", TransferMessage.TransferCategory.CONTROL_COMMAND.getValue());
        assertEquals("METADATA", TransferMessage.TransferCategory.METADATA.getValue());
    }
}