package com.payment.emv.host;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87BPackager;

import com.payment.emv.host.utils.BerTlv;
import com.payment.emv.host.utils.Field55Compiler;
import com.payment.emv.host.utils.SSISO87BPackager;
import com.payment.emv.host.utils.Tag;
import com.payment.emv.host.utils.Utils;
import com.softspace.payment.common.util.ConverterUtil;

public class SimpleEMVHost {

	private static String sampleQr = "www.softspace.com.my";
	private static HttpURLConnection con = null;
	private static String rawRequestString = null;

	public static void main(String[] args) throws InterruptedException, IOException {

		// Create a Server socket channel.
		try (ServerSocketChannel serverSocket = ServerSocketChannel.open()) {
			// Bind the server socket channel to the IP address and Port
			serverSocket.bind(new InetSocketAddress("127.0.0.1", 6668));
			// serverSocket.bind(new InetSocketAddress(6668));
			System.out.println("Waiting for client connections");
			while (true) {
				// Wait and Accept the client socket connection.
				try (SocketChannel socketChannel = serverSocket.accept()) {
					// Printing the address of the client.
					System.out.println("Obtained connection from : " + socketChannel.getRemoteAddress().toString());

					// Creating a reader for reading the content on the socket input stream.
					DataInputStream inputStream = new DataInputStream(socketChannel.socket().getInputStream());
					// Reading the content of the socket input stream.

					byte[] reqLen = new byte[2];
					inputStream.read(reqLen);
					System.out.println("Request Length (HEX) : " + Utils.bytesToHexString(reqLen));
					System.out.println("Request Length (DEC) : " + Integer.parseInt(Utils.bytesToHexString(reqLen), 16));
					byte reqBuf[] = new byte[Integer.parseInt(Utils.bytesToHexString(reqLen), 16)];
					inputStream.read(reqBuf);
					System.out.println("Request Data (HEX) : " + Utils.bytesToHexString(reqBuf));

					// parse the iso message
					byte isoBuf[] = new byte[reqBuf.length - 5];

					System.out.println("ISO : " + Utils.bytesToHexString(isoBuf));
					System.arraycopy(reqBuf, 5, isoBuf, 0, isoBuf.length);
					ISOMsg req_isoMsg = new ISOMsg();
					req_isoMsg.setPackager(new SSISO87BPackager());
					req_isoMsg.unpack(isoBuf);

					// retrieve info from request iso
					String req_mti = req_isoMsg.getMTI();
					String processCode = req_isoMsg.getString(3);
					String stan = req_isoMsg.getString(11);
					Calendar cal = Calendar.getInstance();
					SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
					String time = sdf.format(cal.getTime());
					DateFormat dateFormat = new SimpleDateFormat("MMdd");
					Date date = new Date();
					String currentDate = dateFormat.format(date);
					String tid = req_isoMsg.getString(41);
					String mid = req_isoMsg.getString(42);
					// TODO [1.1] persist request data for response usage
					// TODO [1.2] add sanitation check to the request message
					// TODO [2.0] dynamic request processing

					// construct response iso
					ISOMsg res_isoMsg = new ISOMsg();
					rawRequestString = null;
					if ("0200".equals(req_mti)) {
						res_isoMsg = compileSaleResponseMsg(processCode, time, currentDate, stan, tid, mid, req_isoMsg);
					} else if ("0220".equals(req_mti)) {
						res_isoMsg = compileRefundResponseMsg(processCode, time, currentDate, stan, tid, mid, req_isoMsg);
					} else if ("0320".equals(req_mti)) {
						res_isoMsg = compileTcAdviceResponseMsg(processCode, time, currentDate, stan, tid, mid);
					} else if ("0400".equals(req_mti)) {
						res_isoMsg = compileReversalResponseMsg(processCode, time, currentDate, stan, tid, mid);
					} else if ("0500".equals(req_mti)) {
						res_isoMsg = compileSettlementResponseMsg(processCode, time, currentDate, stan, tid, mid);
					} else if ("0800".equals(req_mti)) {
						res_isoMsg = compileLogonResponseMsg();
					}
					System.out.println(req_mti);
					logISOMsg(res_isoMsg);

					byte[] iso_msg = res_isoMsg.pack();
					byte[] tpdu = Utils.hexStringToByteArray("6000000191");
					byte[] res_msg = new byte[1];
					int intLength = iso_msg.length + tpdu.length;
					byte msgLength[] = new byte[2];
					msgLength[0] = (byte) (intLength / 0x100);
					msgLength[1] = (byte) (intLength % 0x100);

					res_msg = new byte[intLength + 2];
					System.arraycopy(msgLength, 0, res_msg, 0, msgLength.length);
					System.arraycopy(tpdu, 0, res_msg, msgLength.length, tpdu.length);
					System.arraycopy(iso_msg, 0, res_msg, msgLength.length + tpdu.length, iso_msg.length);

					DataOutputStream outputStream = new DataOutputStream(socketChannel.socket().getOutputStream());
					Thread.sleep(500L);
					outputStream.write(res_msg);
					outputStream.flush();
					socketChannel.close();

					sendPushNotification();

				} catch (IOException ex) {
					ex.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ISOException e) {
					e.printStackTrace();
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private static void logISOMsg(final ISOMsg msg) {
		try {
			System.out.println("    MTI : " + msg.getMTI());
			for (int i = 1; i <= msg.getMaxField(); i++) {
				if (msg.hasField(i)) {
					if (msg.getString(i) != null && !msg.getString(i).isEmpty()) {
						System.out.println("    Field-" + i + " : " + msg.getString(i) + " : " + Utils.bytesToHexString(msg.getBytes(i)));
					} else {
						System.out.println("    Field-" + i + " : <empty> : <empty>");
					}
				}
			}
		} catch (ISOException e) {
			e.printStackTrace();
		}
	}

	private static ISOMsg compileLogonResponseMsg() throws ISOException {
		ISOMsg res_isoMsg = new ISOMsg();
		res_isoMsg.setPackager(new SSISO87BPackager());
		res_isoMsg.setMTI("0810");
		res_isoMsg.set(3, "001000");
		res_isoMsg.set(11, "000001");
		res_isoMsg.set(12, "003715");
		res_isoMsg.set(13, "1103");
		res_isoMsg.set(39, "00");
		res_isoMsg.set(41, "M8090000");
		// res_isoMsg.setMTI("0200");
		// res_isoMsg.set(3, "004000");
		// res_isoMsg.set(12, "235959");
		// res_isoMsg.set(13, "0101");
		// res_isoMsg.set(37, "A1234567890Z");
		// res_isoMsg.set(38, "R00170");
		Field55Compiler field55 = new Field55Compiler();
		// uncomment line below to enable 71 issuer script
		// field55.setField("71", "860D8424000008F870BFB509529D9F");
		// uncomment line below to enable 72 issuer script
		// field55.setField("72", "861584160000101122334455667788CD8B069E61F6E8F6");
		field55.setField("8A", "3030");
		field55.setField("91", "39383736353433323130");
		res_isoMsg.set(55, Utils.hexStringToByteArray(field55.getMessage()));
		res_isoMsg.set(62, "C160D0F980C2E3F674BF419C67E0B8E4FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
		// uncomment 2 lines below to enable digio's epp response
		// res_isoMsg.set(61,
		// "9991020008000000021600000021600000021600000216000000200000000016000000000");
		// res_isoMsg.set(63, "01ALL PRODUCT ALL PRODUCT 2 OPTION ");
		return res_isoMsg;
	}

	private static ISOMsg compileSaleResponseMsg(String processCode, String time, String currentDate, String stan, String tid, String mid,
			ISOMsg requestISO) throws ISOException, IOException {
		ISOMsg res_isoMsg = new ISOMsg();
		res_isoMsg.setPackager(new SSISO87BPackager());
		res_isoMsg.setMTI("0210");
		res_isoMsg.set(2, "1234567890123456");
		res_isoMsg.set(3, processCode);
		res_isoMsg.set(11, stan);
		res_isoMsg.set(12, time);
		res_isoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		res_isoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		res_isoMsg.set(38, String.valueOf(rand2));
		res_isoMsg.set(39, "00");
		res_isoMsg.set(41, tid);
		res_isoMsg.set(42, mid);
		res_isoMsg.set(55, requestISO.getString(55));

		boolean enableRewardPointParsing = false;
		// qr related
		boolean isQrInquiry = "300000".equals(processCode);
		boolean isQrSale = "510000".equals(processCode);
		String returnStatus = "QF_OK";
		String returnCode = "0000";
		String clientPaymentType = "";
		String qrId = "";
		boolean sendQrPushNotification = false;

		if (isQrSale) {
			res_isoMsg.set(39, "00");
		}

		boolean enableQrParsing = isQrInquiry || isQrSale;

		System.out.println("processCode " + processCode);
		System.out.println("isQrInquiry " + isQrInquiry);
		System.out.println("isQrSale " + isQrSale);

		if (enableRewardPointParsing) {
			// 1. Field-58 : P00000000100000001000 : 1F0315503030303030303030313030303030303031303030
			// 2. Field-58 : DF0415503030303030303030313030303030303031303030 : DF0415503030303030303030313030303030303031303030

			// Reward Point Request
			if (requestISO.hasField(58)) {
				byte[] header = Utils.hexStringToByteArray("1F041E");
				byte[] body = Utils.hexStringToByteArray(ConverterUtil.ASCIIToHex("P00000000100000001000000000499"));

				res_isoMsg.set(58, ConverterUtil.concatByteArrays(header, body));
			} else {
				byte[] header = Utils.hexStringToByteArray("1F0315");
				byte[] body = Utils.hexStringToByteArray(ConverterUtil.ASCIIToHex("P00000000100000001000"));

				res_isoMsg.set(39, "00");
				res_isoMsg.set(58, ConverterUtil.concatByteArrays(header, body));

			}
		}

		if (requestISO.hasField(61)) {
			if (enableQrParsing) {

				// extract field 61
				HashMap<Object, Object> hashMap = ConverterUtil.parseQrTlvField(requestISO.getString(61));
				String clientTransactionDateTime = "";
				if (hashMap.containsKey("10")) {
					clientTransactionDateTime = (String) hashMap.get("10");
				}
				if (hashMap.containsKey("02")) {
					clientPaymentType = (String) hashMap.get("02");
				}
				if (hashMap.containsKey("03")) {
					clientPaymentType = (String) hashMap.get("03");
				}

				ByteArrayOutputStream field61TLV = new ByteArrayOutputStream();

				qrId = String.format("%025d", System.currentTimeMillis());
				BerTlv qrTransactionId = new BerTlv(new Tag(0x01), Utils.hexStringToByteArray(Utils.ASCIIToHex(qrId)));
				field61TLV.write(qrTransactionId.toByteArray());

				BerTlv paymentType = new BerTlv(new Tag(0x03), Utils.hexStringToByteArray(Utils.ASCIIToHex(clientPaymentType)));
				field61TLV.write(paymentType.toByteArray());

				String amount = requestISO.getString(4);
				String endDigit = amount.substring(amount.length() - 2);

				if (endDigit.equals("01")) {
					returnCode = "1143";
					returnStatus = "QF_ERR_ORDER_NOT_EXIST";
					// Rejected by QF Pay, refer Field 61 for detailed error description
				} else if (endDigit.equals("02")) {
					returnCode = "1143";
					returnStatus = "QF_ERR_ORDER_STATUS_UNKNOWN";
				} else if (endDigit.equals("03")) {
					returnCode = "1145";
					returnStatus = "QF_ERR_ORDER_WAIT_PAY";
				} else if (endDigit.equals("04")) {
					returnCode = "1254";
					returnStatus = "QF_ERR_NOCARD_SYSTEM_ERROR";
				} else if (endDigit.equals("05")) {
					returnCode = "1100";
					returnStatus = "ESB_ERR_MAINTEN";
					// Rejected by ESB due to error during request/response processing, refer Field 61 for detailed error description
				} else if (endDigit.equals("06")) {
					returnCode = "1103";
					returnStatus = "CL_ERR_JSON";
					// Rejected by Cardlink, refer Field 61 for detailed error description
				} else if (endDigit.equals("07")) {
					returnCode = "1131";
					returnStatus = "QF_ERR_CHANNEL_TIMEOUT";
					// Timeout when interfacing with QF Pay
				} else if (endDigit.equals("08")) {
					returnCode = "1131";
					returnStatus = "CL_ERR_CHANNEL_TIMEOUT";
					// Timeout when interfacing with Cardlink
				}

				if (isQrSale) {
					BerTlv qrCode = new BerTlv(new Tag(0x06), Utils.hexStringToByteArray(Utils.ASCIIToHex(sampleQr)));
					field61TLV.write(qrCode.toByteArray());

					BerTlv qrExpireTime = new BerTlv(new Tag(0x07), Utils.hexStringToByteArray(Utils.ASCIIToHex("60")));
					field61TLV.write(qrExpireTime.toByteArray());

					BerTlv qrStartInquiryTime = new BerTlv(new Tag(0x08), Utils.hexStringToByteArray(Utils.ASCIIToHex("10")));
					field61TLV.write(qrStartInquiryTime.toByteArray());

					BerTlv qrTransactionLabel = new BerTlv(new Tag(0x09),
							Utils.hexStringToByteArray(Utils.ASCIIToHex("Simulator Respond")));
					field61TLV.write(qrTransactionLabel.toByteArray());

					// less than 100 all will trigger push notification
					if (Long.valueOf(amount) < 10000) {
						sendQrPushNotification = true;
					}
				}

				if (isQrInquiry) {

					if ("1143".equals(returnCode) || "1145".equals(returnCode)) {
						// Rejected by QF Pay, refer Field 61 for detailed error description
						res_isoMsg.set(39, "E6");
					} else if ("1100".equals(returnCode)) {
						// Rejected by ESB due to error during request/response processing, refer Field 61 for detailed error description
						res_isoMsg.set(39, "E3");
					} else if ("1103".equals(returnCode)) {
						// Rejected by Cardlink, refer Field 61 for detailed error description
						res_isoMsg.set(39, "E7");
					} else if ("1131".equals(returnCode)) {
						// Timeout when interfacing with QF Pay
						res_isoMsg.set(39, "C8");
					}

					if (res_isoMsg.getString(39).startsWith("E")) {
						BerTlv respondCode = new BerTlv(new Tag(Utils.hexStringToByteArray(Utils.ASCIIToHex("H1"))),
								Utils.hexStringToByteArray(Utils.ASCIIToHex("1100")));
						field61TLV.write(respondCode.toByteArray());

						BerTlv respondMsg = new BerTlv(new Tag(Utils.hexToDecimal(Utils.ASCIIToHex("H2"))),
								Utils.hexStringToByteArray(Utils.ASCIIToHex("Rejected by ESB / QF Pay / Cardlink")));
						field61TLV.write(respondMsg.toByteArray());
					}

					BerTlv respondCode = new BerTlv(new Tag(Utils.hexStringToByteArray(Utils.ASCIIToHex("T1"))),
							Utils.hexStringToByteArray(Utils.ASCIIToHex(returnCode)));
					field61TLV.write(respondCode.toByteArray());

					BerTlv respondMsg = new BerTlv(new Tag(Utils.hexToDecimal(Utils.ASCIIToHex("T2"))),
							Utils.hexStringToByteArray(Utils.ASCIIToHex(returnStatus)));
					field61TLV.write(respondMsg.toByteArray());

					BerTlv clientDateTime = new BerTlv(new Tag(0x10),
							Utils.hexStringToByteArray(Utils.ASCIIToHex(clientTransactionDateTime)));
					field61TLV.write(clientDateTime.toByteArray());

					String localDate = new DateTime(new Date()).toString("yyMMdd'T'HHmmss");
					BerTlv sysDateTime = new BerTlv(new Tag(0x13), Utils.hexStringToByteArray(Utils.ASCIIToHex(localDate)));
					field61TLV.write(sysDateTime.toByteArray());

					BerTlv stanBer = new BerTlv(new Tag(0x11), Utils.hexStringToByteArray(Utils.ASCIIToHex(stan)));
					field61TLV.write(stanBer.toByteArray());

					BerTlv orderType = new BerTlv(new Tag(0x14), Utils.hexStringToByteArray(Utils.ASCIIToHex("Payment")));
					field61TLV.write(orderType.toByteArray());

					BerTlv transactionStatus = new BerTlv(new Tag(Utils.hexToDecimal(Utils.ASCIIToHex("TS"))),
							Utils.hexStringToByteArray(Utils.ASCIIToHex("0")));
					field61TLV.write(transactionStatus.toByteArray());

				}

				res_isoMsg.set(61, Utils.bytesToHexString(field61TLV.toByteArray()));

			} else {
				// IPP request
				// Field-61 : IP1111003000000000100 : 495031313131303033303030303030303030313030
				res_isoMsg.set(61, "00606000000000000000000000000000000000000");
			}
		}

		// if is pending-processing or timeout dont trigger push notification
		if (sendQrPushNotification && !("1143".equals(returnCode) || "1145".equals(returnCode) || "1131".equals(returnCode))
				|| "1103".equals(returnCode)) {
			setPushNotification(stan, returnCode, returnStatus, clientPaymentType, qrId);
		}

		return res_isoMsg;
	}

	private static void sendPushNotification() throws IOException, InterruptedException {
		if (rawRequestString != null) {
			System.out.println("sending push notification");
			System.out.printf("JSON: %s", rawRequestString);
			String pushNotAddrress = "http://localhost:8092/qr/result";
			Thread.sleep(5000L); // 5 seconds only push notification back
			openConnection(pushNotAddrress);
			String rawResponseString = doV2Post(rawRequestString, false);
			System.out.println("JSON response: " + rawResponseString);
			con.disconnect();
		}
	}

	private static void setPushNotification(String stan, String status, String statusMsg, String payType, String qrId) {

		Map<String, Object> data = new HashMap<>();
		data.put("pan", "                    ");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		data.put("txndtm", sdf.format(new Date()));
		data.put("out_trade_no", stan);
		data.put("respcd", status);
		data.put("resmsg", statusMsg);
		data.put("pay_type", payType);
		data.put("bill_number", qrId);
		data.put("mobile_number", "0123456789");
		data.put("store_label", "");
		data.put("loyalty_number", "A123456");
		data.put("reference_label", "reference_label");
		data.put("consumer_label", "consumer_label");
		data.put("terminal_label", "terminal_label");
		data.put("purpose_of_txn", "sales");
		data.put("consumer_aditional_data", "false");
		data.put("rrn", "90|30|CIMBPAY");
		data.put("rrn2", "91919195");
		data.put("Geo Coordinates", "[37.224236, -95.708414]");

		rawRequestString = com.payment.emv.host.utils.JSONFactory.toJson(data);

	}

	private static ISOMsg compileRefundResponseMsg(String processCode, String time, String currentDate, String stan, String tid, String mid,
			ISOMsg requestISO) throws ISOException, IOException {
		ISOMsg res_isoMsg = new ISOMsg();
		res_isoMsg.setPackager(new SSISO87BPackager());
		res_isoMsg.setMTI("0230");
		res_isoMsg.set(2, "1234567890123456");
		res_isoMsg.set(3, processCode);
		res_isoMsg.set(11, stan);
		res_isoMsg.set(12, time);
		res_isoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		res_isoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		res_isoMsg.set(38, String.valueOf(rand2));
		res_isoMsg.set(39, "00");
		res_isoMsg.set(41, tid);
		res_isoMsg.set(42, mid);
		res_isoMsg.set(55, requestISO.getString(55));

		boolean isQrRefund = "200000".equals(processCode);

		System.out.println("processCode " + processCode);

		if (requestISO.hasField(61)) {
			if (isQrRefund) {
				// extract field 61
				HashMap<Object, Object> hashMap = ConverterUtil.parseQrTlvField(requestISO.getString(61));
				String clientMainQrId = (String) hashMap.get("01");

				ByteArrayOutputStream field61TLV = new ByteArrayOutputStream();

				BerTlv qrTransactionId = new BerTlv(new Tag(0x01), Utils.hexStringToByteArray(Utils.ASCIIToHex(clientMainQrId)));
				field61TLV.write(qrTransactionId.toByteArray());

				String qrRefundId = String.format("%025d", System.currentTimeMillis());
				BerTlv qrRefundTransactionId = new BerTlv(new Tag(0x02), Utils.hexStringToByteArray(Utils.ASCIIToHex(qrRefundId)));
				field61TLV.write(qrRefundTransactionId.toByteArray());

				String localDate = new DateTime(new Date()).toString("yyyyMMdd'T'HHmmss");

				BerTlv clientDateTime = new BerTlv(new Tag(0x10), Utils.hexStringToByteArray(Utils.ASCIIToHex(localDate)));
				field61TLV.write(clientDateTime.toByteArray());

				BerTlv sysDateTime = new BerTlv(new Tag(0x13), Utils.hexStringToByteArray(Utils.ASCIIToHex(localDate)));
				field61TLV.write(sysDateTime.toByteArray());

				res_isoMsg.set(61, Utils.bytesToHexString(field61TLV.toByteArray()));

			}
		}

		return res_isoMsg;
	}

	private static ISOMsg compileTcAdviceResponseMsg(String processCode, String time, String currentDate, String stan, String tid,
			String mid) throws ISOException {
		ISOMsg res_isoMsg = new ISOMsg();
		res_isoMsg.setPackager(new ISO87BPackager());
		res_isoMsg.setMTI("0330");
		res_isoMsg.set(3, processCode);
		res_isoMsg.set(11, stan);
		res_isoMsg.set(12, time);
		res_isoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		res_isoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		res_isoMsg.set(38, String.valueOf(rand2));
		res_isoMsg.set(39, "00");
		res_isoMsg.set(41, tid);
		res_isoMsg.set(42, mid);
		return res_isoMsg;
	}

	private static ISOMsg compileReversalResponseMsg(String processCode, String time, String currentDate, String stan, String tid,
			String mid) throws ISOException {
		ISOMsg res_isoMsg = new ISOMsg();
		res_isoMsg.setPackager(new ISO87BPackager());
		res_isoMsg.setMTI("0410");
		res_isoMsg.set(3, processCode);
		res_isoMsg.set(11, stan);
		res_isoMsg.set(12, time);
		res_isoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		res_isoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		res_isoMsg.set(38, String.valueOf(rand2));
		res_isoMsg.set(39, "00");
		res_isoMsg.set(41, tid);
		res_isoMsg.set(42, mid);
		return res_isoMsg;
	}

	private static ISOMsg compileSettlementResponseMsg(String processCode, String time, String currentDate, String stan, String tid,
			String mid) throws ISOException {
		ISOMsg res_isoMsg = new ISOMsg();
		res_isoMsg.setPackager(new ISO87BPackager());
		res_isoMsg.setMTI("0510");
		res_isoMsg.set(3, processCode);
		res_isoMsg.set(11, stan);
		res_isoMsg.set(12, time);
		res_isoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		res_isoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		res_isoMsg.set(38, String.valueOf(rand2));
		res_isoMsg.set(39, "00");
		res_isoMsg.set(41, tid);
		res_isoMsg.set(42, mid);
		return res_isoMsg;
	}

	private static void openConnection(String callBackUrl) throws IOException {
		URL url = new URL(callBackUrl);
		con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
		con.setRequestProperty("Compression-Method", "gzip");
		con.setDoOutput(true);
		con.setRequestProperty("Content-Encoding", "gzip");
		con.setRequestProperty("Accept-Encoding", "gzip");
	}

	private static String doV2Post(final String request, final boolean compressZip) throws IOException {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		long now = System.currentTimeMillis();
		if (compressZip) {
			GZIPOutputStream gzos = new GZIPOutputStream(baos);
			byte[] uncompressedBytes = request.getBytes(StandardCharsets.UTF_8);

			gzos.write(uncompressedBytes, 0, uncompressedBytes.length);
			gzos.close();

			String compressed = Base64.encodeBase64String(baos.toByteArray());

			OutputStream os;
			os = con.getOutputStream();
			os.write(compressed.getBytes());
			os.flush();
		} else {
			BufferedOutputStream gzos = new BufferedOutputStream(baos);
			byte[] uncompressedBytes = request.getBytes(StandardCharsets.UTF_8);

			gzos.write(uncompressedBytes, 0, uncompressedBytes.length);
			gzos.close();

			OutputStream os;
			os = con.getOutputStream();
			os.write(uncompressedBytes);
			os.flush();
		}

		System.out.println("Http post took " + (System.currentTimeMillis() - now) + " ms.");

		int responseCode = con.getResponseCode();
		System.out.println("POST Response Code :: " + responseCode);

		if (responseCode == HttpURLConnection.HTTP_OK) { // success
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();

			// print result
			System.out.println(response.toString());

			return response.toString();
		}

		return "";
	}
}
