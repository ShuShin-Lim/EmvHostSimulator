package com.payment.emv.host.utils;

public class Field55Compiler {

	private StringBuffer field55Message;

	public Field55Compiler() {
		field55Message = new StringBuffer();
	}

	public void setField(String fieldTag, String value) {
		if (value != null) {
			field55Message.append(fieldTag);
			byte lengthByte[] = new byte[1];
			lengthByte[0] = (byte) ((value.length() / 2) % 0x100);
			field55Message.append(Utils.bytesToHexString(lengthByte));
			field55Message.append(value);
		}
	}

	public String getMessage() {
		return field55Message.toString();
	}
}
