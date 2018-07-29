/*
 * Copyright 2018 The MQTT Bee project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.mqttbee.mqtt.codec.decoder.mqtt3;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.jetbrains.annotations.Nullable;
import org.mqttbee.api.mqtt.mqtt3.message.Mqtt3MessageType;
import org.mqttbee.mqtt.codec.decoder.MqttMessageDecoder;
import org.mqttbee.mqtt.codec.decoder.MqttMessageDecoders;
import org.mqttbee.mqtt.message.publish.MqttStatefulPublish;
import org.mqttbee.util.ByteBufferUtil;

import static org.junit.jupiter.api.Assertions.*;

class Mqtt3PublishDecoderTest extends AbstractMqtt3DecoderTest {

    private static final Byte WELLFORMED_PUBLISH_BEGIN = 0b0011_0000;
    private static final Byte DUP_BIT = 0b0000_1000;
    private static final Byte RETAIN_BIT = 0b0000_001;

    Mqtt3PublishDecoderTest() {
        super(new Mqtt3PublishTestMessageDecoders());
    }

    private ByteBuf createWellformedPublish(
            final boolean dup, final int qos, final boolean retained, final int packetId, final byte[] topic,
            final byte[] payload) throws Exception {

        final ByteBuf byteBuf = channel.alloc().buffer();

        final int topicLength = topic.length;

        final int remainingLength;
        if (qos == 0) {
            remainingLength = 2 + topicLength + payload.length;
        } else {
            remainingLength = 2 + 2 + topicLength + payload.length;
        }

        if (topicLength > 1 << 7) {
            throw new Exception("Topic is too long");
        }

        if (remainingLength > 1024) {
            throw new Exception(); // too avoid numbers which must be represented with a variable byte integer. (Of course the limit could be much greater than 1024)
        }
        byte fixedHeaderFirstByte = WELLFORMED_PUBLISH_BEGIN;
        //set dup bit
        if (dup) {
            fixedHeaderFirstByte = (byte) (fixedHeaderFirstByte | DUP_BIT);
        }
        //set qos
        fixedHeaderFirstByte = (byte) (fixedHeaderFirstByte | (qos << 1));
        //set retained
        if (retained) {
            fixedHeaderFirstByte = (byte) (fixedHeaderFirstByte | RETAIN_BIT);
        }
        byteBuf.writeByte(fixedHeaderFirstByte);

        final Byte fixedHeaderSecondByte = (byte) remainingLength;
        byteBuf.writeByte(fixedHeaderSecondByte);
        byteBuf.writeShort(topicLength);
        byteBuf.writeBytes(topic);

        if (qos != 0) {
            byteBuf.writeShort(packetId);
        }

        byteBuf.writeBytes(payload);
        return byteBuf;
    }

    @ParameterizedTest
    @CsvSource({
            "true, false , 0", "false, false , 0", //all qos=0 combination
            "true, true, 1", "true, false , 1", "false, true , 1", "false, false , 1", "true, true, 2",
            "true, false , 2", "false, true , 2", "false, false , 2"
    })
    void decode_SUCCESS(final boolean retained, final boolean isDup, final int qos) throws Exception {
        final String topic = "Hello/World/Topic";
        final String payload = "Hallo World!";
        final int packetId = 1;

        final ByteBuf byteBuf = createWellformedPublish(isDup, qos, retained, 1, topic.getBytes(), payload.getBytes());
        channel.writeInbound(byteBuf);
        final MqttStatefulPublish publishInternal = channel.readInbound();
        assertNotNull(publishInternal);
        assertEquals(topic, publishInternal.getStatelessMessage().getTopic().toString());
        assertTrue(publishInternal.getStatelessMessage().getPayload().isPresent());
        assertArrayEquals(
                payload.getBytes(), ByteBufferUtil.getBytes(publishInternal.getStatelessMessage().getPayload().get()));
        assertEquals(isDup, publishInternal.isDup());
        assertEquals(qos, publishInternal.getStatelessMessage().getQos().getCode());
        if (qos == 0) {
            assertEquals(MqttStatefulPublish.NO_PACKET_IDENTIFIER_QOS_0, publishInternal.getPacketIdentifier());
        } else {
            assertEquals(packetId, publishInternal.getPacketIdentifier());
        }

    }

    @ParameterizedTest
    @CsvSource({
            "true, false , 0", "false, false , 0", //all qos=0 combination
            "true, true, 1", "true, false , 1", "false, true , 1", "false, false , 1", "true, true, 2",
            "true, false , 2", "false, true , 2", "false, false , 2"
    })
    void decode_SUCCESS_NO_PAYLOAD(final boolean retained, final boolean isDup, final int qos) throws Exception {
        final String topic = "Hello/World/Topic";
        final String payload = "";
        final int packetId = 1;

        final ByteBuf byteBuf = createWellformedPublish(isDup, qos, retained, 1, topic.getBytes(), payload.getBytes());
        channel.writeInbound(byteBuf);
        final MqttStatefulPublish publishInternal = channel.readInbound();
        assertNotNull(publishInternal);
        assertEquals(topic, publishInternal.getStatelessMessage().getTopic().toString());
        assertFalse(publishInternal.getStatelessMessage().getPayload().isPresent());
        assertEquals(isDup, publishInternal.isDup());
        assertEquals(qos, publishInternal.getStatelessMessage().getQos().getCode());
        if (qos == 0) {
            assertEquals(MqttStatefulPublish.NO_PACKET_IDENTIFIER_QOS_0, publishInternal.getPacketIdentifier());
        } else {
            assertEquals(packetId, publishInternal.getPacketIdentifier());
        }

    }

    @ParameterizedTest
    @CsvSource({
            "true, false , 0", "false, false , 0", //all qos=0 combination
            "true, true, 1", "true, false , 1", "false, true , 1", "false, false , 1", "true, true, 2",
            "true, false , 2", "false, true , 2", "false, false , 2"
    })
    void decode_SUCESS_TOO_MUCH_BYTES(final boolean retained, final boolean isDup, final int qos) throws Exception {
        final String topic = "Hello/World/Topic";
        final String payload = "test";
        final int packetId = 1;

        final ByteBuf byteBuf = createWellformedPublish(isDup, qos, retained, 1, topic.getBytes(), payload.getBytes());
        byteBuf.writeBytes("not readable".getBytes());
        channel.writeInbound(byteBuf);
        final MqttStatefulPublish publishInternal = channel.readInbound();
        assertNotNull(publishInternal);
        assertTrue(publishInternal.getStatelessMessage().getPayload().isPresent());
        assertArrayEquals(
                payload.getBytes(), ByteBufferUtil.getBytes(publishInternal.getStatelessMessage().getPayload().get()));
        assertEquals(isDup, publishInternal.isDup());
        assertEquals(qos, publishInternal.getStatelessMessage().getQos().getCode());
        if (qos == 0) {
            assertEquals(MqttStatefulPublish.NO_PACKET_IDENTIFIER_QOS_0, publishInternal.getPacketIdentifier());
        } else {
            assertEquals(packetId, publishInternal.getPacketIdentifier());
        }

    }

    @ParameterizedTest
    @ValueSource(ints = {0x2b, 0x23})
        //the wildcards 0x2b: + and 0x21: # must not be in topic
    void decode_INVALID_TOPIC(final int invalidLetter) throws Exception {
        final byte[] topic = "beispieltopic".getBytes();
        final byte invalidByte = (byte) invalidLetter;
        topic[3] = (byte) invalidLetter;
        final String payload = "example";
        final int packetId = 1;
        final ByteBuf byteBuf = createWellformedPublish(false, 1, false, 1, topic, payload.getBytes());
        channel.writeInbound(byteBuf);
        final MqttStatefulPublish publishInternal = channel.readInbound();
        assertNull(publishInternal);
        assertFalse(channel.isOpen());
    }

    @ParameterizedTest
    @CsvSource({
            "true, true", "true, false", "false, true", "false, false", //all qos=0 combination
            "true, true", "true, false", "false, true", "false, false", "true, true", "true, false", "false, true",
            "false, false"
    })
    void decode_INVALID_QOS(final boolean retained, final boolean isDup) throws Exception {
        final String topic = "Hello/World/Topic";
        final String payload = "Hallo World!";
        final int qos = 3;
        final ByteBuf byteBuf = createWellformedPublish(isDup, qos, retained, 1, topic.getBytes(), payload.getBytes());
        channel.writeInbound(byteBuf);
        final MqttStatefulPublish publishInternal = channel.readInbound();
        assertNull(publishInternal);
        assertFalse(channel.isOpen());
    }

    private static class Mqtt3PublishTestMessageDecoders implements MqttMessageDecoders {
        @Nullable
        @Override
        public MqttMessageDecoder get(final int code) {
            if (code == Mqtt3MessageType.PUBLISH.getCode()) {
                return new Mqtt3PublishDecoder();
            }
            return null;
        }
    }

}