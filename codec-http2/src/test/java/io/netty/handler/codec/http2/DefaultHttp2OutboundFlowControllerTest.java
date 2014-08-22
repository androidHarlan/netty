/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2CodecUtil.CONNECTION_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_WINDOW_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.DefaultHttp2OutboundFlowController.OutboundFlowState;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link DefaultHttp2OutboundFlowController}.
 */
public class DefaultHttp2OutboundFlowControllerTest {
    private static final int STREAM_A = 1;
    private static final int STREAM_B = 3;
    private static final int STREAM_C = 5;
    private static final int STREAM_D = 7;
    private static final int STREAM_E = 9;

    private DefaultHttp2OutboundFlowController controller;

    @Mock
    private ByteBuf buffer;

    @Mock
    private Http2FrameWriter frameWriter;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private ChannelPromise promise;

    private DefaultHttp2Connection connection;

    @Before
    public void setup() throws Http2Exception {
        MockitoAnnotations.initMocks(this);

        when(ctx.newPromise()).thenReturn(promise);

        connection = new DefaultHttp2Connection(false);
        controller = new DefaultHttp2OutboundFlowController(connection, frameWriter);

        connection.local().createStream(STREAM_A, false);
        connection.local().createStream(STREAM_B, false);
        Http2Stream streamC = connection.local().createStream(STREAM_C, false);
        Http2Stream streamD = connection.local().createStream(STREAM_D, false);
        streamC.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, false);

