/**
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
package org.apache.qpid.jms.provider.amqp;

import java.util.HashMap;
import java.util.Map;

import javax.jms.InvalidDestinationException;

import org.apache.qpid.jms.meta.JmsSessionInfo;
import org.apache.qpid.jms.provider.AsyncResult;
import org.apache.qpid.jms.provider.NoOpAsyncResult;
import org.apache.qpid.jms.provider.WrappedAsyncResult;
import org.apache.qpid.jms.provider.amqp.builders.AmqpResourceBuilder;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subclass of the standard session object used solely by AmqpConnection to
 * aid in managing connection resources that require a persistent session.
 */
public class AmqpConnectionSession extends AmqpSession {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpConnectionSession.class);

    private final Map<String, AsyncResult> pendingUnsubs = new HashMap<String, AsyncResult>();

    /**
     * Create a new instance of a Connection owned Session object.
     *
     * @param connection
     *        the connection that owns this session.
     * @param info
     *        the <code>JmsSessionInfo</code> for the Session to create.
     * @param session
     *        the Proton session instance that this resource wraps.
     */
    public AmqpConnectionSession(AmqpConnection connection, JmsSessionInfo info, Session session) {
        super(connection, info, session);
    }

    /**
     * Used to remove an existing durable topic subscription from the remote broker.
     *
     * @param subscriptionName
     *        the subscription name that is to be removed.
     * @param request
     *        the request that awaits the completion of this action.
     */
    public void unsubscribe(String subscriptionName, AsyncResult request) {
        DurableSubscriptionReattachBuilder builder = new DurableSubscriptionReattachBuilder(this, getResourceInfo(), subscriptionName);
        DurableSubscriptionReattachRequest subscribeRequest = new DurableSubscriptionReattachRequest(builder, request);
        pendingUnsubs.put(subscriptionName, subscribeRequest);

        LOG.debug("Attempting remove of subscription: {}", subscriptionName);
        builder.buildResource(subscribeRequest);
    }

    private class DurableSubscriptionReattach extends AmqpAbstractResource<JmsSessionInfo, Receiver> {

        public DurableSubscriptionReattach(JmsSessionInfo resource, Receiver receiver) {
            super(resource, receiver);
        }

        public String getSubscriptionName() {
            return getEndpoint().getName();
        }
    }

    private final class DurableSubscriptionReattachBuilder extends AmqpResourceBuilder<DurableSubscriptionReattach, AmqpSession, JmsSessionInfo, Receiver> {

        private final String subscriptionName;

        public DurableSubscriptionReattachBuilder(AmqpSession parent, JmsSessionInfo resourceInfo, String subscriptionName) {
            super(parent, resourceInfo);

            this.subscriptionName = subscriptionName;
        }

        @Override
        protected Receiver createEndpoint(JmsSessionInfo resourceInfo) {
            Receiver receiver = getParent().getEndpoint().receiver(subscriptionName);
            receiver.setTarget(new Target());
            receiver.setSenderSettleMode(SenderSettleMode.UNSETTLED);
            receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);

            return receiver;
        }

        @Override
        protected DurableSubscriptionReattach createResource(AmqpSession parent, JmsSessionInfo resourceInfo, Receiver endpoint) {
            return new DurableSubscriptionReattach(resourceInfo, endpoint);
        }

        @Override
        protected boolean isClosePending() {
            // When no link terminus was created, the peer will now detach/close us otherwise
            // we need to validate the returned remote source prior to open completion.
            return endpoint.getRemoteSource() == null;
        }
    }

    private class DurableSubscriptionReattachRequest extends WrappedAsyncResult {

        private final DurableSubscriptionReattachBuilder subscriberBuilder;

        public DurableSubscriptionReattachRequest(DurableSubscriptionReattachBuilder subscriberBuilder, AsyncResult originalRequest) {
            super(originalRequest);
            this.subscriberBuilder = subscriberBuilder;
        }

        @Override
        public void onSuccess() {
            DurableSubscriptionReattach subscriber = subscriberBuilder.getResource();
            LOG.trace("Reattached to subscription: {}", subscriber.getSubscriptionName());
            pendingUnsubs.remove(subscriber.getSubscriptionName());
            if (subscriber.getEndpoint().getRemoteSource() != null) {
                subscriber.close(getWrappedRequest());
            } else {
                subscriber.close(NoOpAsyncResult.INSTANCE);
                getWrappedRequest().onFailure(new InvalidDestinationException("Cannot remove a subscription that does not exist"));
            }
        }

        @Override
        public void onFailure(Throwable result) {
            DurableSubscriptionReattach subscriber = subscriberBuilder.getResource();
            LOG.trace("Failed to reattach to subscription: {}", subscriber.getSubscriptionName());
            pendingUnsubs.remove(subscriber.getSubscriptionName());
            subscriber.resourceClosed();
            super.onFailure(result);
        }
    }
}
