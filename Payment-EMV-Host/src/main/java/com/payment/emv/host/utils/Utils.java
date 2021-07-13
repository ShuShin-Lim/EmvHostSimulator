package com.payment.emv.host.utils;

import java.nio.charset.StandardCharsets;

public class Utils {

	public static String bytesToHexString(final byte[] bytes) {
		final char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[(j * 2) + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static byte[] hexStringToByteArray(final String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	/**
	 * Convert byte array to its binary string representation
	 * 
	 * @param ba byte array
	 * @return binary string representation
	 */
	public static String byteArrayToBinaryString(byte[] ba) {
		StringBuilder sb = new StringBuilder();
		for (byte b : ba) {
			sb.append(byteToBinaryString(b));
		}

		return sb.toString();
	}

	/**
	 * Convert byte to its binary string representation
	 * 
	 * @param b byte
	 * @return binary string representation
	 */
	public static String byteToBinaryString(byte b) {
		return String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
	}

	/**
	 * Construct BCD value.
	 * 
	 * @param num
	 * @return
	 */
	public static byte[] DecToBCDArray(long num) {
		int digits = 0;
		long temp = num;
		while (temp != 0) {
			digits++;
			temp /= 10;
		}
		int byteLen = (digits % 2) == 0 ? digits / 2 : (digits + 1) / 2;
		boolean isOdd = (digits % 2) != 0;
		byte bcd[] = new byte[byteLen];
		for (int i = 0; i < digits; i++) {
			byte tmp = (byte) (num % 10);
			if ((i == (digits - 1)) && isOdd) {
				bcd[i / 2] = tmp;
			} else if ((i % 2) == 0) {
				bcd[i / 2] = tmp;
			} else {
				byte foo = (byte) (tmp << 4);
				bcd[i / 2] |= foo;
			}
			num /= 10;
		}
		for (int i = 0; i < (byteLen / 2); i++) {
			byte tmp = bcd[i];
			bcd[i] = bcd[byteLen - i - 1];
			bcd[byteLen - i - 1] = tmp;
		}

		return bcd;
	}

	public static int hexToDecimal(final String hex) {
		return Integer.parseInt(hex, 16);
	}

	public static String bytesToASCII(byte[] bytes) {
		return new String(bytes, StandardCharsets.US_ASCII);
	}

	public static String hexToASCII(String hex) {
		hex = hex.replaceAll(" ", "");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < hex.length() - 1; i += 2) {
			// grab the hex in pairs
			String output = hex.substring(i, (i + 2));
			// convert hex to decimal
			try {
				int decimal = Integer.parseInt(output, 16);
				// convert the decimal to character
				sb.append((char) decimal);
			} catch (NumberFormatException nfe) {
				return hex;
			}
		}

		return sb.toString();
	}

	public static String ASCIIToHex(String ascii) {
		char[] chars = ascii.toCharArray();
		StringBuffer hex = new StringBuffer();
		for (int i = 0; i < chars.length; i++) {
			hex.append(Integer.toHexString(chars[i]));
		}
		return hex.toString();
	}
}
