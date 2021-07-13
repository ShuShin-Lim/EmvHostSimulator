package com.payment.emv.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87BPackager;

import com.payment.common.util.ConverterUtil;
import com.payment.emv.host.utils.HostField55Compiler;
import com.payment.emv.host.utils.SSISO87BPackager;

public class EmvClient {
	
	public static final String FIELD_2_PAN = "2";
	public static final String FIELD_3_PROCESS_CODE = "3";
	public static final String FIELD_4_TRANSACTION_AMOUNT = "4";
	public static final String FIELD_7_TRANSMISSION_DATE_TIME = "7";
	public static final String FIELD_11_STAN = "11";
	public static final String FIELD_14_EXPIRY_DATE = "14";
	public static final String FIELD_22_POS_ENTRY_MODE = "22";
	public static final String FIELD_23_PAN_SEQUENCE_NUMBER = "23";
	public static final String FIELD_24_NII = "24";
	public static final String FIELD_25_POS_CONDITION_CODE = "25";
	public static final String FIELD_35_TRACK2DATA = "35";
	public static final String FIELD_41_TERMINAL_ID = "41";
	public static final String FIELD_42_MERCHANT_ID = "42";
	public static final String FIELD_49_TRANSACTION_CURRENCY_CODE = "49";
	public static final String FIELD_52_PERSONAL_ID_NUMBER_DATA = "52";
	public static final String FIELD_53_SECURITY_CONTROL_INFO = "53";
	public static final String FIELD_55_EMV_DATA = "55";
	public static final String FIELD_62_TRACE_NO = "62";
	
	public static final String HOST_TPDU_ID = "60";
	public static final String QR_HOST_TPDU_DESTINATION = "0177";
	public static final String QR_HOST_TPDU_SOURCE = "0423";
	public static final String HOST_TPDU_SOURCE = "0000";
	public static final String HOST_TPDU_DESTINATION = "0200";
	
	public static final String ISO_PROCESS_CODE_SALE = "004000";
	
