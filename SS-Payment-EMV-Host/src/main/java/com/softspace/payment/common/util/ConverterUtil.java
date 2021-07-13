package com.softspace.payment.common.util;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.payment.emv.host.utils.Utils;

public class ConverterUtil {

	public static String hexToBinary(final String hex) {
		int i = Integer.parseInt(hex, 16);
		String Bin = Integer.toBinaryString(i);

		return Bin;
	}

	public static byte[] hexStringToByteArray(String s) {
		s = s.replaceAll(" ", "");
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	/**
	 * To convert an Integer value to double character hex string.
	 * 
	 * @param value
	 * @return
	 */
	public static String intToHexString(final int value) {
		byte lengthByte[] = new byte[1];
		lengthByte[0] = (byte) (value % 0x100);

		return bytesToHexString(lengthByte);
	}

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

	public static String bytesToHexStringWithSpace(final byte[] b) {
		StringBuffer sb = new StringBuffer();
		if ((b != null) && (b.length > 0)) {
			sb.append(Integer.toHexString((b[0] & 240) >> 4));
			sb.append(Integer.toHexString(b[0] & 15));
		}
		for (int i = 1; i < b.length; i++) {
			sb.append(" ");
			sb.append(Integer.toHexString((b[i] & 240) >> 4));
			sb.append(Integer.toHexString(b[i] & 15));
		}
		return sb.toString();
	}

	public static String bytesToASCII(byte[] bytes) {
		return new String(bytes, StandardCharsets.US_ASCII);
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

	public static byte[] concatByteArrays(final byte[] byteArray1, final byte[] byteArray2) {

		byte[] one = byteArray1;
		byte[] two = byteArray2;
		byte[] combined = new byte[one.length + two.length];

		System.arraycopy(one, 0, combined, 0, one.length);
		System.arraycopy(two, 0, combined, one.length, two.length);

		return combined;
	}

	public static int hexToDecimal(final String hex) {
		return Integer.parseInt(hex, 16);
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

	public static HashMap parseQrTlvField(final String strField) {

		HashMap<Object, Object> fieldResult = new HashMap<>();

		try {
			Map<String, String> hashMapInHex = parseTLV(strField, true);

			for (Map.Entry<String, String> entry : hashMapInHex.entrySet()) {
				fieldResult.put(entry.getKey(), Utils.hexToASCII(entry.getValue()));
			}

		} catch (Exception ex) {
			System.out.println("error converting QR bertlv field " + ex.getMessage());
		}

		return fieldResult;
	}

	public static Map<String, String> parseTLV(String tlv, boolean isQr) {
		if (tlv == null || tlv.length() % 2 != 0) {
			throw new RuntimeException("Invalid tlv, null or odd length");
		}

		HashMap<String, String> hashMap = new HashMap<String, String>();
		for (int i = 0; i < tlv.length();) {
			try {
				String key = tlv.substring(i, i = i + 2);

				boolean isExtraBytesConstant = false;

				if (isQr) {
					// extraBytes for after "T" and "H"
					isExtraBytesConstant = (Integer.parseInt(key, 16) & 0x1F) == 0x1F || (Integer.parseInt(key, 16) & 0x54) == 0x54
							|| (Integer.parseInt(key, 16) & 0x48) == 0x48;
				} else {
					isExtraBytesConstant = (Integer.parseInt(key, 16) & 0x1F) == 0x1F;
				}

				if (isExtraBytesConstant) {
					// extra byte for TAG field
					key += tlv.substring(i, i = i + 2);
				}

				String len = tlv.substring(i, i = i + 2);

				int length = Integer.parseInt(len, 16);
				if (length > 127) {
					// more than 1 byte for lenth
					int bytesLength = length - 128;
					len = tlv.substring(i, i = i + (bytesLength * 2));
					length = Integer.parseInt(len, 16);
				}
				length *= 2;

				String value = tlv.substring(i, i = i + length);
				// System.out.println(key+" = "+value);
				hashMap.put(key, value);
			} catch (NumberFormatException e) {
				throw new RuntimeException("Error parsing number", e);
			} catch (IndexOutOfBoundsException e) {
				throw new RuntimeException("Error processing field", e);
			}
		}

		return hashMap;
	}
}
