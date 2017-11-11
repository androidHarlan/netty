package io.netty.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedList;
import java.util.List;

import static io.netty.handler.codec.mqtt.MqttCodecTest.*;
import static io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE;
import static io.netty.handler.codec.mqtt.SubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MqttEncoder and MqttDecoder for MQTT v5.
 */
public class Mqtt5CodecTest {

    private static final String CLIENT_ID = "RANDOM_TEST_CLIENT";
    private static final String WILL_TOPIC = "/my_will";
    private static final byte[] WILL_MESSAGE = "gone".getBytes(CharsetUtil.UTF_8);
    private static final String USER_NAME = "happy_user";
    private static final String PASSWORD = "123_or_no_pwd";

    private static final int KEEP_ALIVE_SECONDS = 600;

    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    @Mock
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

    @Mock
    private final Channel channel = mock(Channel.class);

    private final MqttDecoder mqttDecoder = new MqttDecoderV5(new VariableHeaderDecoderV5());

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(ctx.channel()).thenReturn(channel);
    }

    @Test
    public void testConnectMessageForMqtt5() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(0x11, 10)); //session expiry interval
        final MqttConnectMessage message = createConnectV5Message(props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnectMessage decodedMessage = (MqttConnectMessage) out.get(0);

        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnectVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateConnectPayload(message.payload(), decodedMessage.payload());
    }

    private static MqttConnectMessage createConnectV5Message(MqttProperties properties) {
        return createConnectV5Message(USER_NAME, PASSWORD, properties);
    }

    private static MqttConnectMessage createConnectV5Message(String username, String password, MqttProperties properties) {
        return MqttMessageBuilders.connect()
                .clientId(CLIENT_ID)
                .protocolVersion(MqttVersion.MQTT_5)
                .username(username)
                .password(password.getBytes(CharsetUtil.UTF_8))
                .willRetain(true)
                .willQoS(AT_LEAST_ONCE)
                .willFlag(true)
                .willTopic(WILL_TOPIC)
                .willMessage(WILL_MESSAGE)
                .cleanSession(true)
                .keepAlive(KEEP_ALIVE_SECONDS)
                .properties(properties)
                .build();
    }

    @Test
    public void testConnAckMessage() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(0x11, 10)); //session expiry interval
        final MqttConnAckMessage message = createConnAckMessage(props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnAckMessage decodedMessage = (MqttConnAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnAckVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
    }

    static void validateConnAckVariableHeader(
            MqttConnAckVariableHeader expected,
            MqttConnAckVariableHeader actual) {
        MqttCodecTest.validateConnAckVariableHeader(expected, actual);
        final MqttProperties expectedProps = expected.properties();
        final MqttProperties actualProps = actual.properties();
        assertEquals(expectedProps.listAll().iterator().next().value, actualProps.listAll().iterator().next().value);
    }

    private static MqttConnAckMessage createConnAckMessage(MqttProperties properties) {
        return MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(true)
                .properties(properties)
                .build();
    }

    @Test
    public void testPublish() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(0x01, 6)); //Payload Format Indicator
        final MqttPublishMessage message = createPublishMessage(props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttPublishMessage decodedMessage = (MqttPublishMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePublishVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
    }

    private static void validatePublishVariableHeader(
            MqttPublishVariableHeader expected,
            MqttPublishVariableHeader actual) {
        MqttCodecTest.validatePublishVariableHeader(expected, actual);

        final MqttProperties expectedProps = expected.properties();
        final MqttProperties actualProps = actual.properties();
        assertEquals(expectedProps.listAll().iterator().next().value, actualProps.listAll().iterator().next().value);
    }

    @Test
    public void testPubAck() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(0x01, 6)); //Payload Format Indicator
        final MqttMessage message = createPubAckMessage((byte) 0x87, props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttMessage decodedMessage = (MqttMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePubAckVariableHeader(((MqttPubReplyMessageVariableHeader) message.variableHeader()),
                ((MqttPubReplyMessageVariableHeader) decodedMessage.variableHeader()));
    }

    private MqttMessage createPubAckMessage(byte reasonCode, MqttProperties properties) {
        return MqttMessageBuilders.pubAck()
                .packetId((short) 1)
                .reasonCode(reasonCode)
                .properties(properties)
                .build();
    }

    private static void validatePubAckVariableHeader(
            MqttPubReplyMessageVariableHeader expected,
            MqttPubReplyMessageVariableHeader actual) {
        assertEquals("MqttPubReplyMessageVariableHeader MessageId mismatch ", expected.messageId(), actual.messageId());
        assertEquals("MqttPubReplyMessageVariableHeader reasonCode mismatch ", expected.reasonCode(), actual.reasonCode());

        final MqttProperties expectedProps = expected.properties();
        final MqttProperties actualProps = actual.properties();
        assertEquals(expectedProps.listAll().iterator().next().value, actualProps.listAll().iterator().next().value);
    }

    @Test
    public void testSubAck() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(0x01, 6)); //Payload Format Indicator
        final MqttSubAckMessage message = createSubAckMessage(props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttSubAckMessage decodedMessage = (MqttSubAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePacketIdPlusPropertiesVariableHeader((MqttMessageIdPlusPropertiesVariableHeader) message.variableHeader(),
                (MqttMessageIdPlusPropertiesVariableHeader) decodedMessage.variableHeader());
    }

    private MqttSubAckMessage createSubAckMessage(MqttProperties properties) {
        return MqttMessageBuilders.subAck()
                .packetId((short) 1)
                .addGrantedQos(AT_LEAST_ONCE)
                .properties(properties)
                .build();
    }

    private void validatePacketIdPlusPropertiesVariableHeader(MqttMessageIdPlusPropertiesVariableHeader expected,
                                                              MqttMessageIdPlusPropertiesVariableHeader actual) {
        assertEquals("MqttMessageIdVariableHeader MessageId mismatch ", expected.messageId(), actual.messageId());
        final MqttProperties expectedProps = expected.properties();
        final MqttProperties actualProps = actual.properties();
        validateProperties(expectedProps, actualProps);
    }

    private void validateProperties(MqttProperties expected, MqttProperties actual) {
        assertEquals(expected.listAll().iterator().next().value, actual.listAll().iterator().next().value);
    }

    @Test
    public void testSubscribe() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(0x01, 6)); //Payload Format Indicator
        final MqttSubscribeMessage message = MqttMessageBuilders.subscribe()
                .messageId((short) 1)
                .properties(props)
                .addSubscription("/topic", new SubscriptionOption(AT_LEAST_ONCE, true, true,
                        SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS))
                .build();
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());
        final MqttSubscribeMessage decodedMessage = (MqttSubscribeMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        final MqttMessageIdPlusPropertiesVariableHeader expectedHeader =
                (MqttMessageIdPlusPropertiesVariableHeader) message.variableHeader();
        final MqttMessageIdPlusPropertiesVariableHeader actualHeader =
                (MqttMessageIdPlusPropertiesVariableHeader) decodedMessage.variableHeader();
        validatePacketIdPlusPropertiesVariableHeader(expectedHeader, actualHeader);
        validateSubscribePayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testUnsubAck() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(0x01, 6)); //Payload Format Indicator
        final MqttUnsubAckMessage message = MqttMessageBuilders.unsubAck()
                .packetId((short) 1)
                .properties(props)
                .addReasonCode((short) 0x83)
                .build();
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttUnsubAckMessage decodedMessage = (MqttUnsubAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validatePacketIdPlusPropertiesVariableHeader((MqttMessageIdPlusPropertiesVariableHeader) message.variableHeader(),
                (MqttMessageIdPlusPropertiesVariableHeader) decodedMessage.variableHeader());
        assertEquals("Reason code list doesn't match", message.payload().unsubscribeReasonCodes(),
                decodedMessage.payload().unsubscribeReasonCodes());
    }

    @Test
    public void testDisconnect() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(0x11, 6)); //Session Expiry Interval
        final MqttDisconnectMessage message = MqttMessageBuilders.disconnect()
                .reasonCode((short) 0x96) // Message rate too high
                .properties(props)
                .build();
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());
        final MqttDisconnectMessage decodedMessage = (MqttDisconnectMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        assertEquals("Reason code maust match (0x96)",
                message.variableHeader().reasonCode(), decodedMessage.variableHeader().reasonCode());
        validateProperties(message.variableHeader().properties(), decodedMessage.variableHeader().properties());
    }
}