        when(frameWriter.maxFrameSize()).thenReturn(Integer.MAX_VALUE);
    }

    @Test
    public void frameShouldBeSentImmediately() throws Http2Exception {
        ByteBuf data = dummyData(10);
        send(STREAM_A, data.slice());
        verifyWrite(STREAM_A, data);
        assertEquals(1, data.refCnt());
        data.release();
    }

    @Test
    public void frameShouldSplitForMaxFrameSize() throws Http2Exception {
        when(frameWriter.maxFrameSize()).thenReturn(5);
        ByteBuf data = dummyData(10);
        ByteBuf slice1 = data.slice(data.readerIndex(), 5);
        ByteBuf slice2 = data.slice(5, 5);
        send(STREAM_A, data.slice());
        verifyWrite(STREAM_A, slice1);
        verifyWrite(STREAM_A, slice2);
        assertEquals(2, data.refCnt());
        data.release(2);
    }

    @Test
    public void stalledStreamShouldQueueFrame() throws Http2Exception {
        controller.initialOutboundWindowSize(0);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyNoWrite(STREAM_A);
        assertEquals(1, data.refCnt());
        data.release();
    }

    @Test
    public void nonZeroWindowShouldSendPartialFrame() throws Http2Exception {
        controller.initialOutboundWindowSize(5);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);

        // Verify that a partial frame of 5 was sent.
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(5, writtenBuf.readableBytes());
        assertEquals(data.slice(0, 5), writtenBuf);
        assertEquals(2, writtenBuf.refCnt());
        assertEquals(2, data.refCnt());
        data.release(2);
    }

    @Test
    public void initialWindowUpdateShouldSendFrame() throws Http2Exception {
        controller.initialOutboundWindowSize(0);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data.slice());
        verifyNoWrite(STREAM_A);

        // Verify that the entire frame was sent.
        controller.initialOutboundWindowSize(10);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(data, writtenBuf);
        assertEquals(1, data.refCnt());
        data.release();
    }

    @Test
    public void initialWindowUpdateShouldSendPartialFrame() throws Http2Exception {
        controller.initialOutboundWindowSize(0);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyNoWrite(STREAM_A);

        // Verify that a partial frame of 5 was sent.
        controller.initialOutboundWindowSize(5);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(5, writtenBuf.readableBytes());
        assertEquals(data.slice(0, 5), writtenBuf);
        assertEquals(2, writtenBuf.refCnt());
        assertEquals(2, data.refCnt());
        data.release(2);
    }

    @Test
    public void connectionWindowUpdateShouldSendFrame() throws Http2Exception {
        // Set the connection window size to zero.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data.slice());
        verifyNoWrite(STREAM_A);

        // Verify that the entire frame was sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 10);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(data, writtenBuf);
        assertEquals(1, data.refCnt());
        data.release();
    }

    @Test
    public void connectionWindowUpdateShouldSendPartialFrame() throws Http2Exception {
        // Set the connection window size to zero.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyNoWrite(STREAM_A);

        // Verify that a partial frame of 5 was sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 5);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(5, writtenBuf.readableBytes());
        assertEquals(data.slice(0, 5), writtenBuf);
        assertEquals(2, writtenBuf.refCnt());
        assertEquals(2, data.refCnt());
        data.release(2);
    }

    @Test
    public void streamWindowUpdateShouldSendFrame() throws Http2Exception {
        // Set the stream window size to zero.
        controller.updateOutboundWindowSize(STREAM_A, -DEFAULT_WINDOW_SIZE);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data.slice());
        verifyNoWrite(STREAM_A);

        // Verify that the entire frame was sent.
        controller.updateOutboundWindowSize(STREAM_A, 10);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(data, writtenBuf);
        assertEquals(1, data.refCnt());
        data.release();
    }

    @Test
    public void streamWindowUpdateShouldSendPartialFrame() throws Http2Exception {
        // Set the stream window size to zero.
        controller.updateOutboundWindowSize(STREAM_A, -DEFAULT_WINDOW_SIZE);

        ByteBuf data = dummyData(10);
        send(STREAM_A, data);
        verifyNoWrite(STREAM_A);

        // Verify that a partial frame of 5 was sent.
        controller.updateOutboundWindowSize(STREAM_A, 5);
        ArgumentCaptor<ByteBuf> argument = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_A, argument, false);
        ByteBuf writtenBuf = argument.getValue();
        assertEquals(5, writtenBuf.readableBytes());
        assertEquals(2, writtenBuf.refCnt());
        assertEquals(2, data.refCnt());
        data.release(2);
    }

    /**
     * In this test, we block A which allows bytes to be written by C and D. Here's a view of the tree (stream A is
     * blocked).
     *
     * <pre>
     *         0
     *        / \
     *      [A]  B
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void blockedStreamShouldSpreadDataToChildren() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        // Block stream A
        controller.updateOutboundWindowSize(STREAM_A, -DEFAULT_WINDOW_SIZE);

        // Try sending 10 bytes on each stream. They will be pending until we free up the
        // connection.
        send(STREAM_A, dummyData(10));
        send(STREAM_B, dummyData(10));
        send(STREAM_C, dummyData(10));
        send(STREAM_D, dummyData(10));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);

        // Verify that the entire frame was sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 10);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);

        // Verify that no write was done for A, since it's blocked.
        verifyNoWrite(STREAM_A);

        captureWrite(STREAM_B, captor, false);
        assertEquals(5, captor.getValue().readableBytes());

        // Verify that C and D each shared half of A's allowance. Since A's allowance (5) cannot
        // be split evenly, one will get 3 and one will get 2.
        captureWrite(STREAM_C, captor, false);
        int c = captor.getValue().readableBytes();
        captureWrite(STREAM_D, captor, false);
        int d = captor.getValue().readableBytes();
        assertEquals(5, c + d);
        assertEquals(1, Math.abs(c - d));
    }

    /**
     * In this test, we block B which allows all bytes to be written by A. A should not share the data with its children
     * since it's not blocked.
     *
     * <pre>
     *         0
     *        / \
     *       A  [B]
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void childrenShouldNotSendDataUntilParentBlocked() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        // Block stream B
        controller.updateOutboundWindowSize(STREAM_B, -DEFAULT_WINDOW_SIZE);

        // Send 10 bytes to each.
        send(STREAM_A, dummyData(10));
        send(STREAM_B, dummyData(10));
        send(STREAM_C, dummyData(10));
        send(STREAM_D, dummyData(10));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);

        // Verify that the entire frame was sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 10);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);

        // Verify that A received all the bytes.
        captureWrite(STREAM_A, captor, false);
        assertEquals(10, captor.getValue().readableBytes());
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);
    }

    /**
     * In this test, we block B which allows all bytes to be written by A. Once A is blocked, it will spill over the
     * remaining of its portion to its children.
     *
     * <pre>
     *         0
     *        / \
     *       A  [B]
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void parentShouldWaterFallDataToChildren() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        // Block stream B
        controller.updateOutboundWindowSize(STREAM_B, -DEFAULT_WINDOW_SIZE);

        // Only send 5 to A so that it will allow data from its children.
        send(STREAM_A, dummyData(5));
        send(STREAM_B, dummyData(10));
        send(STREAM_C, dummyData(10));
        send(STREAM_D, dummyData(10));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);

        // Verify that the entire frame was sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 10);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);

        // Verify that no write was done for B, since it's blocked.
        verifyNoWrite(STREAM_B);

        captureWrite(STREAM_A, captor, false);
        assertEquals(5, captor.getValue().readableBytes());

        // Verify that C and D each shared half of A's allowance. Since A's allowance (5) cannot
        // be split evenly, one will get 3 and one will get 2.
        captureWrite(STREAM_C, captor, false);
        int c = captor.getValue().readableBytes();
        captureWrite(STREAM_D, captor, false);
        int d = captor.getValue().readableBytes();
        assertEquals(5, c + d);
        assertEquals(1, Math.abs(c - d));
    }

    /**
     * In this test, we verify re-prioritizing a stream. We start out with B blocked:
     *
     * <pre>
     *         0
     *        / \
     *       A  [B]
     *      / \
     *     C   D
     * </pre>
     *
     * We then re-prioritize D so that it's directly off of the connection and verify that A and D split the written
     * bytes between them.
     *
     * <pre>
     *           0
     *          /|\
     *        /  |  \
     *       A  [B]  D
     *      /
     *     C
     * </pre>
     */
    @Test
    public void reprioritizeShouldAdjustOutboundFlow() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        // Block stream B
        controller.updateOutboundWindowSize(STREAM_B, -DEFAULT_WINDOW_SIZE);

        // Send 10 bytes to each.
        send(STREAM_A, dummyData(10));
        send(STREAM_B, dummyData(10));
        send(STREAM_C, dummyData(10));
        send(STREAM_D, dummyData(10));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);

        // Re-prioritize D as a direct child of the connection.
        setPriority(STREAM_D, 0, DEFAULT_PRIORITY_WEIGHT, false);

        // Verify that the entire frame was sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 10);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);

        // Verify that A received all the bytes.
        captureWrite(STREAM_A, captor, false);
        assertEquals(5, captor.getValue().readableBytes());
        captureWrite(STREAM_D, captor, false);
        assertEquals(5, captor.getValue().readableBytes());
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
    }

    /**
     * In this test, we root all streams at the connection, and then verify that data is split appropriately based on
     * weight (all available data is the same).
     *
     * <pre>
     *           0
     *        / / \ \
     *       A B   C D
     * </pre>
     */
    @Test
    public void writeShouldPreferHighestWeight() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        // Root the streams at the connection and assign weights.
        setPriority(STREAM_A, 0, (short) 50, false);
        setPriority(STREAM_B, 0, (short) 200, false);
        setPriority(STREAM_C, 0, (short) 100, false);
        setPriority(STREAM_D, 0, (short) 100, false);

        // Send a bunch of data on each stream.
        send(STREAM_A, dummyData(1000));
        send(STREAM_B, dummyData(1000));
        send(STREAM_C, dummyData(1000));
        send(STREAM_D, dummyData(1000));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);

        // Allow 1000 bytes to be sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 1000);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);

        captureWrite(STREAM_A, captor, false);
        int aWritten = captor.getValue().readableBytes();
        int min = aWritten;
        int max = aWritten;

        captureWrite(STREAM_B, captor, false);
        int bWritten = captor.getValue().readableBytes();
        min = Math.min(min, bWritten);
        max = Math.max(max, bWritten);

        captureWrite(STREAM_C, captor, false);
        int cWritten = captor.getValue().readableBytes();
        min = Math.min(min, cWritten);
        max = Math.max(max, cWritten);

        captureWrite(STREAM_D, captor, false);
        int dWritten = captor.getValue().readableBytes();
        min = Math.min(min, dWritten);
        max = Math.max(max, dWritten);

        assertEquals(1000, aWritten + bWritten + cWritten + dWritten);
        assertEquals(aWritten, min);
        assertEquals(bWritten, max);
        assertTrue(aWritten < cWritten);
        assertEquals(cWritten, dWritten);
        assertTrue(cWritten < bWritten);
    }

    /**
     * In this test, we root all streams at the connection, and then verify that data is split equally among the stream,
     * since they all have the same weight.
     *
     * <pre>
     *           0
     *        / / \ \
     *       A B   C D
     * </pre>
     */
    @Test
    public void samePriorityShouldWriteEqualData() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        // Root the streams at the connection with the same weights.
        setPriority(STREAM_A, 0, DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(STREAM_B, 0, DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(STREAM_C, 0, DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(STREAM_D, 0, DEFAULT_PRIORITY_WEIGHT, false);

        // Send a bunch of data on each stream.
        send(STREAM_A, dummyData(400));
        send(STREAM_B, dummyData(500));
        send(STREAM_C, dummyData(0));
        send(STREAM_D, dummyData(700));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_D);

        // The write will occur on C, because it's an empty frame.
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        captureWrite(STREAM_C, captor, false);
        assertEquals(0, captor.getValue().readableBytes());

        // Allow 1000 bytes to be sent.
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, 999);

        captureWrite(STREAM_A, captor, false);
        int aWritten = captor.getValue().readableBytes();

        captureWrite(STREAM_B, captor, false);
        int bWritten = captor.getValue().readableBytes();

        captureWrite(STREAM_D, captor, false);
        int dWritten = captor.getValue().readableBytes();

        assertEquals(999, aWritten + bWritten + dWritten);
        assertEquals(333, aWritten);
        assertEquals(333, bWritten);
        assertEquals(333, dWritten);
    }

    /**
     * In this test, we block all streams and verify the priority bytes for each sub tree at each node are correct
     *
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void subTreeBytesShouldBeCorrect() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        // Send a bunch of data on each stream.
        IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, 400);
        streamSizes.put(STREAM_B, 500);
        streamSizes.put(STREAM_C, 600);
        streamSizes.put(STREAM_D, 700);
        send(STREAM_A, dummyData(streamSizes.get(STREAM_A)));
        send(STREAM_B, dummyData(streamSizes.get(STREAM_B)));
        send(STREAM_C, dummyData(streamSizes.get(STREAM_C)));
        send(STREAM_D, dummyData(streamSizes.get(STREAM_D)));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);

        OutboundFlowState state = state(stream0);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D)), state.priorityBytes());
        state = state(streamA);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_C, STREAM_D)), state.priorityBytes());
        state = state(streamB);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_B)), state.priorityBytes());
        state = state(streamC);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_C)), state.priorityBytes());
        state = state(streamD);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_D)), state.priorityBytes());
    }

    /**
     * In this test, we block all streams shift the priority tree and verify priority bytes for each subtree are correct
     *
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the tree shift:
     * <pre>
     *        [0]
     *         |
     *         A
     *         |
     *         B
     *        / \
     *       C   D
     * </pre>
     */
    @Test
    public void subTreeBytesShouldBeCorrectWithRestructure() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        // Send a bunch of data on each stream.
        IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, 400);
        streamSizes.put(STREAM_B, 500);
        streamSizes.put(STREAM_C, 600);
        streamSizes.put(STREAM_D, 700);
        send(STREAM_A, dummyData(streamSizes.get(STREAM_A)));
        send(STREAM_B, dummyData(streamSizes.get(STREAM_B)));
        send(STREAM_C, dummyData(streamSizes.get(STREAM_C)));
        send(STREAM_D, dummyData(streamSizes.get(STREAM_D)));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);

        streamB.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);
        OutboundFlowState state = state(stream0);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D)), state.priorityBytes());
        state = state(streamA);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D)), state.priorityBytes());
        state = state(streamB);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_B, STREAM_C, STREAM_D)), state.priorityBytes());
        state = state(streamC);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_C)), state.priorityBytes());
        state = state(streamD);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_D)), state.priorityBytes());
    }

    /**
     * In this test, we block all streams and add a node to the priority tree and verify
     *
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the tree shift:
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *       |
     *       E
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void subTreeBytesShouldBeCorrectWithAddition() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        Http2Stream streamE = connection.local().createStream(STREAM_E, false);
        streamE.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);

        // Send a bunch of data on each stream.
        IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, 400);
        streamSizes.put(STREAM_B, 500);
        streamSizes.put(STREAM_C, 600);
        streamSizes.put(STREAM_D, 700);
        streamSizes.put(STREAM_E, 900);
        send(STREAM_A, dummyData(streamSizes.get(STREAM_A)));
        send(STREAM_B, dummyData(streamSizes.get(STREAM_B)));
        send(STREAM_C, dummyData(streamSizes.get(STREAM_C)));
        send(STREAM_D, dummyData(streamSizes.get(STREAM_D)));
        send(STREAM_E, dummyData(streamSizes.get(STREAM_E)));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);
        verifyNoWrite(STREAM_E);

        OutboundFlowState state = state(stream0);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D, STREAM_E)), state.priorityBytes());
        state = state(streamA);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_E, STREAM_C, STREAM_D)), state.priorityBytes());
        state = state(streamB);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_B)), state.priorityBytes());
        state = state(streamC);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_C)), state.priorityBytes());
        state = state(streamD);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_D)), state.priorityBytes());
        state = state(streamE);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_E, STREAM_C, STREAM_D)), state.priorityBytes());
    }

    /**
     * In this test, we block all streams and remove a node from the priority tree and verify
     *
     * <pre>
     *        [0]
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the tree shift:
     * <pre>
     *        [0]
     *       / | \
     *      C  D  B
     * </pre>
     */
    @Test
    public void subTreeBytesShouldBeCorrectWithRemoval() throws Http2Exception {
        // Block the connection
        controller.updateOutboundWindowSize(CONNECTION_STREAM_ID, -DEFAULT_WINDOW_SIZE);

        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        // Send a bunch of data on each stream.
        IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, 400);
        streamSizes.put(STREAM_B, 500);
        streamSizes.put(STREAM_C, 600);
        streamSizes.put(STREAM_D, 700);
        send(STREAM_A, dummyData(streamSizes.get(STREAM_A)));
        send(STREAM_B, dummyData(streamSizes.get(STREAM_B)));
        send(STREAM_C, dummyData(streamSizes.get(STREAM_C)));
        send(STREAM_D, dummyData(streamSizes.get(STREAM_D)));
        verifyNoWrite(STREAM_A);
        verifyNoWrite(STREAM_B);
        verifyNoWrite(STREAM_C);
        verifyNoWrite(STREAM_D);

        streamA.close();

        OutboundFlowState state = state(stream0);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_B, STREAM_C, STREAM_D)), state.priorityBytes());
        state = state(streamA);
        assertEquals(0, state.priorityBytes());
        state = state(streamB);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_B)), state.priorityBytes());
        state = state(streamC);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_C)), state.priorityBytes());
        state = state(streamD);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_D)), state.priorityBytes());
    }

    private static OutboundFlowState state(Http2Stream stream) {
        return (OutboundFlowState) stream.outboundFlow();
    }

    private static int calculateStreamSizeSum(IntObjectMap<Integer> streamSizes, List<Integer> streamIds) {
        int sum = 0;
        for (int i = 0; i < streamIds.size(); ++i) {
            Integer streamSize = streamSizes.get(streamIds.get(i));
            if (streamSize != null) {
                sum += streamSize;
            }
        }
        return sum;
    }

    private void send(int streamId, ByteBuf data) throws Http2Exception {
        controller.writeData(ctx, streamId, data, 0, false, promise);
    }

    private void verifyWrite(int streamId, ByteBuf data) {
        verify(frameWriter).writeData(eq(ctx), eq(streamId), eq(data), eq(0), eq(false), eq(promise));
    }

    private void verifyNoWrite(int streamId) {
        verify(frameWriter, never()).writeData(eq(ctx), eq(streamId), any(ByteBuf.class), anyInt(), anyBoolean(),
                        eq(promise));
    }

    private void captureWrite(int streamId, ArgumentCaptor<ByteBuf> captor, boolean endStream) {
        verify(frameWriter).writeData(eq(ctx), eq(streamId), captor.capture(), eq(0), eq(endStream), eq(promise));
    }

    private void setPriority(int stream, int parent, int weight, boolean exclusive) throws Http2Exception {
        connection.stream(stream).setPriority(parent, (short) weight, exclusive);
    }

    private static ByteBuf dummyData(int size) {
        String repeatedData = "0123456789";
        ByteBuf buffer = Unpooled.buffer(size);
        for (int index = 0; index < size; ++index) {
            buffer.writeByte(repeatedData.charAt(index % repeatedData.length()));
        }
        return buffer;
    }
}
