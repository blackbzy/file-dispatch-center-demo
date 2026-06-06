package com.example.common.codec;

import com.example.common.config.TransferConstants;
import com.example.common.exception.SignatureVerificationException;
import com.example.common.message.TransferMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class MessageSignatureTest {

    private MessageSignature signature;
    private MessageEncoder encoder;
    private MessageDecoder decoder;

    @BeforeEach
    public void setUp() {
        signature = new MessageSignature();
        encoder = new MessageEncoder(true);
        decoder = new MessageDecoder(true, TransferConstants.SIGNATURE_TIMESTAMP_TOLERANCE);
    }

    @Test
    public void testGenerateAndVerifySignature() {
        TransferMessage msg = TransferMessage.authRequest("test-token");
        String sig = signature.generateSignature(msg);
        
        assertNotNull(sig);
        assertFalse(sig.isEmpty());
    }

    @Test
    public void testSignatureVerificationSuccess() {
        TransferMessage msg = TransferMessage.fileChunk(
            "test data".getBytes(), 0, 1, "test.txt", System.currentTimeMillis());
        msg.setTimestamp(System.currentTimeMillis());
        msg.setSignature(signature.generateSignature(msg));
        
        assertTrue(signature.verifySignature(msg));
    }

    @Test
    public void testSignatureVerificationFailure() {
        TransferMessage msg = TransferMessage.fileChunk(
            "test data".getBytes(), 0, 1, "test.txt", System.currentTimeMillis());
        msg.setTimestamp(System.currentTimeMillis());
        msg.setSignature("invalid-signature");
        
        assertFalse(signature.verifySignature(msg));
    }

    @Test
    public void testSignatureVerificationNullMessage() {
        assertFalse(signature.verifySignature(null));
    }

    @Test
    public void testSignatureVerificationNullSignature() {
        TransferMessage msg = TransferMessage.authRequest("test-token");
        assertFalse(signature.verifySignature(msg));
    }

    @Test
    public void testSignatureVerificationEmptySignature() {
        TransferMessage msg = TransferMessage.authRequest("test-token");
        msg.setSignature("");
        assertFalse(signature.verifySignature(msg));
    }

    @Test
    public void testEncodeAndDecodeWithSignature() throws Exception {
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

        assertNotNull(decodedMsg.getSignature());
        assertTrue(decodedMsg.getSignature().length() > 0);
        assertTrue(decodedMsg.getTimestamp() > 0);
        assertEquals(originalMsg.getMessageType(), decodedMsg.getMessageType());
        assertArrayEquals(data, decodedMsg.getData());
    }

    @Test
    public void testDecodeMessageWithInvalidSignature() throws Exception {
        TransferMessage msg = TransferMessage.fileChunk(
            "test data".getBytes(), 0, 1, "test.txt", System.currentTimeMillis());
        
        ByteBuf out = Unpooled.buffer();
        encoder.encode(null, msg, out);
        
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        
        String jsonStr = new String(bytes, 4, bytes.length - 4);
        String modifiedJson = jsonStr.replace("signature", "signature_modified");
        byte[] modifiedBytes = (bytes.length - 4 + "").getBytes();
        
        int length = modifiedJson.length();
        ByteBuf modifiedIn = Unpooled.buffer();
        modifiedIn.writeInt(length);
        modifiedIn.writeBytes(modifiedJson.getBytes());
        
        List<Object> decoded = new ArrayList<>();
        assertThrows(SignatureVerificationException.class, () -> decoder.decode(null, modifiedIn, decoded));
    }

    @Test
    public void testSignatureWithDifferentSecretKey() {
        MessageSignature sig1 = new MessageSignature("key1", "HmacSHA256");
        MessageSignature sig2 = new MessageSignature("key2", "HmacSHA256");
        
        TransferMessage msg = TransferMessage.authRequest("test-token");
        msg.setTimestamp(System.currentTimeMillis());
        
        String signature1 = sig1.generateSignature(msg);
        msg.setSignature(signature1);
        
        assertFalse(sig2.verifySignature(msg));
    }

    @Test
    public void testMD5Calculation() {
        byte[] data = "Hello, World!".getBytes();
        String md5 = MessageSignature.calculateMD5(data);
        
        assertNotNull(md5);
        assertEquals(32, md5.length());
        assertEquals("65a8e27d8879283831b664bd8b7f0ad4", md5);
    }

    @Test
    public void testMD5Verification() {
        byte[] data = "Hello, World!".getBytes();
        String md5 = "65a8e27d8879283831b664bd8b7f0ad4";
        
        assertTrue(MessageSignature.verifyMD5(data, md5));
        assertFalse(MessageSignature.verifyMD5(data, "invalid-md5"));
    }

    @Test
    public void testSignatureTimestampValidation() throws Exception {
        TransferMessage msg = TransferMessage.fileChunk(
            "test data".getBytes(), 0, 1, "test.txt", System.currentTimeMillis());
        
        ByteBuf out = Unpooled.buffer();
        encoder.encode(null, msg, out);
        
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        ByteBuf in = Unpooled.wrappedBuffer(bytes);
        
        List<Object> decoded = new ArrayList<>();
        decoder.decode(null, in, decoded);
        
        assertEquals(1, decoded.size());
        TransferMessage decodedMsg = (TransferMessage) decoded.get(0);
        assertTrue(decodedMsg.getTimestamp() > 0);
    }

    @Test
    public void testEncoderWithoutSignature() throws Exception {
        MessageEncoder encoderNoSig = new MessageEncoder(false);
        TransferMessage msg = TransferMessage.authRequest("test-token");
        
        ByteBuf out = Unpooled.buffer();
        encoderNoSig.encode(null, msg, out);
        
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        ByteBuf in = Unpooled.wrappedBuffer(bytes);
        
        MessageDecoder decoderNoSig = new MessageDecoder(false, 0);
        List<Object> decoded = new ArrayList<>();
        decoderNoSig.decode(null, in, decoded);
        
        assertEquals(1, decoded.size());
        TransferMessage decodedMsg = (TransferMessage) decoded.get(0);
        assertNull(decodedMsg.getSignature());
    }

    @Test
    public void testSignatureWithFileMetadata() throws Exception {
        com.example.common.message.FileMetadata metadata = new com.example.common.message.FileMetadata(
            "test.pdf", 1024000, "pdf", System.currentTimeMillis(), System.currentTimeMillis(), "md5hash");
        TransferMessage msg = TransferMessage.fileMetadata(metadata);

        ByteBuf out = Unpooled.buffer();
        encoder.encode(null, msg, out);

        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        ByteBuf in = Unpooled.wrappedBuffer(bytes);

        List<Object> decoded = new ArrayList<>();
        decoder.decode(null, in, decoded);

        assertEquals(1, decoded.size());
        TransferMessage decodedMsg = (TransferMessage) decoded.get(0);

        assertNotNull(decodedMsg.getSignature());
        assertEquals(TransferMessage.MessageType.FILE_METADATA, decodedMsg.getMessageType());
        assertNotNull(decodedMsg.getFileMetadata());
        assertEquals("test.pdf", decodedMsg.getFileMetadata().getFileName());
    }

    @Test
    public void testSignatureDataIntegrity() throws Exception {
        byte[] data = "Sensitive data".getBytes();
        TransferMessage msg = TransferMessage.fileChunk(data, 0, 1, "secret.txt", System.currentTimeMillis());

        ByteBuf out = Unpooled.buffer();
        encoder.encode(null, msg, out);

        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        ByteBuf in = Unpooled.wrappedBuffer(bytes);

        List<Object> decoded = new ArrayList<>();
        decoder.decode(null, in, decoded);

        assertEquals(1, decoded.size());
        TransferMessage decodedMsg = (TransferMessage) decoded.get(0);

        assertArrayEquals(data, decodedMsg.getData());
        assertEquals("secret.txt", decodedMsg.getFileName());
        assertEquals(0, decodedMsg.getChunkIndex());
        assertEquals(1, decodedMsg.getTotalChunks());
    }
}