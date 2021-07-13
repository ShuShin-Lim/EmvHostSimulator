package com.softspace.payment.hsm.ksn;

import com.softspace.payment.hsm.util.Utils;

/**
 *  Class to manipulate the KSN Register
 *  
 */
public class KSNRegister {
	private byte[] reg;

	public KSNRegister(String ksn) {
		reg = new byte[10];
		setRegister(ksn);
	}

	/**
	 * Setter for KSN
	 * 
	 * @param ksn
	 * 			{@link String} Key Serial Number
	 */
	public void setRegister(String ksn) {
		if (ksn.length() != 20)
			throw new IllegalArgumentException(
					"KSNRegister::setRegister(String ksn) expects 20-char string argument");
		byte[] tmpBA = Utils.HexStringToByteArray(ksn);
		System.arraycopy(tmpBA, 0, reg, 0, 10);
	}

	/** 
	 * Getter for KSN
	 * 
	 * @return {@link String} Key Serial Number
	 */
	public String getRegister() {
		return Utils.ByteArrayToHexString(reg);
	}

	/**
	 * Extracts the encryption counter value from the 21-bit trailing bits of KSN
	 * 
	 * @return {@link Int} Encryption Counter
	 */
	public int getCounter() {
		byte[] b = new byte[3];
		int MASK = 0xFF;
		int result = 0;

		// store into a byte array
		b[2] = (byte) (reg[7] & 0x1F);
		b[1] = (byte) (reg[8]);
		b[0] = (byte) (reg[9]);

		// compute result
		result = b[0] & MASK;
		result = result + ((b[1] & MASK) << 8);
		result = result + ((b[2] & MASK) << 16);

		return result;
	}

	/**
	 * Set the 21-bit encryption counter portion of the KSNRegister to input argument
	 * @param encCntr {@link Int} Encryption Counter
	 */
	public void setCounter(int encCntr) {
		byte encCtnr_bin[] = new byte[4];

		// convert the integer encCtnr into the 4-byte encCtnr_bin byte array
		for (int i = 0; i < 4; i++) {
			encCtnr_bin[3 - i] = (byte) ((encCntr >>> (8 * i)) & 0xFF);
		}

		// for encCtnr, mask out all but the last 21-bits
		encCtnr_bin[0] = (byte) (encCtnr_bin[0] & 0x00);
		encCtnr_bin[1] = (byte) (encCtnr_bin[1] & 0x1F);
		encCtnr_bin[2] = (byte) (encCtnr_bin[2] & 0xFF);
		encCtnr_bin[3] = (byte) (encCtnr_bin[3] & 0xFF);

		// for KSN, mask in all but the last 21-bits
		reg[7] = (byte) (reg[7] & 0xE0);
		reg[8] = (byte) (reg[8] & 0x00);
		reg[9] = (byte) (reg[9] & 0x00);

		// concat the KSN & Encryption Counter into 10-byte KsnReg
		for (int i = 6; i < 10; i++) {
			reg[i] = (byte) (reg[i] | encCtnr_bin[i - 6]);
		}
	}

}
