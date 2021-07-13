package com.payment.emv.host.utils;

import com.payment.common.util.ConverterUtil;

public class HostField55Compiler {

	private StringBuffer field55Message;

	public HostField55Compiler() {
		field55Message = new StringBuffer();
	}

	public void setField(String fieldTag, String value) {
		if (value != null) {
			field55Message.append(fieldTag);

			byte lengthByte[] = new byte[1];
			lengthByte[0] = (byte) ((value.length() / 2) % 0x100);
			field55Message.append(ConverterUtil.bytesToHexString(lengthByte));

			field55Message.append(value);
		}
	}

	public String getMessage() {
		// return getField55Length() + field55Message.toString();
		return field55Message.toString();
	}

	private String getField55Length() {
		// int length = field55Message.length()/2;
		// byte lengthByte [] = new byte[2];
		// lengthByte[0] = (byte) (length / 0x100);
		// lengthByte[1] = (byte) (length % 0x100);
		//
		// return Utils.bytesToHexString(lengthByte);

		int length = field55Message.length() / 2;
		return ConverterUtil.bytesToHexString(ConverterUtil.DecToBCDArray(length));
	}
}
