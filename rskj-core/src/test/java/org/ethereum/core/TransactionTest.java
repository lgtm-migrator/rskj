/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.core;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.crypto.Keccak256;
import co.rsk.db.HashMapBlocksIndex;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.MissingPrivateKeyException;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.jsontestsuite.StateTestSuite;
import org.ethereum.jsontestsuite.runners.StateTestRunner;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("squid:S1607") // many @Disabled annotations for diverse reasons
class TransactionTest {

    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    @Test /* sign transaction  https://tools.ietf.org/html/rfc6979 */
    void test1() {

        //python taken exact data
        String txRLPRawData = "a9e880872386f26fc1000085e8d4a510008203e89413978aee95f38490e9769c39b2773ed763d9cd5f80";
        // String txRLPRawData = "f82804881bc16d674ec8000094cd2a3d9f938e13cd947ec05abc7fe734df8dd8268609184e72a0006480";

        byte[] cowPrivKey = Hex.decode("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4");
        ECKey key = ECKey.fromPrivate(cowPrivKey);

        byte[] data = Hex.decode(txRLPRawData);

        // step 1: serialize + RLP encode
        // step 2: hash = sha3(step1)
        byte[] txHash = HashUtil.keccak256(data);

        ECDSASignature signature = ECDSASignature.fromSignature(key.doSign(txHash));
        Assertions.assertNotNull(signature);
        System.out.println(signature);
    }

