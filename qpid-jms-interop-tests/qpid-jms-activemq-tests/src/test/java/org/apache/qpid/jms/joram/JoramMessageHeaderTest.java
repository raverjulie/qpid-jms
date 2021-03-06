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
package org.apache.qpid.jms.joram;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.junit.After;
import org.junit.Before;
import org.objectweb.jtests.jms.conform.message.headers.MessageHeaderTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the Joram MessageHeaderTest
 */
public class JoramMessageHeaderTest extends MessageHeaderTest {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    public JoramMessageHeaderTest(String name) {
        super(name);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        LOG.info("========== Starting test: " + getName() + " ==========");
        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        LOG.info("========== Finsished test: " + getName() + " ==========");
        super.tearDown();
    }

    public static Test suite() {
       return new TestSuite(JoramMessageHeaderTest.class);
    }
}
