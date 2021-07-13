package com.softspace.payment.hsm.bean;

public class JSONServiceDTO {

	private String ksn;
	private String plainText;
	private String cipherText;
	private String encryptedWorkingKey;
	private String initialVector;

	public String getKsn() {
		return ksn;
	}

	public void setKsn(String ksn) {
		this.ksn = ksn;
	}

	public String getPlainText() {
		return plainText;
	}

	public void setPlainText(String plainText) {
		this.plainText = plainText;
	}

	public String getCipherText() {
		return cipherText;
	}

	public void setCipherText(String cipherText) {
		this.cipherText = cipherText;
	}

	public String getEncryptedWorkingKey() {
		return encryptedWorkingKey;
	}

	public void setEncryptedWorkingKey(String encryptedWorkingKey) {
		this.encryptedWorkingKey = encryptedWorkingKey;
	}

	public String getInitialVector() {
		return initialVector;
	}

	public void setInitialVector(String initialVector) {
		this.initialVector = initialVector;
	}

}