    @Disabled
    @Test  /* achieve public key of the sender */
    void test2() throws Exception {

        // cat --> 79b08ad8787060333663d19704909ee7b1903e58
        // cow --> cd2a3d9f938e13cd947ec05abc7fe734df8dd826

        BigInteger value = new BigInteger("1000000000000000000000");

        byte[] privKey = HashUtil.keccak256("cat".getBytes());
        ECKey ecKey = ECKey.fromPrivate(privKey);

        byte[] senderPrivKey = HashUtil.keccak256("cow".getBytes());

        byte[] gasPrice = Hex.decode("09184e72a000");
        byte[] gas = Hex.decode("4255");

        // Tn (nonce); Tp(pgas); Tg(gaslimi); Tt(value); Tv(value); Ti(sender);  Tw; Tr; Ts
        Transaction tx = Transaction.builder()
                .gasPrice(gasPrice)
                .gasLimit(gas)
                .destination(ecKey.getAddress())
                .value(value.toByteArray())
                .build();

        tx.sign(senderPrivKey);

        System.out.println("v\t\t\t: " + ByteUtil.toHexString(new byte[]{tx.getSignature().getV()}));
        System.out.println("r\t\t\t: " + ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().getR())));
        System.out.println("s\t\t\t: " + ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().getS())));

        System.out.println("RLP encoded tx\t\t: " + ByteUtil.toHexString(tx.getEncoded()));

        // retrieve the signer/sender of the transaction
        ECKey key = Secp256k1.getInstance().signatureToKey(tx.getHash().getBytes(), tx.getSignature());

        System.out.println("Tx unsigned RLP\t\t: " + ByteUtil.toHexString(tx.getEncodedRaw()));
        System.out.println("Tx signed   RLP\t\t: " + ByteUtil.toHexString(tx.getEncoded()));

        System.out.println("Signature public key\t: " + ByteUtil.toHexString(key.getPubKey()));
        System.out.println("Sender is\t\t: " + ByteUtil.toHexString(key.getAddress()));

        assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826",
                ByteUtil.toHexString(key.getAddress()));

        System.out.println(tx.toString());
    }

    @Disabled
    @Test  /* achieve public key of the sender nonce: 01 */
    void test3() throws Exception {

        // cat --> 79b08ad8787060333663d19704909ee7b1903e58
        // cow --> cd2a3d9f938e13cd947ec05abc7fe734df8dd826

        ECKey ecKey = ECKey.fromPrivate(HashUtil.keccak256("cat".getBytes()));
        byte[] senderPrivKey = HashUtil.keccak256("cow".getBytes());

        byte[] nonce = {0x01};
        byte[] gasPrice = Hex.decode("09184e72a000");
        byte[] gasLimit = Hex.decode("4255");
        BigInteger value = new BigInteger("1000000000000000000000000");

        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(gasLimit)
                .destination(ecKey.getAddress())
                .value(value)
                .build();

        tx.sign(senderPrivKey);

        System.out.println("v\t\t\t: " + ByteUtil.toHexString(new byte[]{tx.getSignature().getV()}));
        System.out.println("r\t\t\t: " + ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().getR())));
        System.out.println("s\t\t\t: " + ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(tx.getSignature().getS())));

        System.out.println("RLP encoded tx\t\t: " + ByteUtil.toHexString(tx.getEncoded()));

        // retrieve the signer/sender of the transaction
        ECKey key = Secp256k1.getInstance().signatureToKey(tx.getHash().getBytes(), tx.getSignature());

        System.out.println("Tx unsigned RLP\t\t: " + ByteUtil.toHexString(tx.getEncodedRaw()));
        System.out.println("Tx signed   RLP\t\t: " + ByteUtil.toHexString(tx.getEncoded()));

        System.out.println("Signature public key\t: " + ByteUtil.toHexString(key.getPubKey()));
        System.out.println("Sender is\t\t: " + ByteUtil.toHexString(key.getAddress()));

        assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826",
                ByteUtil.toHexString(key.getAddress()));
    }

    // Testdata from: https://github.com/ethereum/tests/blob/master/txtest.json
    String RLP_ENCODED_RAW_TX = "e88085e8d4a510008227109413978aee95f38490e9769c39b2773ed763d9cd5f872386f26fc1000080";
    String RLP_ENCODED_UNSIGNED_TX = "eb8085e8d4a510008227109413978aee95f38490e9769c39b2773ed763d9cd5f872386f26fc1000080808080";
    Keccak256 HASH_TX = new Keccak256("328ea6d24659dec48adea1aced9a136e5ebdf40258db30d1b1d97ed2b74be34e");
    String RLP_ENCODED_SIGNED_TX = "f86b8085e8d4a510008227109413978aee95f38490e9769c39b2773ed763d9cd5f872386f26fc10000801ba0eab47c1a49bf2fe5d40e01d313900e19ca485867d462fe06e139e3a536c6d4f4a014a569d327dcda4b29f74f93c0e9729d2f49ad726e703f9cd90dbb0fbf6649f1";
    String KEY = "c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4";
    byte[] testNonce = Hex.decode("");
    byte[] testGasPrice = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(1000000000000L));
    byte[] testGasLimit = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(10000));
    byte[] testReceiveAddress = Hex.decode("13978aee95f38490e9769c39b2773ed763d9cd5f");
    byte[] testValue = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(10000000000000000L));
    byte[] testData = Hex.decode("");
    byte[] testInit = Hex.decode("");

    @Disabled
    @Test
    void testTransactionFromSignedRLP() {
        Transaction txSigned = new ImmutableTransaction(Hex.decode(RLP_ENCODED_SIGNED_TX));

        assertEquals(HASH_TX, txSigned.getHash());
        assertEquals(RLP_ENCODED_SIGNED_TX, ByteUtil.toHexString(txSigned.getEncoded()));

        assertEquals(BigInteger.ZERO, new BigInteger(1, txSigned.getNonce()));
        assertEquals(new BigInteger(1, testGasPrice), txSigned.getGasPrice().asBigInteger());
        assertEquals(new BigInteger(1, testGasLimit), new BigInteger(1, txSigned.getGasLimit()));
        assertEquals(ByteUtil.toHexString(testReceiveAddress), ByteUtil.toHexString(txSigned.getReceiveAddress().getBytes()));
        assertEquals(new BigInteger(1, testValue), txSigned.getValue().asBigInteger());
        assertNull(txSigned.getData());
        assertEquals(27, txSigned.getSignature().getV());
        assertEquals("eab47c1a49bf2fe5d40e01d313900e19ca485867d462fe06e139e3a536c6d4f4", ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(txSigned.getSignature().getR())));
        assertEquals("14a569d327dcda4b29f74f93c0e9729d2f49ad726e703f9cd90dbb0fbf6649f1", ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(txSigned.getSignature().getS())));
    }

    @Disabled
    @Test
    void testTransactionFromUnsignedRLP() {
        Transaction txUnsigned = new ImmutableTransaction(Hex.decode(RLP_ENCODED_UNSIGNED_TX));

        assertEquals(HASH_TX, txUnsigned.getHash());
        assertEquals(RLP_ENCODED_UNSIGNED_TX, ByteUtil.toHexString(txUnsigned.getEncoded()));
        txUnsigned.sign(Hex.decode(KEY));
        assertEquals(RLP_ENCODED_SIGNED_TX, ByteUtil.toHexString(txUnsigned.getEncoded()));

        assertEquals(BigInteger.ZERO, new BigInteger(1, txUnsigned.getNonce()));
        assertEquals(new BigInteger(1, testGasPrice), txUnsigned.getGasPrice().asBigInteger());
        assertEquals(new BigInteger(1, testGasLimit), new BigInteger(1, txUnsigned.getGasLimit()));
        assertEquals(ByteUtil.toHexString(testReceiveAddress), ByteUtil.toHexString(txUnsigned.getReceiveAddress().getBytes()));
        assertEquals(new BigInteger(1, testValue), txUnsigned.getValue().asBigInteger());
        assertNull(txUnsigned.getData());
        assertEquals(27, txUnsigned.getSignature().getV());
        assertEquals("eab47c1a49bf2fe5d40e01d313900e19ca485867d462fe06e139e3a536c6d4f4", ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(txUnsigned.getSignature().getR())));
        assertEquals("14a569d327dcda4b29f74f93c0e9729d2f49ad726e703f9cd90dbb0fbf6649f1", ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(txUnsigned.getSignature().getS())));
    }

    @Disabled
    @Test
    void testTransactionFromNew1() throws MissingPrivateKeyException {
        Transaction txNew = Transaction.builder()
                .nonce(testNonce)
                .gasPrice(testGasPrice)
                .gasLimit(testGasLimit)
                .destination(testReceiveAddress)
                .value(testValue)
                .data(testData)
                .build();

        assertEquals("", ByteUtil.toHexString(txNew.getNonce()));
        assertEquals(new BigInteger(1, testGasPrice), txNew.getGasPrice().asBigInteger());
        assertEquals(new BigInteger(1, testGasLimit), new BigInteger(1, txNew.getGasLimit()));
        assertEquals(ByteUtil.toHexString(testReceiveAddress), ByteUtil.toHexString(txNew.getReceiveAddress().getBytes()));
        assertEquals(new BigInteger(1, testValue), txNew.getValue().asBigInteger());
        assertEquals("", ByteUtil.toHexString(txNew.getData()));
        assertNull(txNew.getSignature());

        assertEquals(RLP_ENCODED_RAW_TX, ByteUtil.toHexString(txNew.getEncodedRaw()));
        assertEquals(HASH_TX, txNew.getHash());
        assertEquals(RLP_ENCODED_UNSIGNED_TX, ByteUtil.toHexString(txNew.getEncoded()));
        txNew.sign(Hex.decode(KEY));
        assertEquals(RLP_ENCODED_SIGNED_TX, ByteUtil.toHexString(txNew.getEncoded()));

        assertEquals(27, txNew.getSignature().getV());
        assertEquals("eab47c1a49bf2fe5d40e01d313900e19ca485867d462fe06e139e3a536c6d4f4", ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(txNew.getSignature().getR())));
        assertEquals("14a569d327dcda4b29f74f93c0e9729d2f49ad726e703f9cd90dbb0fbf6649f1", ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(txNew.getSignature().getS())));
    }

    @Disabled
    @Test
    void testTransactionFromNew2() throws MissingPrivateKeyException {
        byte[] privKeyBytes = Hex.decode("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4");

        String RLP_TX_UNSIGNED = "eb8085e8d4a510008227109413978aee95f38490e9769c39b2773ed763d9cd5f872386f26fc1000080808080";
        String RLP_TX_SIGNED = "f86b8085e8d4a510008227109413978aee95f38490e9769c39b2773ed763d9cd5f872386f26fc10000801ba0eab47c1a49bf2fe5d40e01d313900e19ca485867d462fe06e139e3a536c6d4f4a014a569d327dcda4b29f74f93c0e9729d2f49ad726e703f9cd90dbb0fbf6649f1";
        Keccak256 HASH_TX_UNSIGNED = new Keccak256("328ea6d24659dec48adea1aced9a136e5ebdf40258db30d1b1d97ed2b74be34e");

        byte[] nonce = BigIntegers.asUnsignedByteArray(BigInteger.ZERO);
        byte[] gasPrice = Hex.decode("e8d4a51000");     // 1000000000000
        byte[] gas = Hex.decode("2710");           // 10000
        byte[] recieveAddress = Hex.decode("13978aee95f38490e9769c39b2773ed763d9cd5f");
        byte[] value = Hex.decode("2386f26fc10000"); //10000000000000000"
        byte[] data = new byte[0];

        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(gas)
                .destination(recieveAddress)
                .value(value)
                .data(data)
                .build();

        // Testing unsigned
        String encodedUnsigned = ByteUtil.toHexString(tx.getEncoded());
        assertEquals(RLP_TX_UNSIGNED, encodedUnsigned);
        assertEquals(HASH_TX_UNSIGNED, tx.getHash());

        // Testing signed
        tx.sign(privKeyBytes);
        String encodedSigned = ByteUtil.toHexString(tx.getEncoded());
        assertEquals(RLP_TX_SIGNED, encodedSigned);
        assertEquals(HASH_TX_UNSIGNED, tx.getHash());
    }

    @Test
    void testTransactionCreateContract() {

//        String rlp =
// "f89f808609184e72a0008203e8808203e8b84b4560005444602054600f60056002600a02010b0d630000001d596002602054630000003b5860066000530860056006600202010a0d6300000036596004604054630000003b5860056060541ca0ddc901d83110ea50bc40803f42083afea1bbd420548f6392a679af8e24b21345a06620b3b512bea5f0a272703e8d6933177c23afc79516fd0ca4a204aa6e34c7e9";

        byte[] senderPrivKey = HashUtil.keccak256("cow".getBytes());

        byte[] nonce = BigIntegers.asUnsignedByteArray(BigInteger.ZERO);
        byte[] gasPrice = Hex.decode("09184e72a000");       // 10000000000000
        byte[] gas = Hex.decode("03e8");           // 1000
        byte[] recieveAddress = null;
        byte[] endowment = Hex.decode("03e8"); //10000000000000000"
        byte[] init = Hex.decode
                ("4560005444602054600f60056002600a02010b0d630000001d596002602054630000003b5860066000530860056006600202010a0d6300000036596004604054630000003b586005606054");


        Transaction tx1 = Transaction.builder()
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(gas)
                .destination(recieveAddress)
                .value(endowment)
                .build();
        tx1.sign(senderPrivKey);

        byte[] payload = tx1.getEncoded();


        System.out.println(ByteUtil.toHexString(payload));
        Transaction tx2 = new ImmutableTransaction(payload);
//        tx2.getSender();

        String plainTx1 = ByteUtil.toHexString(tx1.getEncodedRaw());
        String plainTx2 = ByteUtil.toHexString(tx2.getEncodedRaw());

//        Transaction tx = new Transaction(Hex.decode(rlp));

        System.out.println("tx1.hash: " + tx1.getHash());
        System.out.println("tx2.hash: " + tx2.getHash());
        System.out.println();
        System.out.println("plainTx1: " + plainTx1);
        System.out.println("plainTx2: " + plainTx2);

        Assertions.assertNotNull(tx2.getSender());
        System.out.println(tx2.getSender().toString());
    }


    @Disabled
    @Test
    void encodeReceiptTest() {

        String data = "f90244a0f5ff3fbd159773816a7c707a9b8cb6bb778b934a8f6466c7830ed970498f4b688301e848b902000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000dbda94cd2a3d9f938e13cd947ec05abc7fe734df8dd826c083a1a1a1";

        byte[] stateRoot = Hex.decode("f5ff3fbd159773816a7c707a9b8cb6bb778b934a8f6466c7830ed970498f4b68");
        byte[] gasUsed = Hex.decode("01E848");
        Bloom bloom = new Bloom(Hex.decode("0000000000000000800000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));

        LogInfo logInfo1 = new LogInfo(
                Hex.decode("cd2a3d9f938e13cd947ec05abc7fe734df8dd826"),
                null,
                Hex.decode("a1a1a1")
        );

        List<LogInfo> logs = new ArrayList<>();
        logs.add(logInfo1);

        // TODO calculate cumulative gas
        TransactionReceipt receipt = new TransactionReceipt(stateRoot, gasUsed, gasUsed, bloom, logs, TransactionReceipt.SUCCESS_STATUS);

        assertEquals(data,
                ByteUtil.toHexString(receipt.getEncoded()));
    }

    @Test
    void constantCallConflictTest() throws Exception {
        /*
          0x095e7baea6a6c7c4c2dfeb977efac326af552d87 contract is the following Solidity code:

         contract Test {
            uint a = 256;

            function set(uint s) {
                a = s;
            }

            function get() returns (uint) {
                return a;
            }
        }
        */
        String json = "{ " +
                "    'test1' : { " +
                "        'env' : { " +
                "            'currentCoinbase' : '2adc25665018aa1fe0e6bc666dac8fc2697ff9ba', " +
                "            'currentDifficulty' : '0x0100', " +
                "            'currentGasLimit' : '0x0f4240', " +
                "            'currentNumber' : '0x00', " +
                "            'currentTimestamp' : '0x01', " +
                "            'previousHash' : '5e20a0453cecd065ea59c37ac63e079ee08998b6045136a8ce6635c7912ec0b6' " +
                "        }, " +
                "        'logs' : [ " +
                "        ], " +
                "        'out' : '0x', " +
                "        'post' : { " +
                "            '095e7baea6a6c7c4c2dfeb977efac326af552d87' : { " +
                "                'balance' : '0x0de0b6b3a76586a0', " +
                "                'code' : '0x606060405260e060020a600035046360fe47b1811460245780636d4ce63c14602e575b005b6004356000556022565b6000546060908152602090f3', " +
                "                'nonce' : '0x00', " +
                "                'storage' : { " +
                "                    '0x00' : '0x0400' " +
                "                } " +
                "            }, " +
                "            '0000000000000000000000000000000001000008' : { " +
                "                'balance' : '0x67EB', " +
                "                'code' : '0x', " +
                "                'nonce' : '0x00', " +
                "                'storage' : { " +
                "                } " +
                "            }, " +
                "            'a94f5374fce5edbc8e2a8697c15331677e6ebf0b' : { " +
                "                'balance' : '0x0DE0B6B3A7621175', " +
                "                'code' : '0x', " +
                "                'nonce' : '0x01', " +
                "                'storage' : { " +
                "                } " +
                "            } " +
                "        }, " +
                "        'postStateRoot' : '17454a767e5f04461256f3812ffca930443c04a47d05ce3f38940c4a14b8c479', " +
                "        'pre' : { " +
                "            '095e7baea6a6c7c4c2dfeb977efac326af552d87' : { " +
                "                'balance' : '0x0de0b6b3a7640000', " +
                "                'code' : '0x606060405260e060020a600035046360fe47b1811460245780636d4ce63c14602e575b005b6004356000556022565b6000546060908152602090f3', " +
                "                'nonce' : '0x00', " +
                "                'storage' : { " +
                "                    '0x00' : '0x02' " +
                "                } " +
                "            }, " +
                "            'a94f5374fce5edbc8e2a8697c15331677e6ebf0b' : { " +
                "                'balance' : '0x0de0b6b3a7640000', " +
                "                'code' : '0x', " +
                "                'nonce' : '0x00', " +
                "                'storage' : { " +
                "                } " +
                "            } " +
                "        }, " +
                "        'transaction' : { " +
                "            'data' : '0x60fe47b10000000000000000000000000000000000000000000000000000000000000400', " +
                "            'gasLimit' : '0x061a80', " +
                "            'gasPrice' : '0x01', " +
                "            'nonce' : '0x00', " +
                "            'secretKey' : '45a915e4d060149eb4365960e6a7a45f334393093061116b197e3240065ff2d8', " +
                "            'to' : '095e7baea6a6c7c4c2dfeb977efac326af552d87', " +
                "            'value' : '0x0186a0' " +
                "        } " +
                "    } " +
                "}";

        StateTestSuite stateTestSuite = new StateTestSuite(json.replaceAll("'", "\""));

        List<String> res = new StateTestRunner(stateTestSuite.getTestCases().get("test1")) {
            @Override
            protected ProgramResult executeTransaction(Transaction tx) {
                // first emulating the constant call (Ethereum.callConstantFunction)
                // to ensure it doesn't affect the final state

                {
                    Repository track = repository.startTracking();

                    Transaction txConst = CallTransaction.createCallTransaction(0, 0, 100000000000000L,
                            new RskAddress("095e7baea6a6c7c4c2dfeb977efac326af552d87"), 0, CallTransaction.Function.fromSignature("get"), config.getNetworkConstants().getChainId());
                    txConst.sign(new byte[]{});

                    Block bestBlock = block;

                    BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                            new RepositoryBtcBlockStoreWithCache.Factory(
                                    config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                            config.getNetworkConstants().getBridgeConstants(),
                            config.getActivationConfig());

                    TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                            config,
                            new BlockStoreDummy(),
                            null,
                            blockFactory,
                            invokeFactory,
                            new PrecompiledContracts(config, bridgeSupportFactory),
                            new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
                    TransactionExecutor executor = transactionExecutorFactory
                            .newInstance(txConst, 0, bestBlock.getCoinbase(), track, bestBlock, 0)
                            .setLocalCall(true);

                    executor.executeTransaction();

                    track.rollback();

                    System.out.println("Return value: " + new CallTransaction.IntType("uint").decode(executor.getResult().getHReturn()));
                }

                // now executing the JSON test transaction
                return super.executeTransaction(tx);
            }
        }.setstateTestUSeREMASC(true).runImpl();
        Assertions.assertTrue(res.isEmpty(), res.toString());
    }

    @Disabled // This test fails post EIP150
    @Test
    void contractCreationTest() throws Exception {
        // Checks Homestead updates (1) & (3) from
        // https://github.com/ethereum/EIPs/blob/master/EIPS/eip-2.mediawiki

        /*
          trying to create a contract with the following Solidity code:

         contract Test {
            uint a = 256;

            function set(uint s) {
                a = s;
            }

            function get() returns (uint) {
                return a;
            }
         }
        */

        int iBitLowGas = 0x015f84;  // [actual gas required] - 1
        String aBitLowGas = "0x0" + Integer.toHexString(iBitLowGas);
        String senderPostBalance = "0x0" + Long.toHexString(1000000000000000000L - iBitLowGas);

        String json = "{ " +
                "    'test1' : { " +
                "        'env' : { " +
                "            'currentCoinbase' : '2adc25665018aa1fe0e6bc666dac8fc2697ff9ba', " +
                "            'currentDifficulty' : '0x0100', " +
                "            'currentGasLimit' : '0x0f4240', " +
                "            'currentNumber' : '0x01', " +
                "            'currentTimestamp' : '0x01', " +
                "            'previousHash' : '5e20a0453cecd065ea59c37ac63e079ee08998b6045136a8ce6635c7912ec0b6' " +
                "        }, " +
                "        'logs' : [ " +
                "        ], " +
                "        'out' : '0x', " +
                "        'post' : { " +
                "            '2adc25665018aa1fe0e6bc666dac8fc2697ff9ba' : { " +
                "                'balance' : '" + aBitLowGas + "', " +
                "                'code' : '0x', " +
                "                'nonce' : '0x00', " +
                "                'storage' : { " +
                "                } " +
                "            }," +
                "            'a94f5374fce5edbc8e2a8697c15331677e6ebf0b' : { " +
                "                'balance' : '" + senderPostBalance + "', " +
                "                'code' : '0x', " +
                "                'nonce' : '0x01', " +
                "                'storage' : { " +
                "                } " +
                "            } " +
                "        }, " +
                "        'postStateRoot' : '17454a767e5f04461256f3812ffca930443c04a47d05ce3f38940c4a14b8c479', " +
                "        'pre' : { " +
                "            'a94f5374fce5edbc8e2a8697c15331677e6ebf0b' : { " +
                "                'balance' : '0x0de0b6b3a7640000', " +
                "                'code' : '0x', " +
                "                'nonce' : '0x00', " +
                "                'storage' : { " +
                "                } " +
                "            } " +
                "        }, " +
                "        'transaction' : { " +
                "            'data' : '0x6060604052610100600060005055603b8060196000396000f3606060405260e060020a600035046360fe47b1811460245780636d4ce63c14602e575b005b6004356000556022565b6000546060908152602090f3', " +
                "            'gasLimit' : '" + aBitLowGas + "', " +
                "            'gasPrice' : '0x01', " +
                "            'nonce' : '0x00', " +
                "            'secretKey' : '45a915e4d060149eb4365960e6a7a45f334393093061116b197e3240065ff2d8', " +
                "            'to' : '', " +
                "            'value' : '0x0' " +
                "        } " +
                "    } " +
                "}";

        StateTestSuite stateTestSuite = new StateTestSuite(json.replaceAll("'", "\""));

        System.out.println(json.replaceAll("'", "\""));

        List<String> res = new StateTestRunner(stateTestSuite.getTestCases().get("test1")).runImpl();
        Assertions.assertTrue(res.isEmpty(), res.toString());
    }

    @Test
    void multiSuicideTest() throws InterruptedException {
        /*
        Original contract

        pragma solidity ^0.4.3;

        contract PsychoKiller {
            function () payable {}

            function homicide() {
                suicide(msg.sender);
            }

            function multipleHomicide() {
                PsychoKiller k  = this;
                k.homicide();
                k.homicide();
                k.homicide();
                k.homicide();
            }
        }

         */

        BigInteger nonce = config.getNetworkConstants().getInitialNonce();
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableRepository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
        IndexedBlockStore blockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        Blockchain blockchain = ImportLightTest.createBlockchain(
                new TestGenesisLoader(
                        trieStore, getClass().getResourceAsStream("/genesis/genesis-light.json"), nonce,
                        false, true, true
                ).load(),
                config, repository, blockStore, trieStore
        );

        ECKey sender = ECKey.fromPrivate(Hex.decode("3ec771c31cac8c0dba77a69e503765701d3c2bb62435888d4ffa38fed60c445c"));
        System.out.println("address: " + ByteUtil.toHexString(sender.getAddress()));

        String code = "6060604052341561000c57fe5b5b6102938061001c6000396000f3006060604052361561004a576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff16806309e587a514610053578063de990da914610065575b6100515b5b565b005b341561005b57fe5b610063610077565b005b341561006d57fe5b610075610092565b005b3373ffffffffffffffffffffffffffffffffffffffff16ff5b565b60003090508073ffffffffffffffffffffffffffffffffffffffff166309e587a56040518163ffffffff167c0100000000000000000000000000000000000000000000000000000000028152600401809050600060405180830381600087803b15156100fa57fe5b60325a03f1151561010757fe5b5050508073ffffffffffffffffffffffffffffffffffffffff166309e587a56040518163ffffffff167c0100000000000000000000000000000000000000000000000000000000028152600401809050600060405180830381600087803b151561016d57fe5b60325a03f1151561017a57fe5b5050508073ffffffffffffffffffffffffffffffffffffffff166309e587a56040518163ffffffff167c0100000000000000000000000000000000000000000000000000000000028152600401809050600060405180830381600087803b15156101e057fe5b60325a03f115156101ed57fe5b5050508073ffffffffffffffffffffffffffffffffffffffff166309e587a56040518163ffffffff167c0100000000000000000000000000000000000000000000000000000000028152600401809050600060405180830381600087803b151561025357fe5b60325a03f1151561026057fe5b5050505b505600a165627a7a72305820084e74021c556522723b6725354378df2fb4b6732f82dd33f5daa29e2820b37c0029";
        String abi = "[{\"constant\":false,\"inputs\":[],\"name\":\"homicide\",\"outputs\":[],\"payable\":false,\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"multipleHomicide\",\"outputs\":[],\"payable\":false,\"type\":\"function\"},{\"payable\":true,\"type\":\"fallback\"}]";

        Transaction tx = createTx(sender, new byte[0], Hex.decode(code), repository);
        executeTransaction(blockchain, blockStore, tx, repository, blockTxSignatureCache);

        byte[] contractAddress = tx.getContractAddress().getBytes();

        CallTransaction.Contract contract1 = new CallTransaction.Contract(abi);
        byte[] callData = contract1.getByName("multipleHomicide").encode();

        Assertions.assertNull(contract1.getConstructor());
        Assertions.assertNotNull(contract1.parseInvocation(callData));
        Assertions.assertNotNull(contract1.parseInvocation(callData).toString());

        try {
            contract1.parseInvocation(new byte[32]);
            Assertions.fail();
        }
        catch (RuntimeException ex) {
        }

        try {
            contract1.parseInvocation(new byte[2]);
            Assertions.fail();
        }
        catch (RuntimeException ex) {
        }

        Transaction tx1 = createTx(sender, contractAddress, callData, repository);
        ProgramResult programResult = executeTransaction(blockchain, blockStore, tx1, repository, blockTxSignatureCache).getResult();

        // suicide of a single account should be counted only once
        Assertions.assertEquals(24000, programResult.getFutureRefund());
    }

    @Test
    void dontLogWhenReverting() throws InterruptedException {
        /*

        Original contracts

        pragma solidity ^0.4.0;
        contract TestEventInvoked {
            event internalEvent();

            function doIt() {
                internalEvent();
                throw;
            }
        }

        contract TestEventInvoker {
            event externalEvent();

            function doIt(address invokedAddress) {
                externalEvent();
                invokedAddress.call.gas(50000)(0xb29f0835);
            }
        }

         */

        BigInteger nonce = config.getNetworkConstants().getInitialNonce();
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableRepository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
        IndexedBlockStore blockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        Blockchain blockchain = ImportLightTest.createBlockchain(
                new TestGenesisLoader(
                        trieStore, getClass().getResourceAsStream("/genesis/genesis-light.json"), nonce,
                        false, true, true
                ).load(),
                config, repository, blockStore, trieStore
        );

        ECKey sender = ECKey.fromPrivate(Hex.decode("3ec771c31cac8c0dba77a69e503765701d3c2bb62435888d4ffa38fed60c445c"));
        System.out.println("address: " + ByteUtil.toHexString(sender.getAddress()));

        // First contract code TestEventInvoked
        String code1 = "6060604052341561000f57600080fd5b5b60ae8061001e6000396000f30060606040526000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063b29f083514603d575b600080fd5b3415604757600080fd5b604d604f565b005b7f95481a538d62f8458d3cecac82408d5ff2630d8335962b1cdbac16f1a9b910e760405160405180910390a1600080fd5b5600a165627a7a723058207d93861daff7f4a0479d7f3eb0ca7ef5cef7e2bbf2c4637ab4f021ecc5afa7ad0029";
        // Second contract code TestEventInvoker
        String code2 = "6060604052341561000f57600080fd5b5b6101358061001f6000396000f30060606040526000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063e25fd8a71461003e575b600080fd5b341561004957600080fd5b610075600480803573ffffffffffffffffffffffffffffffffffffffff16906020019091905050610077565b005b7f4cd6f2e769273405c20f3a0c098c9045749deec145502c4838b54206ec5c542860405160405180910390a18073ffffffffffffffffffffffffffffffffffffffff1661c35063b29f0835906040518263ffffffff167c010000000000000000000000000000000000000000000000000000000002815260040160006040518083038160008887f19350505050505b505600a165627a7a7230582019096fd773ebc5581ba378acd64cb1acb450b4eb4866d710f3e3f4e33d635a4b0029";
        // Second contract ABI
        String abi2 = "[{\"constant\":false,\"inputs\":[{\"name\":\"invokedAddress\",\"type\":\"address\"}],\"name\":\"doIt\",\"outputs\":[],\"payable\":false,\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[],\"name\":\"externalEvent\",\"type\":\"event\"}]";

        Transaction tx1 = createTx(sender, new byte[0], Hex.decode(code1), repository);
        executeTransaction(blockchain, blockStore, tx1, repository, blockTxSignatureCache);

        Transaction tx2 = createTx(sender, new byte[0], Hex.decode(code2), repository);
        executeTransaction(blockchain, blockStore, tx2, repository, blockTxSignatureCache);

        CallTransaction.Contract contract2 = new CallTransaction.Contract(abi2);
        byte[] data = contract2.getByName("doIt").encode(ByteUtil.toHexString(tx1.getContractAddress().getBytes()));

        Transaction tx3 = createTx(sender, tx2.getContractAddress().getBytes(), data, repository);
        TransactionExecutor executor = executeTransaction(blockchain, blockStore, tx3, repository, blockTxSignatureCache);
        Assertions.assertEquals(1, executor.getResult().getLogInfoList().size());
        Assertions.assertFalse(executor.getResult().getLogInfoList().get(0).isRejected());
        Assertions.assertEquals(1, executor.getVMLogs().size());
    }

    @Test
    void verifyTx_noSignature() {
        BigInteger value = new BigInteger("1000000000000000000000");
        byte[] privKey = HashUtil.keccak256("cat".getBytes());
        ECKey ecKey = ECKey.fromPrivate(privKey);
        byte[] gasPrice = Hex.decode("09184e72a000");
        byte[] gas = Hex.decode("4255");

        Transaction tx = Transaction.builder()
                .gasPrice(gasPrice)
                .gasLimit(gas)
                .destination(ecKey.getAddress())
                .value(value)
                .build();
        try {
            tx.verify();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void verifyTx_withSignature() {
        BigInteger value = new BigInteger("1000000000000000000000");
        byte[] senderPrivKey = HashUtil.keccak256("cow".getBytes());
        byte[] privKey = HashUtil.keccak256("cat".getBytes());
        ECKey ecKey = ECKey.fromPrivate(privKey);
        byte[] gasPrice = Hex.decode("09184e72a000");
        byte[] gas = Hex.decode("4255");

        Transaction tx = Transaction.builder()
                .gasPrice(gasPrice)
                .gasLimit(gas)
                .destination(ecKey.getAddress())
                .value(value)
                .build();
        tx.sign(senderPrivKey);
        try {
            tx.verify();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void toString_nullElements() {
        byte[] encodedNull = RLP.encodeElement(null);
        byte[] encodedEmptyArray = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[] rawData = RLP.encodeList(encodedNull, encodedNull, encodedNull, encodedNull, encodedNull, encodedNull,
                encodedEmptyArray, encodedEmptyArray, encodedEmptyArray);
        Transaction tx = new ImmutableTransaction(rawData);
        try {
            tx.toString();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private Transaction createTx(ECKey sender, byte[] receiveAddress, byte[] data, final Repository repository) throws InterruptedException {
        return createTx(sender, receiveAddress, data, 0, repository.getNonce(new RskAddress(sender.getAddress())));
    }

    private Transaction createTx(ECKey sender, byte[] receiveAddress,
                                 byte[] data, long value, BigInteger nonce) {
        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(ByteUtil.longToBytesNoLeadZeroes(1))
                .gasLimit(ByteUtil.longToBytesNoLeadZeroes(3_000_000))
                .destination(receiveAddress)
                .value(ByteUtil.longToBytesNoLeadZeroes(value))
                .data(data)
                .build();
        tx.sign(sender.getPrivKeyBytes());
        return tx;
    }

    private TransactionExecutor executeTransaction(
            Blockchain blockchain,
            BlockStore blockStore,
            Transaction tx,
            Repository repository,
            BlockTxSignatureCache blockTxSignatureCache) {
        Repository track = repository.startTracking();
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(
                        config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                config.getNetworkConstants().getBridgeConstants(),
                config.getActivationConfig());

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                config,
                blockStore,
                null,
                blockFactory,
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory),
                blockTxSignatureCache);
        TransactionExecutor executor = transactionExecutorFactory
                .newInstance(tx, 0, RskAddress.nullAddress(), repository, blockchain.getBestBlock(), 0);

        executor.executeTransaction();

        track.commit();
        return executor;
    }

}
