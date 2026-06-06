package com.example.common.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChunkParamsTest {

    private ChunkParams chunkParams;

    @BeforeEach
    public void setUp() {
        chunkParams = new ChunkParams();
    }

    @Test
    public void testDefaultConstructor() {
        assertNotNull(chunkParams);
        assertEquals(0, chunkParams.getChunkIndex());
        assertEquals(0, chunkParams.getTotalChunks());
        assertEquals(0, chunkParams.getChunkSize());
        assertEquals(0, chunkParams.getOffset());
    }

    @Test
    public void testParameterizedConstructor() {
        ChunkParams params = new ChunkParams(1, 10, 1024, 1024);
        assertEquals(1, params.getChunkIndex());
        assertEquals(10, params.getTotalChunks());
        assertEquals(1024, params.getChunkSize());
        assertEquals(1024, params.getOffset());
    }

    @Test
    public void testSettersAndGetters() {
        chunkParams.setChunkIndex(2);
        chunkParams.setTotalChunks(5);
        chunkParams.setChunkSize(2048);
        chunkParams.setOffset(4096);

        assertEquals(2, chunkParams.getChunkIndex());
        assertEquals(5, chunkParams.getTotalChunks());
        assertEquals(2048, chunkParams.getChunkSize());
        assertEquals(4096, chunkParams.getOffset());
    }

    @Test
    public void testToString() {
        chunkParams.setChunkIndex(1);
        chunkParams.setTotalChunks(5);
        chunkParams.setChunkSize(1024);
        chunkParams.setOffset(1024);
        
        String result = chunkParams.toString();
        assertTrue(result.contains("chunkIndex=1"));
        assertTrue(result.contains("totalChunks=5"));
        assertTrue(result.contains("chunkSize=1024"));
        assertTrue(result.contains("offset=1024"));
    }
}