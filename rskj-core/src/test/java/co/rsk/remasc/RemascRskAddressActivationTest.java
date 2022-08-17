/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.remasc;

import co.rsk.config.RemascConfig;
import co.rsk.config.RemascConfigFactory;
import co.rsk.core.RskAddress;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.spy;

public class RemascRskAddressActivationTest {

    private static final RskAddress RSK_LABS_ADDRESS = new RskAddress("14d3065c8Eb89895f4df12450EC6b130049F8034");
    private static final RskAddress RSK_LABS_ADDRESS_RSKIP_218 = new RskAddress("dcb12179ba4697350f66224c959bdd9c282818df");
    private static final RskAddress RSK_LABS_ADDRESS_RSKIP_348 = new RskAddress("dcb12179ba4697350f66224c959bdd9c282818df"); // TODO -> Update this when ready

    private static final long FIRST_BLOCK = 1;
    private static final long RSKIP_218_ACTIVATION_HEIGHT = 2;
    private static final long RSKIP_348_ACTIVATION_HEIGHT = 3;

    @Test
    public void testRSKLabsAddressChangeOnRSKIPsActivation() {
        Block blockMock = mock(Block.class);
        ActivationConfig activationConfigMock = getActivationConfig();
        RemascConfig remascConfig = getRemascConfig();
        Remasc remasc = getRemasc(blockMock, activationConfigMock, remascConfig);

        // RSK IP #218 not yet activated
        when(blockMock.getNumber()).thenReturn(FIRST_BLOCK);

        RskAddress actualAddress = remasc.getRskLabsAddress();

        Assert.assertEquals(RSK_LABS_ADDRESS, actualAddress);
        Assert.assertEquals(FIRST_BLOCK, blockMock.getNumber());
        Assert.assertFalse(activationConfigMock.isActive(ConsensusRule.RSKIP218, blockMock.getNumber()));
        verify(remascConfig).getRskLabsAddress();

        // RSK IP #218 just activated
        when(blockMock.getNumber()).thenReturn(RSKIP_218_ACTIVATION_HEIGHT);

        actualAddress = remasc.getRskLabsAddress();

        Assert.assertEquals(RSK_LABS_ADDRESS_RSKIP_218, actualAddress);
        Assert.assertEquals(RSKIP_218_ACTIVATION_HEIGHT, blockMock.getNumber());
        Assert.assertTrue(activationConfigMock.isActive(ConsensusRule.RSKIP218, blockMock.getNumber()));
        verify(remascConfig).getRskLabsAddressRskip218();

        // RSK IP #348 just activated
        when(blockMock.getNumber()).thenReturn(RSKIP_348_ACTIVATION_HEIGHT);

        actualAddress = remasc.getRskLabsAddress();

        Assert.assertEquals(RSK_LABS_ADDRESS_RSKIP_218, actualAddress);
        Assert.assertEquals(RSKIP_348_ACTIVATION_HEIGHT, blockMock.getNumber());
        Assert.assertTrue(activationConfigMock.isActive(ConsensusRule.RSKIP348, blockMock.getNumber()));
        verify(remascConfig).getRskLabsAddressRskip348();
    }

    private ActivationConfig getActivationConfig() {
        ActivationConfig activationConfigMock = mock(ActivationConfig.class);

        when(activationConfigMock.isActive(eq(ConsensusRule.RSKIP218), geq(RSKIP_218_ACTIVATION_HEIGHT))).thenReturn(true);
        when(activationConfigMock.isActive(eq(ConsensusRule.RSKIP348), geq(RSKIP_348_ACTIVATION_HEIGHT))).thenReturn(true);

        return activationConfigMock;
    }

    private RemascConfig getRemascConfig() {
        RemascConfig remascConfigSpy = spy(new RemascConfigFactory(RemascContract.REMASC_CONFIG).createRemascConfig("regtest"));

        when(remascConfigSpy.getRskLabsAddress()).thenReturn(RSK_LABS_ADDRESS);
        when(remascConfigSpy.getRskLabsAddressRskip218()).thenReturn(RSK_LABS_ADDRESS_RSKIP_218);
        when(remascConfigSpy.getRskLabsAddressRskip348()).thenReturn(RSK_LABS_ADDRESS_RSKIP_348);

        return remascConfigSpy;
    }

    private Remasc getRemasc(Block blockMock, ActivationConfig activationConfigMock, RemascConfig remascConfigMock) {
        RemascTransaction txMock = mock(RemascTransaction.class);
        Repository repositoryMock = mock(Repository.class);
        BlockStore blockStoreMock = mock(BlockStore.class);
        List<LogInfo> logs = Collections.emptyList();

        return new Remasc(Constants.regtest(), activationConfigMock, repositoryMock,
                blockStoreMock, remascConfigMock, txMock, PrecompiledContracts.REMASC_ADDR, blockMock, logs);
    }

}
