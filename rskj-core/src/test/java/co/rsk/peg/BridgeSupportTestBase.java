package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.trie.Trie;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Repository;
import org.ethereum.db.MutableRepository;

import java.math.BigInteger;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BridgeSupportTestBase {
    protected final BridgeConstants bridgeConstantsRegtest = BridgeRegTestConstants.getInstance();
    protected final BridgeConstants bridgeConstantsMainnet = BridgeMainNetConstants.getInstance();
    protected final NetworkParameters btcRegTestParams = bridgeConstantsRegtest.getBtcParams();
    protected BridgeSupportBuilder bridgeSupportBuilder;
    protected ActivationConfig.ForBlock activations;

    protected UTXO createUTXO(Coin value, Address address) {
        return new UTXO(
            PegTestUtils.createHash(),
            1,
            value,
            0,
            false,
            ScriptBuilder.createOutputScript(address));
    }

    protected static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    protected static void mockChainOfStoredBlocks(BtcBlockStoreWithCache btcBlockStore, BtcBlock targetHeader, int headHeight, int targetHeight) throws BlockStoreException {
        // Simulate that the block is in there by mocking the getter by height,
        // and then simulate that the txs have enough confirmations by setting a high head.
        when(btcBlockStore.getStoredBlockAtMainChainHeight(targetHeight)).thenReturn(new StoredBlock(targetHeader, BigInteger.ONE, targetHeight));
        // Mock current pointer's header
        StoredBlock currentStored = mock(StoredBlock.class);
        BtcBlock currentBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(currentBlock).getHash();
        doReturn(currentBlock).when(currentStored).getHeader();
        when(currentStored.getHeader()).thenReturn(currentBlock);
        when(btcBlockStore.getChainHead()).thenReturn(currentStored);
        when(currentStored.getHeight()).thenReturn(headHeight);
    }
}
