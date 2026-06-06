package com.example.common.codec;

import com.example.common.message.ChunkParams;
import com.example.common.message.FileMetadata;
import com.example.common.message.TransferMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class MessageCodecTest {

    private MessageEncoder encoder;
    private MessageDecoder decoder;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        encoder = new MessageEncoder(false);
        decoder = new MessageDecoder(false, 0);
        objectMapper = new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Test
    public void testEncodeDecodeFileChunk() throws Exception {
        byte[] data = "Hello, World!".getBytes();
        TransferMessage originalMsg = TransferMessage.fileChunk(data, 0, 1, "test.txt", System.currentTimeMillis());

        ByteBuf out = Unpooled.buffer();
        encoder.encode(null, originalMsg, out);

        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        ByteBuf in = Unpooled.wrappedBuffer(bytes);

        List<Object> decoded = new ArrayList<>();
        decoder.decode(null, in, decoded);

        assertEquals(1, decoded.size());
        TransferMessage decodedMsg = (TransferMessage) decoded.get(0);

        assertEquals(originalMsg.getMessageType(), decodedMsg.getMessageType());
        assertEquals(originalMsg.getTransferType(), decodedMsg.getTransferType());
        assertArrayEquals(data, decodedMsg.getData());
        assertEquals("test.txt", decodedMsg.getFileName());
        assertNotNull(decodedMsg.getChunkParams());
        assertNotNull(decodedMsg.getFileMetadata());
    }

    @Test
    public void testEncodeDecodeFileMetadata() throws Exception {
        FileMetadata metadata = new FileMetadata("test.pdf", 1024000, "pdf", 
            System.currentTimeMillis(), System.currentTimeMillis(), "md5checksum");
        TransferMessage originalMsg = TransferMessage.fileMetadata(metadata);

        ByteBuf out = Unpooled.buffer();
        encoder.encode(null, originalMsg, out);

        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        ByteBuf in = Unpooled.wrappedBuffer(bytes);

        List<Object> decoded = new ArrayList<>();
        decoder.decode(null, in, decoded);

        assertEquals(1, decoded.size());
        TransferMessage decodedMsg = (TransferMessage) decoded.get(0);

        assertEquals(TransferMessage.MessageType.FILE_METADATA, decodedMsg.getMessageType());
        assertEquals(TransferMessage.TransferCategory.METADATA.getValue(), decodedMsg.getTransferType());
        assertNotNull(decodedMsg.getFileMetadata());
        assertEquals("test.pdf", decodedMsg.getFileMetadata().getFileName());
        assertEquals(1024000, decodedMsg.getFileMetadata().getFileSize());
        assertEquals("md5checksum", decodedMsg.getFileMetadata().getMd5());
    }

    @Test
    public void testEncodeDecodeAuthRequest() throws Exception {
        TransferMessage originalMsg = TransferMessage.authRequest("my-token");

        ByteBuf out = Unpooled.buffer();
        encoder.encode(null, originalMsg, out);

        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        ByteBuf in = Unpooled.wrappedBuffer(bytes);

        List<Object> decoded = new ArrayList<>();
        decoder.decode(null, in, decoded);

        assertEquals(1, decoded.size());
        TransferMessage decodedMsg = (TransferMessage) decoded.get(0);

        assertEquals(TransferMessage.MessageType.AUTH_REQUEST, decodedMsg.getMessageType());
        assertEquals("my-token", decodedMsg.getValidateFlag());
    }

    @Test
    public void testEncodeDecodeEndMessage() throws Exception {
        TransferMessage originalMsg = TransferMessage.end("test.txt");

        ByteBuf out = Unpooled.buffer();
        encoder.encode(null, originalMsg, out);

        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        ByteBuf in = Unpooled.wrappedBuffer(bytes);

        List<Object> decoded = new ArrayList<>();
        decoder.decode(null, in, decoded);

        assertEquals(1, decoded.size());
        TransferMessage decodedMsg = (TransferMessage) decoded.get(0);

        assertEquals(TransferMessage.MessageType.END, decodedMsg.getMessageType());
        assertTrue(decodedMsg.isEndFlag());
        assertEquals("test.txt", decodedMsg.getFileName());
    }

    @Test
    public void testBackwardCompatibilityLegacyMessage() throws Exception {
        String legacyJson = "{\"messageType\":\"FILE_CHUNK\",\"chunkIndex\":0,\"totalChunks\":1,\"fileName\":\"legacy.txt\",\"data\":\"SGVsbG8sIFdvcmxkIQ==\"}";
        
        byte[] jsonBytes = legacyJson.getBytes();
        ByteBuf in = Unpooled.buffer();
        in.writeInt(jsonBytes.length);
        in.writeBytes(jsonBytes);

        List<Object> decoded = new ArrayList<>();
        decoder.decode(null, in, decoded);

        assertEquals(1, decoded.size());
        TransferMessage decodedMsg = (TransferMessage) decoded.get(0);

        assertEquals(TransferMessage.MessageType.FILE_CHUNK, decodedMsg.getMessageType());
        assertEquals(TransferMessage.TransferCategory.FILE_DATA.getValue(), decodedMsg.getTransferType());
        assertEquals(0, decodedMsg.getChunkIndex());
        assertEquals(1, decodedMsg.getTotalChunks());
        assertEquals("legacy.txt", decodedMsg.getFileName());
    }

    @Test
    public void testDecodePartialMessage() throws Exception {
        byte[] data = "Hello".getBytes();
        TransferMessage originalMsg = TransferMessage.fileChunk(data, 0, 1, "test.txt", System.currentTimeMillis());

        ByteBuf out = Unpooled.buffer();
        encoder.encode(null, originalMsg, out);

        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);

        ByteBuf partialIn = Unpooled.wrappedBuffer(bytes, 0, 2);
        List<Object> decoded = new ArrayList<>();
        decoder.decode(null, partialIn, decoded);

        assertTrue(decoded.isEmpty());
    }

    @Test
    public void testEncodeDecodeWithChunkParams() throws Exception {
        byte[] data = "test data".getBytes();
        ChunkParams chunkParams = new ChunkParams(2, 10, 512, 1024);
        FileMetadata fileMetadata = new FileMetadata("chunked.bin", 5120, "bin", 
            System.currentTimeMillis(), System.currentTimeMillis(), "chunked-md5");
        
        TransferMessage originalMsg = TransferMessage.fileChunkWithMetadata(data, chunkParams, fileMetadata);

        ByteBuf out = Unpooled.buffer();
        encoder.encode(null, originalMsg, out);

        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        ByteBuf in = Unpooled.wrappedBuffer(bytes);

        List<Object> decoded = new ArrayList<>();
        decoder.decode(null, in, decoded);

        assertEquals(1, decoded.size());
        TransferMessage decodedMsg = (TransferMessage) decoded.get(0);

        assertNotNull(decodedMsg.getChunkParams());
        assertEquals(2, decodedMsg.getChunkParams().getChunkIndex());
        assertEquals(10, decodedMsg.getChunkParams().getTotalChunks());
        assertEquals(512, decodedMsg.getChunkParams().getChunkSize());
        assertEquals(1024, decodedMsg.getChunkParams().getOffset());

        assertNotNull(decodedMsg.getFileMetadata());
        assertEquals("chunked.bin", decodedMsg.getFileMetadata().getFileName());
        assertEquals("chunked-md5", decodedMsg.getFileMetadata().getMd5());
    }

    @Test
    public void testInvalidFrameLength() {
        ByteBuf in = Unpooled.buffer();
        in.writeInt(-1);

        List<Object> decoded = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(null, in, decoded));
    }

    @Test
    public void testExceedsMaxFrameSize() {
        ByteBuf in = Unpooled.buffer();
        in.writeInt(20 * 1024 * 1024);

        List<Object> decoded = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(null, in, decoded));
    }
}