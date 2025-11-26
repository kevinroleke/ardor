/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2024 Jelurida Swiss SA
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida
 * Swiss SA, no part of this software, including this file, may be copied,
 * modified, propagated, or distributed except according to the terms
 * contained in the LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.peer;

import nxt.DoPrivilegedTestRule;
import nxt.peer.helper.AbstractPeersTest;
import nxt.peer.overload.InboundConnectionOverload4x1Test;
import nxt.peer.overload.OutboundConnectionOverloadTest;
import nxt.peer.overload.OutboundConnectionScanTest;
import nxt.peer.overload.RegularInboundStabilityTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.util.Properties;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        OutboundConnectionOverloadTest.class,
        OutboundConnectionScanTest.class,
        //OutboundOverloadTest.class,
        RegularInboundStabilityTest.class,
        //RegularMessagingTest.class,
        //RegularOutboundStabilityTest.class,
        InboundConnectionOverload4x1Test.class
})
public class PeersNetworkTestSuite {

    /** Modify the test duration between slow (default) and fast */
    private static final boolean FAST_TEST_RUN = false;

    @ClassRule
    public static final DoPrivilegedTestRule DO_PRIVILEGED_TEST_RULE = new DoPrivilegedTestRule();

    @BeforeClass
    public static void initSuite() {

        AbstractPeersTest.setRunInSuite(true);
        if (FAST_TEST_RUN)
            AbstractPeersTest.activateFastTest();

        Properties additionalProperties = new Properties();
        additionalProperties.setProperty("nxt.peerConnectTimeout", "2");
        additionalProperties.setProperty("nxt.peerReadTimeout", "2");

        AbstractPeersTest.setAdditionalProperties(additionalProperties);
    }

    @AfterClass
    public static void shutdownSuite() {
        AbstractPeersTest.setRunInSuite(false);
        AbstractPeersTest.finalShutdown();
    }

}
