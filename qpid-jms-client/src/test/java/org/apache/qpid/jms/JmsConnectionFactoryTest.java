/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.qpid.jms;

import static org.apache.qpid.jms.SerializationTestSupport.roundTripSerialize;
import static org.apache.qpid.jms.SerializationTestSupport.serialize;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;

import org.apache.qpid.jms.test.QpidJmsTestCase;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmsConnectionFactoryTest extends QpidJmsTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(JmsConnectionFactoryTest.class);

    private static final String CLIENT_ID_PROP = "clientID";
    private static final String QUEUE_PREFIX_PROP = "queuePrefix";
    private static final String TOPIC_PREFIX_PROP = "topicPrefix";
    private static final String USER = "USER";
    private static final String PASSWORD = "PASSWORD";

    @Test
    public void testConnectionFactoryCreate() {
        JmsConnectionFactory factory = new JmsConnectionFactory();
        assertNull(factory.getUsername());
        assertNull(factory.getPassword());
    }

    @Test
    public void testConnectionFactoryCreateUsernameAndPassword() {
        JmsConnectionFactory factory = new JmsConnectionFactory(USER, PASSWORD);
        assertNotNull(factory.getUsername());
        assertNotNull(factory.getPassword());
        assertEquals(USER, factory.getUsername());
        assertEquals(PASSWORD, factory.getPassword());
    }

    @Test
    public void testConnectionFactoryOptionsAreAppliedToConnection() throws JMSException {
        JmsConnectionFactory factory = new JmsConnectionFactory(USER, PASSWORD, "mock://localhost");

        factory.setTopicPrefix(TOPIC_PREFIX_PROP);
        factory.setQueuePrefix(QUEUE_PREFIX_PROP);
        factory.setClientID(CLIENT_ID_PROP);

        factory.setForceSyncSend(!factory.isForceSyncSend());
        factory.setForceAsyncSend(!factory.isForceAsyncSend());
        factory.setLocalMessagePriority(!factory.isLocalMessagePriority());
        factory.setForceAsyncAcks(!factory.isForceAsyncAcks());
        factory.setConnectTimeout(TimeUnit.SECONDS.toMillis(30));
        factory.setCloseTimeout(TimeUnit.SECONDS.toMillis(45));

        factory.getPrefetchPolicy().setAll(1);

        JmsConnection connection = (JmsConnection) factory.createConnection();
        assertNotNull(connection);

        assertEquals(USER, connection.getUsername());
        assertEquals(PASSWORD, connection.getPassword());
        assertEquals(CLIENT_ID_PROP, connection.getClientID());
        assertEquals(TOPIC_PREFIX_PROP, connection.getTopicPrefix());
        assertEquals(QUEUE_PREFIX_PROP, connection.getQueuePrefix());

        assertEquals(factory.isForceSyncSend(), connection.isForceSyncSend());
        assertEquals(factory.isForceAsyncSend(), connection.isForceAsyncSend());
        assertEquals(factory.isLocalMessagePriority(), connection.isLocalMessagePriority());
        assertEquals(factory.isForceAsyncAcks(), connection.isForceAsyncAcks());

        assertEquals(TimeUnit.SECONDS.toMillis(30), connection.getConnectTimeout());
        assertEquals(TimeUnit.SECONDS.toMillis(45), connection.getCloseTimeout());

        assertEquals(1, connection.getPrefetchPolicy().getTopicPrefetch());
        assertEquals(1, connection.getPrefetchPolicy().getQueuePrefetch());
        assertEquals(1, connection.getPrefetchPolicy().getQueueBrowserPrefetch());
        assertEquals(1, connection.getPrefetchPolicy().getDurableTopicPrefetch());
    }

    @Test
    public void testConnectionGetConfiguredURIApplied() throws Exception {
        URI mock = new URI("mock://localhost");

        JmsConnectionFactory factory = new JmsConnectionFactory(mock);

        JmsConnection connection = (JmsConnection) factory.createConnection();
        assertEquals(mock, connection.getConfiguredURI());
        connection.start();
        assertEquals(mock, connection.getConnectedURI());
    }

    @Test
    public void testGlobalExceptionListenerIsAppliedToCreatedConnection() throws Exception {
        JmsConnectionFactory factory = new JmsConnectionFactory(new URI("mock://127.0.0.1:5763"));

        ExceptionListener listener = new ExceptionListener() {

            @Override
            public void onException(JMSException exception) {
            }
        };

        factory.setExceptionListener(listener);
        Connection connection = factory.createConnection();
        assertNotNull(connection);
        assertNotNull(connection.getExceptionListener());
        assertSame(listener, connection.getExceptionListener());
    }

    @Test
    public void testCreateConnectionBadProviderURI() throws Exception {
        JmsConnectionFactory factory = new JmsConnectionFactory(new URI("bad://127.0.0.1:5763"));

        try {
            factory.createConnection();
            fail("Should have thrown exception");
        } catch (JMSException jmse) {
            // expected
        }
    }

    @Test
    public void testCreateConnectionBadProviderString() throws Exception {
        JmsConnectionFactory factory = new JmsConnectionFactory("bad://127.0.0.1:5763");

        try {
            factory.createConnection();
            fail("Should have thrown exception");
        } catch (JMSException jmse) {
            // expected
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testBadUriOptionCausesFail() throws Exception {
        new JmsConnectionFactory("amqp://localhost:1234?jms.badOption=true");
    }

    @Test
    public void testCreateConnectionWithoutUriThrowsJMSISE() throws Exception {
        JmsConnectionFactory cf = new JmsConnectionFactory();
        try {
            cf.createConnection();
            fail("Should have thrown exception");
        } catch (IllegalStateException jmsise){
            // expected
        }
    }

    @Test
    public void testSetProperties() throws Exception {
        String clientID = getTestName();
        String queuePrefix = "q:";
        String jmsOptionPrefix = "jms.";
        String baseUri = "amqp://localhost:1234";
        String uri = baseUri + "?" + jmsOptionPrefix + CLIENT_ID_PROP + "=" + clientID;

        // Create a connection factory object
        JmsConnectionFactory cf = new JmsConnectionFactory();

        // Verify the outcome conditions have not been met already
        assertNotEquals("value should not match yet", clientID, cf.getClientID());
        assertNotEquals("value should not match yet", queuePrefix, cf.getQueuePrefix());
        assertNotEquals("value should not match yet", baseUri, cf.getRemoteURI());

        // Set the properties
        Map<String, String> props = new HashMap<String, String>();
        // Add the URI property, itself containing a property option in its query
        props.put("remoteURI", uri);
        // Add another property directly
        props.put("queuePrefix", queuePrefix);
        Map<String, String> unusedProps = cf.setProperties(props);

        // Verify the clientID property option from the URI was applied.
        assertEquals("uri property query option not applied as expected", clientID, cf.getClientID());
        // Verify the direct property was applied
        assertEquals("direct property not applied as expected", queuePrefix, cf.getQueuePrefix());
        // Verify the URI was filtered to remove the applied options
        assertEquals("URI was filtered to remove options that were applied", baseUri, cf.getRemoteURI());

        // Verify the returned map was empty and unmodifiable
        assertTrue("Map should be empty: " + unusedProps, unusedProps.isEmpty());
        try {
            unusedProps.put("a", "b");
            fail("Map should be unmodifiable");
        } catch (UnsupportedOperationException uoe) {
            // expected
        }
    }

    @Test
    public void testSetPropertiesWithUnusedOptions() throws Exception {
        String uri = "amqp://localhost:1234";
        String unusedKey = "unusedKey";
        String unusedValue = "unusedValue";

        // Create a connection factory object
        JmsConnectionFactory cf = new JmsConnectionFactory();

        // Verify the outcome conditions have not been met already
        assertNotEquals("value should not match yet", uri, cf.getRemoteURI());

        // Set the properties
        Map<String, String> props = new HashMap<String, String>();
        // Add a property that will get used
        props.put("remoteURI", uri);
        // Add a property that wont get used
        props.put(unusedKey, unusedValue);
        Map<String, String> unusedProps = cf.setProperties(props);

        // Verify the URI property was applied.
        assertEquals("uri property option not applied as expected", uri, cf.getRemoteURI());

        //Verify that the unused property was returned
        assertEquals("Unexpected size of return map", 1, unusedProps.size());
        assertTrue("Expected property not found in map: " + unusedProps, unusedProps.containsKey(unusedKey));
        assertEquals("Unexpected property value", unusedValue, unusedProps.get(unusedKey));

        // Verify the returned map was unmodifiable
        try {
            unusedProps.put("a", "b");
            fail("Map should be unmodifiable");
        } catch (UnsupportedOperationException uoe) {
            // expected
        }
    }

    @Test
    public void testSetPropertiesWithBadUriOptionCausesFail() throws Exception {
        JmsConnectionFactory cf = new JmsConnectionFactory();

        Map<String, String> props = new HashMap<String, String>();
        props.put("remoteURI", "amqp://localhost:1234?jms.badOption=true");

        try {
            cf.setProperties(props);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }

    @Test
    public void testGetProperties() throws Exception {
        String clientID = getTestName();
        String queuePrefix = "q:";
        String jmsOptionPrefix = "jms.";
        String clientIDprop = "clientID";
        String baseUri = "amqp://localhost:1234";
        String uri = baseUri + "?" + jmsOptionPrefix + clientIDprop + "=" + clientID;

        JmsConnectionFactory cf = new JmsConnectionFactory();

        // Set the URI property, itself containing a property option in its query
        cf.setRemoteURI(uri);
        // Set another property directly
        cf.setQueuePrefix(queuePrefix);

        // Get the properties
        Map<String, String> props = cf.getProperties();

        // Verify the clientID property option from the URI was applied.
        assertTrue(CLIENT_ID_PROP + " property not found", props.containsKey(CLIENT_ID_PROP));
        assertEquals("clientID uri property query option not applied as expected", clientID, props.get(CLIENT_ID_PROP));
        assertTrue(QUEUE_PREFIX_PROP + " property not found", props.containsKey(QUEUE_PREFIX_PROP));
        assertEquals("queue prefix property not applied as expected", queuePrefix, props.get(QUEUE_PREFIX_PROP));
    }

    @Test
    public void testSerializeThenDeserialize() throws Exception {
        String uri = "amqp://localhost:1234";

        JmsConnectionFactory cf = new JmsConnectionFactory(uri);
        Map<String, String> props = cf.getProperties();

        Object roundTripped = roundTripSerialize(cf);

        assertNotNull("Null object returned", roundTripped);
        assertEquals("Unexpected type", JmsConnectionFactory.class, roundTripped.getClass());
        assertEquals("Unexpected uri", uri, ((JmsConnectionFactory)roundTripped).getRemoteURI());

        Map<String, String> props2 = ((JmsConnectionFactory)roundTripped).getProperties();
        assertEquals("Properties were not equal", props, props2);
    }

    /**
     * The prefetch policy is maintained in a child-object, which we extract the properties from
     * when serializing the factory. Ensure this functions by doing a round trip on a factory
     * configured with some new prefetch configuration via the URI.
     *
     * @throws Exception if an error occurs during the test.
     */
    @Test
    public void testSerializeThenDeserializeMaintainsPrefetchPolicy() throws Exception {
        String topicPrefetchValue = "17";
        String topicPrefetchKey = "prefetchPolicy.topicPrefetch";
        String uri = "amqp://localhost:1234?jms." + topicPrefetchKey + "=" + topicPrefetchValue;

        JmsConnectionFactory cf = new JmsConnectionFactory(uri);
        Map<String, String> props = cf.getProperties();

        assertTrue("Props dont contain expected prefetch policy change", props.containsKey(topicPrefetchKey));
        assertEquals("Unexpected value", topicPrefetchValue, props.get(topicPrefetchKey));

        Object roundTripped = roundTripSerialize(cf);

        assertNotNull("Null object returned", roundTripped);
        assertEquals("Unexpected type", JmsConnectionFactory.class, roundTripped.getClass());

        Map<String, String> props2 = ((JmsConnectionFactory)roundTripped).getProperties();
        assertTrue("Props dont contain expected prefetch policy change", props2.containsKey(topicPrefetchKey));
        assertEquals("Unexpected value", topicPrefetchValue, props2.get(topicPrefetchKey));

        assertEquals("Properties were not equal", props, props2);
    }

    /**
     * The redelivery policy is maintained in a child-object, which we extract the properties from
     * when serializing the factory. Ensure this functions by doing a round trip on a factory
     * configured with some new redelivery configuration via the URI.
     *
     * @throws Exception if an error occurs during the test.
     */
    @Test
    public void testSerializeThenDeserializeMaintainsRedeliveryPolicy() throws Exception {
        String maxRedeliveryValue = "5";
        String maxRedeliveryKey = "redeliveryPolicy.maxRedeliveries";
        String uri = "amqp://localhost:1234?jms." + maxRedeliveryKey + "=" + maxRedeliveryValue;

        JmsConnectionFactory cf = new JmsConnectionFactory(uri);
        Map<String, String> props = cf.getProperties();

        assertTrue("Props dont contain expected redelivery policy change", props.containsKey(maxRedeliveryKey));
        assertEquals("Unexpected value", maxRedeliveryValue, props.get(maxRedeliveryKey));

        Object roundTripped = roundTripSerialize(cf);

        assertNotNull("Null object returned", roundTripped);
        assertEquals("Unexpected type", JmsConnectionFactory.class, roundTripped.getClass());

        Map<String, String> props2 = ((JmsConnectionFactory)roundTripped).getProperties();
        assertTrue("Props dont contain expected redelivery policy change", props2.containsKey(maxRedeliveryKey));
        assertEquals("Unexpected value", maxRedeliveryValue, props2.get(maxRedeliveryKey));

        assertEquals("Properties were not equal", props, props2);
    }

    @Test
    public void testSerializeTwoConnectionFactories() throws Exception {
        String uri = "amqp://localhost:1234";

        JmsConnectionFactory cf1 = new JmsConnectionFactory(uri);
        JmsConnectionFactory cf2 = new JmsConnectionFactory(uri);

        byte[] bytes1 = serialize(cf1);
        byte[] bytes2 = serialize(cf2);

        assertArrayEquals(bytes1, bytes2);
    }

    @Test
    public void testSerializeTwoDifferentConnectionFactories() throws Exception {
        JmsConnectionFactory cf1 = new JmsConnectionFactory("amqp://localhost:1234");
        JmsConnectionFactory cf2 = new JmsConnectionFactory("amqp://localhost:5678");

        byte[] bytes1 = serialize(cf1);
        byte[] bytes2 = serialize(cf2);

        try {
            assertArrayEquals(bytes1, bytes2);
            fail("Expected arrays to differ");
        } catch (AssertionError ae) {
            // Expected, pass
        }
    }

    /**
     * Verify that the 'global' exception listener set on the connection factory
     * is ignored when the factory gets serialized.
     *
     * @throws Exception if an error occurs during the test.
     */
    @Test
    public void testSerializeThenDeserializeIgnoresGlobalExceptionListener() throws Exception {
        String uri = "amqp://localhost:1234";

        JmsConnectionFactory cf = new JmsConnectionFactory(uri);
        cf.setExceptionListener(new ExceptionListener() {
            @Override
            public void onException(JMSException exception) {
                // Nothing
            }
        });

        Map<String, String> props = cf.getProperties();

        Object roundTripped = roundTripSerialize(cf);

        assertNotNull("Null object returned", roundTripped);
        assertEquals("Unexpected type", JmsConnectionFactory.class, roundTripped.getClass());
        assertEquals("Unexpected uri", uri, ((JmsConnectionFactory)roundTripped).getRemoteURI());

        Map<String, String> props2 = ((JmsConnectionFactory)roundTripped).getProperties();

        assertFalse("Properties map should not contain ExceptionListener", props.containsKey("exceptionListener"));
        assertEquals("Properties were not equal", props, props2);
    }

    @Test(timeout = 5000)
    public void testCreateConnectionWithPortOutOfRange() throws Exception {
        JmsConnectionFactory factory = new JmsConnectionFactory("amqp://127.0.0.1:567564562");

        try {
            factory.createConnection();
            fail("Should have thrown exception");
        } catch (JMSException jmse) {
            LOG.debug("Caught Ex -> ", jmse);
        }

        factory = new JmsConnectionFactory("amqp://127.0.0.1:5675645622");

        try {
            factory.createConnection();
            fail("Should have thrown exception");
        } catch (JMSException jmse) {
            LOG.debug("Caught Ex -> ", jmse);
        }
    }
}
