package com.softspace.payment.hsm.service;

import java.util.HashMap;

public interface HSMService {

	/**
	 * Get IPEK
	 * 
	 * @param ksn
	 * @return return a hashMap of two values with the following keys: kek &amp; ipek
	 * @throws Exception
	 */
	public abstract HashMap<String, Object> getIPEK(String ksn) throws Exception;

	/**
	 * Encrypt value
	 * 
	 * @param KSN
	 * @param plainText
	 * @throws Exception
	 */
	public abstract String encrypt(String KSN, byte[] plainText) throws Exception;

	/**
	 * Decrypt values
	 * 
	 * @param KSN
	 * @param encryptedText
	 * @return Decrypted String
	 * @throws Exception
	 */
	public abstract String decrypt(String KSN, String encryptedText) throws Exception;

	/**
	 * Encrypt value
	 * 
	 * @param plainTextData
	 * @param encryptedWorkingKey
	 * @throws Exception
	 */
	public abstract String encryptAES(byte[] plainTextData, byte[] encryptedWorkingKey, byte[] initialVector) throws Exception;

	/**
	 * Decrypt values
	 * 
	 * @param cipherTextData
	 * @param encryptedWorkingKey
	 * @return Decrypted String
	 * @throws Exception
	 */
	public abstract String decryptAES(byte[] cipherTextData, byte[] encryptedWorkingKey, byte[] initialVector) throws Exception;

}
