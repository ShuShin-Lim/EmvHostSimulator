package com.softspace.payment.hsm.service.impl;

import java.util.Arrays;
import java.util.HashMap;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.softspace.payment.hsm.common.Constants;
import com.softspace.payment.hsm.ksn.KSNRegister;
import com.softspace.payment.hsm.service.HSMService;
import com.softspace.payment.hsm.util.CryptoUtil;
import com.softspace.payment.hsm.util.Utils;

@Service("hsmService")
public class HSMServiceImpl implements HSMService {

	private static final Logger logger = LoggerFactory.getLogger(HSMServiceImpl.class);
	private byte[] m_baKeyBDK;
	private static final String INITKEY_CONSTANT = "C0C0C0C000000000C0C0C0C000000000";
	private static final byte[] ZERO_ARRAY = new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00 };

	@Value("${hsm.bdk}")
	private String BDK;

	@Value("${hsm.kek}")
	private String KEK;

	@PostConstruct
	public void init() {
		setBDK(BDK);
	}

	// private constructor
	private HSMServiceImpl() {

	}

	/**
	 * Set value of BDK
	 * 
	 * @param strHexBDK hex string representation of 16-byte BDK
	 */
	public void setBDK(String strHexBDK) {
		// check strHexBDK
		if (strHexBDK == null)
			throw new IllegalArgumentException("BDK argument can't be null");
		if (strHexBDK.length() != 32 || (!strHexBDK.matches("^[0-9A-Fa-f]+$")))
			throw new IllegalArgumentException(String.format("BDK must be 32 characters in length, with each char in [0-9A-F]. Given:[%s]",
					strHexBDK));

		// Input Base Derivation Key is double length, ie 16-bytes (128-bits) long.
		// DESede requires triple length key. baKeyBDK[1]=strKeyBDK[1], baKeyBDK[2]=strKeyBDK[2], baKeyBDK[3]=strKeyBDK[1]
		if (m_baKeyBDK == null)
			m_baKeyBDK = new byte[24]; // 192-bit key = K1, K2, K3
		byte ba[] = Utils.HexStringToByteArray(strHexBDK); // convert hex string to 16-byte byte array
		System.arraycopy(ba, 0, m_baKeyBDK, 0, 16); // ba(K1,K2) -> m_baKeyBDK(K1,K2)
		System.arraycopy(ba, 0, m_baKeyBDK, 16, 8); // ba(K1) -> m_baKeyBDK(K3)
	}

	public HashMap<String, Object> getIPEK(String ksn) throws Exception {
		byte[] baBDKMasked = new byte[24];
		byte[] baKSNMasked = new byte[8];
		SecretKey secretKey;

		if (ksn.length() != 20)
			throw new IllegalArgumentException("getIPEK> ERROR: Input Argument String ksn must be 20-char in length");

		if (logger.isDebugEnabled())
			logger.info(String.format("getIPEK> KSN       =[%s]\n", ksn));
		if (logger.isDebugEnabled())
			logger.info(String.format("getIPEK> BDK       =[%s]\n", Utils.ByteArrayToHexString(m_baKeyBDK)));

		// Get BDK mask as a byte array
		byte[] baConstant = new byte[24];
		byte[] mask = Utils.HexStringToByteArray(INITKEY_CONSTANT);
		System.arraycopy(mask, 0, baConstant, 0, 16);
		System.arraycopy(mask, 0, baConstant, 16, 8);
		// BDKMasked = XOR 16-byte CONSTANT with 16-byte BDK
		for (int i = 0; i < baConstant.length; i++)
			baBDKMasked[i] = (byte) (m_baKeyBDK[i] ^ baConstant[i]);
		if (logger.isDebugEnabled())
			logger.info(String.format("getIPEK> baBDKMasked =[%s]\n", Utils.ByteArrayToHexString(baBDKMasked)));

		// Extract key serial number from ksn (ie: mask out rightmost 21-bit encryption counter)
		byte[] baKSN = Utils.HexStringToByteArray(ksn);
		System.arraycopy(baKSN, 0, baKSNMasked, 0, 7);
		baKSNMasked[7] = (byte) (baKSN[7] & 0xE0); // Last byte is masked with 0xE0 = 1110 0000
		if (logger.isDebugEnabled())
			logger.info(String.format("getIPEK> baKSNMasked =[%s]\n", Utils.ByteArrayToHexString(baKSNMasked)));

		// Cryptographic objects
		SecretKeyFactory tmp_skf = SecretKeyFactory.getInstance("DESede"); // TDEA
		Cipher tmp_cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding"); // TDEA in ECB mode, using PKCS5 padding

		// Generate 8-byte left-side of IPEK. Left-IPEK := Encrypt3DES(Key:=BDK,Data:=KSN.MSB[8])
		DESedeKeySpec ksBDK1 = new DESedeKeySpec(m_baKeyBDK);
		secretKey = tmp_skf.generateSecret(ksBDK1);
		tmp_cipher.init(Cipher.ENCRYPT_MODE, secretKey);
		byte[] baKeyL = tmp_cipher.doFinal(baKSNMasked);
		if (logger.isDebugEnabled())
			logger.info(String.format("getIPEK> baKeyL      =[%s]\n", Utils.ByteArrayToHexString(baKeyL)));

		// Generate 8-byte right-side of IPEK. Right-IPEK := Encrypt3DES(Key:=XOR(BDK,CONSTANT), Data:=KSN.MSB[8])
		DESedeKeySpec ksBDK2 = new DESedeKeySpec(baBDKMasked);
		secretKey = tmp_skf.generateSecret(ksBDK2);
		tmp_cipher.init(Cipher.ENCRYPT_MODE, secretKey);
		byte[] baKeyR = tmp_cipher.doFinal(baKSNMasked);
		if (logger.isDebugEnabled())
			logger.info(String.format("getIPEK> baKeyR      =[%s]\n", Utils.ByteArrayToHexString(baKeyR)));

		// Concatenate the two 8-byte half to form the full 16-byte IK
		byte[] baKey = new byte[16];
		for (int i = 0; i < 8; i++) {
			baKey[i] = baKeyL[i];
			baKey[i + 8] = baKeyR[i];
		}

		String strHexIPEK = Utils.ByteArrayToHexString(baKey);
		if (logger.isDebugEnabled())
			logger.info(String.format("getIPEK> IPEK        =[%s]\n", strHexIPEK));

		HashMap<String, Object> hm = new HashMap<String, Object>();
		hm.put(Constants.PARAM_IPEK, strHexIPEK);
		hm.put(Constants.PARAM_KEK, KEK);
		return hm;
	}

	public String encrypt(String ksn, byte[] plainTextByte) throws Exception {
		String strCipherText = null;
		byte[] txnKey; // double length key
		byte[] enc3Key = new byte[24]; // triple length key

		if (logger.isDebugEnabled())
			logger.debug(String.format("encrypt> IN KSN=[%s] plainText=[%s]\n", ksn, Utils.ByteArrayToHexString(plainTextByte)));

		// get txn key
		txnKey = hostGetTxnKey(ksn);
		if (logger.isDebugEnabled())
			logger.debug(String.format("encrypt> txnKey=[%s]\n", Utils.ByteArrayToHexString(txnKey)));

		// K1,K2,K1
		System.arraycopy(txnKey, 0, enc3Key, 0, 16);
		System.arraycopy(txnKey, 0, enc3Key, 16, 8);

		// An IV of 0x00
		byte[] baIV = new byte[8];
		System.arraycopy(ZERO_ARRAY, 0, baIV, 0, 8);
		IvParameterSpec iv = new IvParameterSpec(baIV);

		strCipherText = Utils.ByteArrayToHexString(CryptoUtil.encrypt3DESCBC(enc3Key, iv.getIV(), plainTextByte));
		if (logger.isDebugEnabled())
			logger.debug(String.format("encrypt> cipherText=[%s]\n", strCipherText));

		return strCipherText;
	}

	public String decrypt(String ksn, String encryptedText) throws Exception {
		String strPlainText = null;
		byte[] txnKey;
		byte[] enc3Key = new byte[24];

		if (logger.isDebugEnabled())
			logger.debug(String.format("decrypt> IN KSN=[%s] encryptedText=[%s]\n", ksn, encryptedText));

		// get txn key
		txnKey = hostGetTxnKey(ksn);
		if (logger.isDebugEnabled())
			logger.debug(String.format("decrypt> txnKey=[%s]\n", Utils.ByteArrayToHexString(txnKey)));

		// K1,K2,K1
		System.arraycopy(txnKey, 0, enc3Key, 0, 16);
		System.arraycopy(txnKey, 0, enc3Key, 16, 8);

		// An IV of 0x00
		byte[] baIV = new byte[8];
		System.arraycopy(ZERO_ARRAY, 0, baIV, 0, 8);
		IvParameterSpec iv = new IvParameterSpec(baIV);

		// decrypt
		strPlainText = Utils
				.ByteArrayToHexString(CryptoUtil.decrypt3DESCBC(enc3Key, iv.getIV(), Utils.HexStringToByteArray(encryptedText)));
		if (logger.isDebugEnabled())
			logger.debug(String.format("decrypt> planiText=[%s]\n", strPlainText));

		return strPlainText;
	}

	private byte[] hostGetTxnKey(String txnKSN) throws Exception {
		int orig_encrypt_ctr;

		if (logger.isDebugEnabled())
			logger.debug(String.format("hostGetTxnKey> txnKSN    =[%s]\n", txnKSN));

		// convert txnKSN to byte array
		KSNRegister ksnReg = new KSNRegister(txnKSN);

		// Extract encryption counter from txnKSN
		orig_encrypt_ctr = ksnReg.getCounter();
		if (logger.isDebugEnabled())
			logger.debug(String.format("hostGetTxnKey> orig_encrypt_ctr=[%s] (%d)\n", Utils.IntToBinaryString(orig_encrypt_ctr),
					orig_encrypt_ctr));

		// create initial KSN for device, which incorporates EC=0
		ksnReg.setCounter(0);
		if (logger.isDebugEnabled())
			logger.debug(String.format("hostGetTxnKey> initialKSN=[%s]\n", ksnReg.getRegister()));

		// get IPEK
		HashMap<String, Object> hm = this.getIPEK(ksnReg.getRegister());
		String strHexIPEK = hm.get("ipek").toString();
		if (logger.isDebugEnabled())
			logger.debug(String.format("hostGetTxnKey> ipek=[%s]\n", strHexIPEK));

		// generate txnKey
		byte[] txnKey = CryptoUtil.calculateDataKey(Utils.HexStringToByteArray(txnKSN), Utils.HexStringToByteArray(strHexIPEK));
		if (logger.isDebugEnabled())
			logger.debug(String.format("hostGetTxnKey> txnKey=[%s]\n", Utils.ByteArrayToHexString(txnKey)));

		return txnKey;
	}

	public String encryptAES(byte[] plainTextData, byte[] encryptedWorkingKey, byte[] initialVector) throws Exception {
		// massage key to 16bytes
		if (encryptedWorkingKey != null && encryptedWorkingKey.length != 16) {
			encryptedWorkingKey = Arrays.copyOf(encryptedWorkingKey, 16);
		}
		String plainTextStr = Utils.bytesToHexString(plainTextData);

		int padding = plainTextStr.length() % 32;
		if (padding != 0) {
			plainTextStr = String.format("%-" + (plainTextStr.length() + 32 - padding) + "s", plainTextStr).replace(' ', '0');
		}

		plainTextData = Utils.hexStringToByteArray(plainTextStr);

		String cipherTextData = null;

		cipherTextData = Utils.ByteArrayToHexString(CryptoUtil.encryptAESCBC(encryptedWorkingKey, initialVector, plainTextData));

		if (logger.isDebugEnabled()) {
			logger.debug(String.format("encryptAES> cipherTextData=[%s]\n", cipherTextData));
		}

		return cipherTextData;
	}

	public String decryptAES(byte[] cipherTextData, byte[] encryptedWorkingKey, byte[] initialVector) throws Exception {
		// massage key to 16bytes
		if (encryptedWorkingKey != null && encryptedWorkingKey.length != 16) {
			encryptedWorkingKey = Arrays.copyOf(encryptedWorkingKey, 16);
		}

		String cipherTextStr = Utils.bytesToHexString(cipherTextData);

		int padding = cipherTextStr.length() % 32;
		if (padding != 0) {
			cipherTextStr = String.format("%-" + (cipherTextStr.length() + 32 - padding) + "s", cipherTextStr).replace(' ', '0');
		}

		cipherTextData = Utils.hexStringToByteArray(cipherTextStr);

		String plainTextData = null;

		plainTextData = Utils.ByteArrayToHexString(CryptoUtil.decryptAESCBC(encryptedWorkingKey, initialVector, cipherTextData));

		if (logger.isDebugEnabled()) {
			logger.debug(String.format("decryptAES> plainTextData=[%s]\n", plainTextData));
		}

		return plainTextData;
	}
}