	public static void main(String args[]) throws IOException {
		
		long connectOpenStart = System.currentTimeMillis();

		try (Socket myClient = new Socket()) {
			myClient.connect(new InetSocketAddress("127.0.0.1", 16668), 100000);
			myClient.setSoTimeout(300000);
	
			long connectOpenEnd = System.currentTimeMillis();
	
			System.out.println("Opening Connection success : " + (connectOpenEnd - connectOpenStart));
		
			HashMap<String, Object> requestData = new HashMap<>();
			requestData.put(FIELD_3_PROCESS_CODE,ISO_PROCESS_CODE_SALE);
			requestData.put(FIELD_4_TRANSACTION_AMOUNT, 100l);
			requestData.put(FIELD_11_STAN, 1000l);
			requestData.put(FIELD_22_POS_ENTRY_MODE, "021");
			requestData.put(FIELD_24_NII, "177");
			requestData.put(FIELD_25_POS_CONDITION_CODE,"00");
			requestData.put(FIELD_35_TRACK2DATA, "");
			requestData.put(FIELD_41_TERMINAL_ID, "123456");
			requestData.put(FIELD_42_MERCHANT_ID, "1234567890");
			requestData.put(FIELD_49_TRANSACTION_CURRENCY_CODE,"0458");
			requestData.put(FIELD_49_TRANSACTION_CURRENCY_CODE,"0458");
			
			try (DataOutputStream output = new DataOutputStream(myClient.getOutputStream()); 
					DataInputStream input = new DataInputStream(myClient.getInputStream())) {
				byte[] data = compileMsgSale(requestData);
				System.out.println("Hex String : " + ConverterUtil.bytesToHexString(data) + " length : " + data.length);
	
				output.write(data);
				output.flush();
			
				byte[] lengthByte = new byte[2];
				input.read(lengthByte);
	
				System.out.println("Response Length : " + Integer.parseInt(ConverterUtil.bytesToHexString(lengthByte), 16));
	
				byte completeResponseMsg[] = new byte[Integer.parseInt(ConverterUtil.bytesToHexString(lengthByte), 16)];
	
				input.read(completeResponseMsg);
	
				System.out.println("Response : " + ConverterUtil.bytesToHexString(completeResponseMsg));
						
				if (completeResponseMsg.length <= 5) {
					System.out.println("Error Parsing sales msg response from host");
				}
				byte[] responseMsg = new byte[completeResponseMsg.length - 5];
				System.arraycopy(completeResponseMsg, 5, responseMsg, 0, completeResponseMsg.length - 5);
				
				parseMsgSale(responseMsg);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static Map<String, Object> parseMsgSale(final byte[] msg) {
		HashMap<String, Object> responseData = new HashMap<>();

		try {
			ISOMsg rspIsoMsg = new ISOMsg();
			rspIsoMsg.setPackager(new ISO87BPackager());
			rspIsoMsg.unpack(msg);
			logISOMsg(rspIsoMsg);

			if (rspIsoMsg.hasField(12)) {
				responseData.put("12", rspIsoMsg.getString(12));
			}
			if (rspIsoMsg.hasField(13)) {
				responseData.put("13", rspIsoMsg.getString(13));
			}
			if (rspIsoMsg.hasField(37)) {
				responseData.put("37", rspIsoMsg.getString(37));
			}
			if (rspIsoMsg.hasField(38)) {
				responseData.put("38", rspIsoMsg.getString(38));
			}
			if (rspIsoMsg.hasField(39)) {
				responseData.put("39", rspIsoMsg.getString(39));
			}
			if (rspIsoMsg.hasField(61)) {
				responseData.put("61", rspIsoMsg.getString(61));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return responseData;
	}
	
	private static byte[] compileMsgSale(final Map<String, Object> data) {
		
		byte[] msg = new byte[10];
		byte[] completeMsg = new byte[1];
		try {

			ISOMsg isoMsg = new ISOMsg();
			isoMsg.setPackager(new SSISO87BPackager());
			// Create ISO Message
			isoMsg.setMTI("0200");
			isoMsg.set(3, (String) data.get(FIELD_3_PROCESS_CODE));
			isoMsg.set(4, String.format("%012d", data.get(FIELD_4_TRANSACTION_AMOUNT)));

			if (data.containsKey(FIELD_7_TRANSMISSION_DATE_TIME)) {
				isoMsg.set(7, (String) data.get(FIELD_7_TRANSMISSION_DATE_TIME));
			}

			isoMsg.set(11, String.format("%06d", data.get(FIELD_11_STAN)));
			isoMsg.set(22, (String) data.get(FIELD_22_POS_ENTRY_MODE));
			if (data.containsKey(FIELD_23_PAN_SEQUENCE_NUMBER)) {
				String panSequence = (String) data.get(FIELD_23_PAN_SEQUENCE_NUMBER);
				isoMsg.set(23, panSequence);
			}

			isoMsg.set(24, (String) data.get(FIELD_24_NII));
			isoMsg.set(25, (String) data.get(FIELD_25_POS_CONDITION_CODE));

			//isoMsg.set(35, ConverterUtil.convertEMVTrack2Equivalent((String) data.get(FIELD_35_TRACK2DATA)));

			isoMsg.set(41, (String) data.get(FIELD_41_TERMINAL_ID));
			isoMsg.set(42, (String) data.get(FIELD_42_MERCHANT_ID));

			if (data.containsKey(FIELD_52_PERSONAL_ID_NUMBER_DATA)) {
				isoMsg.set(52, (String) data.get(FIELD_52_PERSONAL_ID_NUMBER_DATA));
				isoMsg.set(53, (String) data.get(FIELD_53_SECURITY_CONTROL_INFO));
			}

			if(data.containsKey(FIELD_55_EMV_DATA)) {
			
				HashMap<String, String> field55Data = (HashMap<String, String>) data.get(FIELD_55_EMV_DATA);
	
				// include field 55 data only if present (ie, emv chip transaction)
				if (field55Data != null && !field55Data.isEmpty()) {
					HostField55Compiler field55 = new HostField55Compiler();
	
					field55.setField("82", field55Data.get("82"));
					field55.setField("84", field55Data.get("84"));
					field55.setField("95", field55Data.get("95"));
					field55.setField("9a", field55Data.get("9A"));
					field55.setField("9c", field55Data.get("9C"));
					field55.setField("5f2a", field55Data.get("5F2A"));
					if (field55Data.containsKey("5F34")) {
						field55.setField("5f34", field55Data.get("5F34"));
					}
					field55.setField("9f02", field55Data.get("9F02"));
					field55.setField("9f03", field55Data.get("9F03"));
					field55.setField("9f09", field55Data.get("9F09"));
					field55.setField("9f10", field55Data.get("9F10"));
					field55.setField("9f1a", field55Data.get("9F1A"));
					field55.setField("9f1e", field55Data.get("9F1E"));
					field55.setField("9f26", field55Data.get("9F26"));
					field55.setField("9f27", field55Data.get("9F27"));
					field55.setField("9f33", field55Data.get("9F33"));
					field55.setField("9f34", field55Data.get("9F34"));
					field55.setField("9f35", field55Data.get("9F35"));
					field55.setField("9f36", field55Data.get("9F36"));
					field55.setField("9f37", field55Data.get("9F37"));
					field55.setField("9f41", field55Data.get("9F41"));
					// Added for Visa paywave mandate
					if (field55Data.containsKey(field55Data.get("9F6E"))) {
						field55.setField("9f6e", field55Data.get("9F6E"));
					}
	
					isoMsg.set(55, ConverterUtil.hexStringToByteArray(field55.getMessage()));
	
				}
			}

			if (data.containsKey(FIELD_62_TRACE_NO)) {
				Long trace = (Long) data.get(FIELD_62_TRACE_NO);
				String traceItemDesc = String.format("%06d", trace);
				isoMsg.set(62, traceItemDesc);
			}

			msg = isoMsg.pack();
			completeMsg = getCompleteMessage(msg, isoMsg);

			System.out.println("Request (Hex) : "+ ConverterUtil.bytesToHexStringWithSpace(completeMsg));

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return completeMsg;
	}
	
	private static byte[] getCompleteMessage(byte[] msg, ISOMsg isoMsg) {
		byte[] completeMsg = new byte[1];
		logISOMsg(isoMsg);

		int intLength = msg.length + getTPDUByteArray(false).length;
		byte msgLength[] = new byte[2];
		msgLength[0] = (byte) (intLength / 0x100);
		msgLength[1] = (byte) (intLength % 0x100);

		completeMsg = new byte[intLength + 2];
		System.arraycopy(msgLength, 0, completeMsg, 0, msgLength.length);
		System.arraycopy(getTPDUByteArray(false), 0, completeMsg, msgLength.length,
				getTPDUByteArray(false).length);
		System.arraycopy(msg, 0, completeMsg, msgLength.length + getTPDUByteArray(false).length, msg.length);
		return completeMsg;
	}
	
	private static byte[] getTPDUByteArray(Boolean isQR) {
		if (isQR != null && Boolean.TRUE.equals(isQR)) {
			return ConverterUtil.hexStringToByteArray(
					HOST_TPDU_ID + QR_HOST_TPDU_DESTINATION + QR_HOST_TPDU_SOURCE);
		} else {
			return ConverterUtil.hexStringToByteArray(HOST_TPDU_ID + HOST_TPDU_DESTINATION +HOST_TPDU_SOURCE);
		}
	}
	
	private static void logISOMsg(final ISOMsg msg) {
		// sensitive data list
		List<String> sensitiveDataList = new ArrayList<>();
		sensitiveDataList.add(FIELD_2_PAN);
		sensitiveDataList.add(FIELD_3_PROCESS_CODE);
		sensitiveDataList.add(FIELD_14_EXPIRY_DATE);
		sensitiveDataList.add(FIELD_35_TRACK2DATA);
		sensitiveDataList.add(FIELD_55_EMV_DATA);

		boolean testingMode = true;
			System.out.println("----ISO MESSAGE-----");
			try {
				System.out.println("  MTI : " + msg.getMTI());
				for (int i = 1; i <= msg.getMaxField(); i++) {
					if (msg.hasField(i)) {
						if ( msg.getString(i) !=null ) {
							if (!testingMode) {
								// hides sensitive cardholder and authentication data
								if (sensitiveDataList.contains(String.valueOf(i))) {
									System.out.println("    Field-" + i + " : <hidden> : <hidden>");
								} else {
									System.out.println("    Field-" + i + " : " + msg.getString(i) + " : "
											+ ConverterUtil.bytesToHexString(msg.getBytes(i)));
								}
							} else {
								System.out.println("    Field-" + i + " : " + msg.getString(i) + " : "
										+ ConverterUtil.bytesToHexString(msg.getBytes(i)));
							}
						} else {
							System.out.println("    Field-" + i + " : <empty> : <empty>");
						}
					}
				}
			} catch (ISOException e) {
				e.printStackTrace();
			} finally {
				System.out.println("--------------------");
			}
		}

}
