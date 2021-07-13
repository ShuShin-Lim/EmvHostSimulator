package com.softspace.payment.hsm.util;

public class Utils {

	public static String decimalToHexWithLunaFormat(int integer) {
		String hex = null;
		try {
			if (integer < 0) {
				throw new Exception("pass in length integer cannot be negative value, maximum length for int is " + Integer.MAX_VALUE
						+ " if the amount is too big, int will become negative");
			}

			String i = Integer.toBinaryString(integer);

			// *important LUNA format, please refer to LUNA programming guide page 26
			// check the integer range will need to display in how many byte length
			// formula (2 ^ n) - 1
			// n = 7 * x
			// x = counter
			// if the formula return result is smaller than the pass in integer counter + 1, means byte plus 1
			double counter = 1;
			int totalByte = 1;
			while ((Math.pow(2, 7 * counter) - 1) < integer) {
				counter++;
				totalByte++;
			}

			// convert the binary back to hex
			String dataHex = Long.toHexString(Long.parseLong(i, 2));

			// construct the format based on the total of byte. Example 128 integer = 80 hex, 128 integer in LUNA format
			// is 2 byte so become 0080
			dataHex = String.format("%" + (totalByte * 2) + "s", dataHex).replace(' ', '0').toUpperCase();// multiply 2
																											// is
																											// because 1
																											// hex = 2
																											// length in
																											// string

			// append 1 in to the binary to indicate this is a how many byte length hex
			char[] tempBit = hexToBinaryWithSize(dataHex, totalByte).toCharArray();
			for (int index = 0; index < (totalByte - 1); index++) {
				tempBit[index] = '1';
			}
			i = String.valueOf(tempBit);
			dataHex = Long.toHexString(Long.parseLong(i, 2)).toUpperCase();

			hex = String.format("%" + (totalByte * 2) + "s", dataHex).replace(' ', '0');
			return hex;
		} catch (Exception ex) {
			throw new RuntimeException("Exception happen in decimalToHexWithLunaFormat. Exception = " + ex);
		}
	}

	public static String hexToBinary(String hex) {
		int i = Integer.parseInt(hex, 16);
		String Bin = Integer.toBinaryString(i);

		System.out.println(Bin);

		return Bin;
	}

	public static String hexToBinaryWithSize(String hex, int byteLength) {
		int i = Integer.parseInt(hex, 16);
		String Bin = String.format("%" + (byteLength * 8) + "s", Integer.toBinaryString(i)).replace(' ', '0');
		// add leading 0 for bit. 1 byte = 8 bit

		return Bin;
	}

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	public static String bytesToHexString(byte[] bytes) {
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

	public static String bytesToHexStringWithSpace(byte[] b) {
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

	// Construct Double Byte Hex Length value
	public static byte[] intToDoubleByteHexLength(int length) {
		byte lengthByte[] = new byte[2];
		lengthByte[0] = (byte) (length / 0x100);
		lengthByte[1] = (byte) (length % 0x100);

		return lengthByte;
	}

	// Construct Byte Hex Length value
	public static byte[] intToHexLength(int length) {
		// byte lengthByte [] = new byte[2];
		// lengthByte[0] = (byte) (length / 0x100);
		// lengthByte[1] = (byte) (length % 0x100);

		return hexStringToByteArray(Integer.toHexString(0x100 | length).substring(1).toUpperCase());
	}

	// Construct Single Byte BCD value
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

	// to Convert EMV's Track2Equivalent data to unformatted version
	public static String convertEMVTrack2Equivalent(String strOriginal) {

		strOriginal = strOriginal.toUpperCase();

		// System.out.println("originalMsg : " + strOriginal);
		String cardNumber = strOriginal.substring(0, strOriginal.indexOf("D"));

		String remainingData = "";

		// ymtoh - 20121210 : check whether is there F being used as filler bytes at the end...
		if (strOriginal.indexOf("F") > 0) {
			remainingData = strOriginal.substring(strOriginal.indexOf("D") + 1, strOriginal.indexOf("F"));
		} else {
			remainingData = strOriginal.substring(strOriginal.indexOf("D") + 1, strOriginal.length());
		}

		String strConverted = cardNumber + "=" + remainingData;

		return strConverted;
	}

	public static String compileReconciliationMessage(int saleCount, int saleTrxAmount) {
		String strSaleCount = String.format("%03d", saleCount);
		String strSaleTrxAmount = String.format("%012d", saleTrxAmount);

		String strReconciliationMsg = String.format("%-90s", strSaleCount + strSaleTrxAmount).replace(' ', '0');
		;

		return strReconciliationMsg;
	}
	
	public static byte[] HexStringToByteArray(String lexicalXSDHexBinary) {
		return javax.xml.bind.DatatypeConverter
				.parseHexBinary(lexicalXSDHexBinary);
	}

	public static String ByteArrayToHexString(byte[] bytes) {
		return javax.xml.bind.DatatypeConverter.printHexBinary(bytes);
	}

	// converts an integer value into a 0-prepadded binary string of specified
	// length
	public static String IntToBinaryString(int value) {
		// String s = String.format("%16s", Integer.toBinaryString(value));
		// s.replace(' ', '0');
		// return s;
		// System.out.println(Integer.toBinaryString(encrypt_ctr));

		return Integer.toString(value, 2);
	}	
}
