package com.payment.emv.host;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.packager.ISO87BPackager;

import com.payment.common.util.ConverterUtil;
import com.payment.emv.host.utils.Field55Compiler;
import com.payment.emv.host.utils.QRPackager;
import com.payment.emv.host.utils.Utils;

public class SimpleEMVMultiHost {

	public static List<String> pushNotCQueue = new ArrayList<>();
	public static List<String> qrRefundList = new ArrayList<>();
	public static final int PADDING_ALIGN_LEFT = 0;
	public static final int PADDING_ALIGN_RIGHT = 1;
	public static final String PADDING_TYPE_ZERO = "0";
	public static final String PADDING_TYPE_SPACE = "";
	public static String tpdu;

	public static void main(String[] args) {
		SimpleEMVMultiHost server = new SimpleEMVMultiHost();
		try {
			while (true) {
				Thread.sleep(60000);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public SimpleEMVMultiHost() {
		try {
			// Create an AsynchronousServerSocketChannel that will listen on port 5000
			final AsynchronousServerSocketChannel listener = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(16668));

			System.out.println("Listening port 16668");

			// Listen for a new request
			listener.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {

				@Override
				public void completed(AsynchronousSocketChannel ch, Void att) {
					// Accept the next connection
					listener.accept(null, this);

					// Greet the client
					// ch.write(ByteBuffer.wrap("Hello, I am Echo Server 2020, let's have an engaging conversation!\n".getBytes()));

					// Allocate a byte buffer (4K) to read from the client
					ByteBuffer byteBuffer = ByteBuffer.allocate(4096);
					try {
						// Read the first line
						int bytesRead = ch.read(byteBuffer).get(20, TimeUnit.SECONDS);

						boolean running = true;
						StringBuilder reqInHex = new StringBuilder("");
						ISOMsg resIsoMsg = null;
						System.out.println("request received : " + new Date());
						while (bytesRead != -1 && running) {
							System.out.println("bytes read: " + bytesRead);

							// Make sure that we have data to read
							if (byteBuffer.position() > 2) {
								// Make the buffer ready to read
								byteBuffer.flip();

								// Convert the buffer into a line
								byte[] lineBytes = new byte[bytesRead];
								byteBuffer.get(lineBytes, 0, bytesRead);

								String fullReqInHex = Utils.bytesToHexString(lineBytes);
								reqInHex.append(fullReqInHex);
								System.out.println("Full Request (HEX) : " + reqInHex.toString());

								String tcpHeaderLength = reqInHex.substring(0, 4);
								System.out.println("TCP Header length : " + tcpHeaderLength);
								reqInHex.delete(0, 4);

								tpdu = reqInHex.substring(0, 10);
								System.out.println("TPDU : " + tpdu);
								reqInHex.delete(0, 10);

								byte fullIsoMessage[] = Utils.hexStringToByteArray(reqInHex.toString());

								// QR TPDU
								if ("6001610423".equals(tpdu)) {
									System.out.println("Processing QR... ");
									resIsoMsg = ProcessQRRequest(fullIsoMessage);
								} else {
									System.out.println("Processing CreditCard... ");
									resIsoMsg = ProcessCCRequest(fullIsoMessage);
								}

								logISOMsg(resIsoMsg);

								byte[] isoMsg = resIsoMsg.pack();
								byte[] resTpdu = Utils.hexStringToByteArray("6000000191");
								byte[] resMsg = new byte[1];
								int intLength = isoMsg.length + resTpdu.length;
								byte msgLength[] = new byte[2];
								msgLength[0] = (byte) (intLength / 0x100);
								msgLength[1] = (byte) (intLength % 0x100);

								resMsg = new byte[intLength + 2];
								System.arraycopy(msgLength, 0, resMsg, 0, msgLength.length);
								System.arraycopy(resTpdu, 0, resMsg, msgLength.length, resTpdu.length);
								System.arraycopy(isoMsg, 0, resMsg, msgLength.length + resTpdu.length, isoMsg.length);

								ch.write(ByteBuffer.wrap(resMsg));

								if ("6001610423".equals(tpdu)) {
									System.out.println("Check whether to send push notification for QR... ");
									new SendQRPushNotification().run();
								}

								// Make the buffer ready to write
								byteBuffer.clear();

								// Read the next line
								bytesRead = ch.read(byteBuffer).get(20, TimeUnit.SECONDS);

								reqInHex.setLength(0);

							} else {
								// An empty line signifies the end of the conversation in our protocol
								running = false;
							}
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (ExecutionException e) {
						e.printStackTrace();
					} catch (TimeoutException e) {
						// The user exceeded the 20 second timeout, so close the connection
						System.out.println("Connection timed out, closing connection");
					} catch (ISOException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}

					System.out.println("===============================================");
					try {
						// Close the connection if we need to
						if (ch.isOpen()) {
							ch.close();
						}
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}

				@Override
				public void failed(Throwable exc, Void att) {
					/// ...
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String doV2Post(String pushNotAddrress, final String request, final boolean compressZip) throws IOException {

		URL url = new URL(pushNotAddrress);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
		con.setRequestProperty("Compression-Method", "gzip");
		con.setUseCaches(false);
		con.setDoOutput(true);
		con.setAllowUserInteraction(false);
		con.setRequestProperty("Connection", "close");
		con.setRequestProperty("Content-Encoding", "gzip");
		con.setRequestProperty("Accept-Encoding", "gzip");

		long now = System.currentTimeMillis();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		OutputStream os = null;

		try {

			if (compressZip) {
				GZIPOutputStream gzos = new GZIPOutputStream(baos);
				byte[] uncompressedBytes = request.getBytes(StandardCharsets.UTF_8);

				gzos.write(uncompressedBytes, 0, uncompressedBytes.length);
				gzos.close();

				String compressed = Base64.encodeBase64String(baos.toByteArray());

				os = con.getOutputStream();
				os.write(compressed.getBytes());
				os.flush();
			} else {
				BufferedOutputStream gzos = new BufferedOutputStream(baos);
				byte[] uncompressedBytes = request.getBytes(StandardCharsets.UTF_8);

				gzos.write(uncompressedBytes, 0, uncompressedBytes.length);
				gzos.close();

				os = con.getOutputStream();
				os.write(uncompressedBytes);
				os.flush();
			}
		} finally {
			baos.close();
			if (os != null) {
				os.close();
			}

		}

		System.out.println("Http post took " + (System.currentTimeMillis() - now) + " ms.");

		int responseCode = con.getResponseCode();
		System.out.println("POST Response Code :: " + responseCode);

		try {
			if (responseCode == HttpURLConnection.HTTP_OK) { // success

				BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) {
					sb.append(line + "\n");
				}
				br.close();
				return sb.toString();

			}
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (con != null) {
				try {
					con.disconnect();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}

		return "";
	}

	private static void logISOMsg(final ISOMsg msg) {
		try {
			System.out.println("===============================================");
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

	private static ISOMsg ProcessCCRequest(byte[] fullIsoMessage) throws ISOException, IOException {

		ISOMsg reqIsoMsg = new ISOMsg();
		reqIsoMsg.setPackager(getISOPackagerBasedOnTPDU());
		reqIsoMsg.unpack(fullIsoMessage);

		logISOMsg(reqIsoMsg);

		ISOMsg resIsoMsg = new ISOMsg();
		String reqMti = reqIsoMsg.getMTI();

		String processCode = reqIsoMsg.getString(3);
		boolean isVoid = ("024000".equals(processCode) || "020000".equals(processCode));

		if ("0100".equals(reqMti)) {
			boolean isBalanceInquiry = "31".equals(processCode.substring(0, 2));
			if (isBalanceInquiry) {
				resIsoMsg = compileBalanceInquiryResponseMsg(reqIsoMsg);
			} else {
				resIsoMsg = compilePreauthResponseMsg(reqIsoMsg);
			}
		} else if ("0200".equals(reqMti)) {
			if (isVoid) {
				resIsoMsg = compileVoidResponseMsg(reqIsoMsg);
			} else {
				resIsoMsg = compileSaleResponseMsg(reqIsoMsg);
			}
		} else if ("0220".contentEquals(reqMti)) {
			resIsoMsg = compileOfflineSaleResponseMsg(reqIsoMsg);
		} else if ("0320".equals(reqMti)) {
			resIsoMsg = compileTcAdviceResponseMsg(reqIsoMsg);
		} else if ("0400".equals(reqMti)) {
			resIsoMsg = compileReversalResponseMsg(reqIsoMsg);
		} else if ("0500".equals(reqMti)) {
			resIsoMsg = compileSettlementResponseMsg(reqIsoMsg);
		} else if ("0800".equals(reqMti)) {
			resIsoMsg = compileLogonResponseMsg(reqIsoMsg);
		}

		return resIsoMsg;
	}

	private static ISOMsg ProcessQRRequest(byte[] fullIsoMessage) throws ISOException, IOException {

		ISOMsg reqIsoMsg = new ISOMsg();
		reqIsoMsg.setPackager(new QRPackager());
		reqIsoMsg.unpack(fullIsoMessage);

		logISOMsg(reqIsoMsg);

		ISOMsg resIsoMsg = new ISOMsg();
		String reqMti = reqIsoMsg.getMTI();
		String processCode = reqIsoMsg.getString(3);
		boolean isVoid = "024000".equals(processCode) || "220000".equals(processCode);
		boolean isInquiry = "300000".equals(processCode);
		boolean isSale = "510000".equals(processCode);
		boolean isRefund = "200000".equals(processCode);
		if ("0200".equals(reqMti)) {
			if (isSale) {
				resIsoMsg = compileSaleResponseMsgQR(reqIsoMsg);
			} else if (isInquiry) {
				resIsoMsg = compileInquiryResponseMsgQR(reqIsoMsg);
			}
		} else if ("0220".equals(reqMti)) {
			if (isRefund) {
				resIsoMsg = compileRefundResponseMsgQR(reqIsoMsg);
			} else if (isVoid) {
				resIsoMsg = compileVoidResponseMsgQR(reqIsoMsg);
			}
		} else if ("0400".equals(reqMti)) {
			resIsoMsg = compileReversalResponseMsg(reqIsoMsg);
		} else if ("0420".equals(reqMti)) {
			resIsoMsg = compileRefundReversalResponseMsgQR(reqIsoMsg);
		}

		return resIsoMsg;
	}

	private static ISOMsg compileRefundReversalResponseMsgQR(ISOMsg requestISO) throws ISOException {
		String processCode = requestISO.getString(3);
		String stan = requestISO.getString(11);
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);
		String tid = requestISO.getString(41);
		String mid = requestISO.getString(42);

		ISOMsg resIsoMsg = new ISOMsg();
		resIsoMsg.setPackager(new QRPackager());
		resIsoMsg.setMTI("0430");
		resIsoMsg.set(3, processCode);
		resIsoMsg.set(11, stan);
		resIsoMsg.set(12, time);
		resIsoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));
		resIsoMsg.set(39, "58");
		resIsoMsg.set(41, tid);
		resIsoMsg.set(42, mid);
		resIsoMsg.set(48,
				"H10041119H2046ReversalRefund is not supported for this orderE10041119E2181Error From Service Provider.Please refer to //SignonRs/HeaderRs/ProviderList/Provider/ProviderRespStatusList/ProviderRespStatus for the Status Code and Desc");
		return resIsoMsg;
	}

	private static ISOMsg compileReversalResponseMsg(ISOMsg requestISO) throws ISOException {
		String processCode = requestISO.getString(3);
		String stan = requestISO.getString(11);
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);
		String tid = requestISO.getString(41);
		String mid = requestISO.getString(42);

		String field12Resp = time;

		// cubc field 12 format is YYMMDDhhmmss
		if (requestISO.hasField(12) && requestISO.getString(12).length() == 12) {
			SimpleDateFormat sdfCUBC = new SimpleDateFormat("yyMMddhhmmss");
			field12Resp = sdfCUBC.format(new Date());
		}

		ISOMsg resIsoMsg = new ISOMsg();
		resIsoMsg.setPackager(getISOPackagerBasedOnTPDU());
		resIsoMsg.setMTI("0410");
		resIsoMsg.set(3, processCode);

		if (requestISO.hasField(7)) {
			resIsoMsg.set(7, requestISO.getString(7));
		}

		resIsoMsg.set(11, stan);
		resIsoMsg.set(12, field12Resp);
		resIsoMsg.set(13, currentDate);

		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));
		resIsoMsg.set(39, "00");
		resIsoMsg.set(41, tid);
		resIsoMsg.set(42, mid);

		if (requestISO.hasField(49)) {
			resIsoMsg.set(49, requestISO.getString(49));
		}

		return resIsoMsg;
	}

	private static ISOMsg compileVoidResponseMsg(ISOMsg requestISO) throws ISOException, IOException {

		String processCode = requestISO.getString(3);
		String stan = requestISO.getString(11);
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);
		String tid = requestISO.getString(41);
		String mid = requestISO.getString(42);

		String field12Resp = time;

		// cubc field 12 format is YYMMDDhhmmss
		if (requestISO.hasField(12) && requestISO.getString(12).length() == 12) {
			SimpleDateFormat sdfCUBC = new SimpleDateFormat("yyMMddhhmmss");
			field12Resp = sdfCUBC.format(new Date());
		}

		ISOMsg resIsoMsg = new ISOMsg();
		resIsoMsg.setPackager(getISOPackagerBasedOnTPDU());

		// cubc void is 0200 msg
		if ("0200".equals(requestISO.getMTI())) {
			resIsoMsg.setMTI("0210");
		} else {
			resIsoMsg.setMTI("0230");
		}

		resIsoMsg.set(2, "1234567890123456");
		resIsoMsg.set(3, processCode);

		if (requestISO.hasField(7)) {
			resIsoMsg.set(7, requestISO.getString(7));
		}

		resIsoMsg.set(11, stan);
		resIsoMsg.set(12, field12Resp);
		resIsoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));
		resIsoMsg.set(39, "00");
		resIsoMsg.set(41, tid);
		resIsoMsg.set(42, mid);

		if (requestISO.hasField(49)) {
			resIsoMsg.set(49, requestISO.getString(49));
		}

		resIsoMsg.set(55, requestISO.getString(55));

		return resIsoMsg;
	}

	private static ISOMsg compileVoidResponseMsgQR(ISOMsg requestISO) throws ISOException, IOException {

		String processCode = requestISO.getString(3);
		String stan = requestISO.getString(11);
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		DateFormat dateFormat2 = new SimpleDateFormat("yyyyMMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);
		String tid = requestISO.getString(41);
		String mid = requestISO.getString(42);

		ISOMsg resIsoMsg = new ISOMsg();
		resIsoMsg.setPackager(new QRPackager());
		resIsoMsg.setMTI("0230");
		resIsoMsg.set(2, "1234567890123456");
		resIsoMsg.set(3, processCode);
		resIsoMsg.set(11, stan);
		resIsoMsg.set(12, time);
		resIsoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));
		resIsoMsg.set(41, tid);
		resIsoMsg.set(42, mid);
		resIsoMsg.set(55, requestISO.getString(55));

		String amount = requestISO.getString(4);
		String[] respond = getMockRespond(amount);
		// resIsoMsg.set(39, "E6");
		resIsoMsg.set(39, respond[2]);

		if (requestISO.hasField(61)) {
			// extract field 61
			HashMap<Object, Object> hashMap = parseQrTlvField(requestISO.getString(61));
			String clientMainQrId = (String) hashMap.get("01");

			ByteArrayOutputStream field61TLV = new ByteArrayOutputStream();
			StringBuilder field61Sb = new StringBuilder();

			// qrTransactionId
			field61Sb.append("01" + "025" + clientMainQrId);

			// refundId
			String qrRefundId = String.format("%025d", System.currentTimeMillis());
			field61Sb.append("02" + "025" + qrRefundId);

			String localDate = new DateTime(new Date()).toString("yyyyMMdd'T'HHmmss");
			field61Sb.append("10" + "015" + localDate);

			field61Sb.append("13" + "015" + localDate);

			field61Sb.append("29" + "008" + "87654321");

			resIsoMsg.set(48,
					"H10041269H2042today unsettled amount is not enough(1269)E10049999E2181Error From Service Provider.Please refer to //SignonRs/HeaderRs/ProviderList/Provider/ProviderRespStatusList/ProviderRespStatus for the Status Code and Desc");

			String rppID = null;
			String clientPaymentType = (String) hashMap.get("03");
			String CIMBPayClientPaymentType = "802501";
			if (CIMBPayClientPaymentType.equals(clientPaymentType)) {
				// duitnow id
				// duitnowRRPID
				int n = new Random().nextInt(99999999);
				rppID = "30" + "031" + dateFormat2.format(new Date()) + "SIMULATOR" + "030OQR"
						+ formatStringWithPadding(String.valueOf(n), PADDING_ALIGN_LEFT, PADDING_TYPE_ZERO, 8);
				field61Sb.append(rppID);
			}

			field61TLV.write(ConverterUtil.hexStringToByteArray(ConverterUtil.ASCIIToHex(field61Sb.toString())));
			resIsoMsg.set(61, field61TLV.toByteArray());

		}

		return resIsoMsg;

	}

	@SuppressWarnings("unchecked")
	private static ISOMsg compileSaleResponseMsg(ISOMsg requestISO) throws ISOException, IOException {

		String processCode = requestISO.getString(3);
		String stan = requestISO.getString(11);
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);
		String tid = requestISO.getString(41);
		String mid = requestISO.getString(42);

		String field12Resp = time;

		// cubc field 12 format is YYMMDDhhmmss
		if (requestISO.hasField(12) && requestISO.getString(12).length() == 12) {
			SimpleDateFormat sdfCUBC = new SimpleDateFormat("yyMMddhhmmss");
			field12Resp = sdfCUBC.format(new Date());
		}

		ISOMsg resIsoMsg = new ISOMsg();
		resIsoMsg.setPackager(getISOPackagerBasedOnTPDU());
		resIsoMsg.setMTI("0210");
		resIsoMsg.set(2, "1234567890123456");
		resIsoMsg.set(3, processCode);
		resIsoMsg.set(4, requestISO.getString(4));
		resIsoMsg.set(7, requestISO.getString(7));
		resIsoMsg.set(11, stan);
		resIsoMsg.set(12, field12Resp);
		resIsoMsg.set(13, currentDate);
		resIsoMsg.set(24, requestISO.getString(24));
		resIsoMsg.set(25, requestISO.getString(25));
		resIsoMsg.set(30, "00000000");
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));
		resIsoMsg.set(39, "00");

		resIsoMsg.set(41, tid);
		resIsoMsg.set(42, mid);

		// cubc installment
		if (requestISO.hasField(48)) {
			resIsoMsg.set(48, requestISO.getString(48));
		}

		// cubc current code
		if (requestISO.hasField(49)) {
			resIsoMsg.set(49, requestISO.getString(49));
		}

		// resIsoMsg.set(55, requestISO.getString(55));

		if (requestISO.hasField(61)) {
			// IPP request
			// Field-61 : IP1111003000000000100 : 495031313131303033303030303030303030313030
			resIsoMsg.set(61, requestISO.getString(61) + "000000000000000000000000000000000000");
		}
		return resIsoMsg;

	}

	@SuppressWarnings("unchecked")
	private static ISOMsg compileSaleResponseMsgQR(ISOMsg requestISO) throws ISOException, IOException {

		ISOMsg resIsoMsg = new ISOMsg();
		String processCode = requestISO.getString(3);
		String stan = requestISO.getString(11);
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);
		String tid = requestISO.getString(41);
		String mid = requestISO.getString(42);

		resIsoMsg.setPackager(new QRPackager());
		resIsoMsg.setMTI("0210");
		resIsoMsg.set(3, processCode);
		resIsoMsg.set(11, stan);
		resIsoMsg.set(12, time);
		resIsoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));
		resIsoMsg.set(41, tid);
		resIsoMsg.set(42, mid);

		String amount = requestISO.getString(4);
		resIsoMsg.set(39, "00");

		if (requestISO.hasField(61)) {
			resIsoMsg.set(61, generateQrTagOutput("sale", requestISO.getString(61), amount, stan).toByteArray());
		}

		return resIsoMsg;

	}

	@SuppressWarnings("unchecked")
	private static ISOMsg compileInquiryResponseMsgQR(ISOMsg requestISO) throws ISOException, IOException {

		ISOMsg resIsoMsg = new ISOMsg();
		String processCode = requestISO.getString(3);
		String stan = requestISO.getString(11);
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);
		String tid = requestISO.getString(41);
		String mid = requestISO.getString(42);

		resIsoMsg.setPackager(new QRPackager());
		resIsoMsg.setMTI("0210");
		resIsoMsg.set(3, processCode);
		resIsoMsg.set(11, stan);
		resIsoMsg.set(12, time);
		resIsoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));
		resIsoMsg.set(41, tid);
		resIsoMsg.set(42, mid);

		String amount = requestISO.getString(4);
		resIsoMsg.set(39, getMockRespond(amount)[2]);

		if (requestISO.hasField(61)) {

			resIsoMsg.set(61, generateQrTagOutput("inquiry", requestISO.getString(61), amount, stan).toByteArray());

		}

		return resIsoMsg;
	}

	@SuppressWarnings("unchecked")
	private static ISOMsg compileRefundResponseMsgQR(ISOMsg requestISO) throws ISOException, IOException {

		String processCode = requestISO.getString(3);
		String stan = requestISO.getString(11);
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		SimpleDateFormat dateFormat2 = new SimpleDateFormat("yyyyMMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);
		String tid = requestISO.getString(41);
		String mid = requestISO.getString(42);

		ISOMsg resIsoMsg = new ISOMsg();
		resIsoMsg.setPackager(new QRPackager());
		resIsoMsg.setMTI("0230");
		resIsoMsg.set(2, "1234567890123456");
		resIsoMsg.set(3, processCode);
		resIsoMsg.set(11, stan);
		resIsoMsg.set(12, time);
		resIsoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));
		resIsoMsg.set(41, tid);
		resIsoMsg.set(42, mid);
		resIsoMsg.set(55, requestISO.getString(55));

		String amount = requestISO.getString(4);
		String[] respond = getMockRespond(amount);
		resIsoMsg.set(39, respond[2]);

		if (requestISO.hasField(61)) {
			// extract field 61
			HashMap<Object, Object> hashMap = parseQrTlvField(requestISO.getString(61));
			String clientMainQrId = (String) hashMap.get("01");

			ByteArrayOutputStream field61TLV = new ByteArrayOutputStream();
			StringBuilder field61Sb = new StringBuilder();

			// qrTransactionId
			field61Sb.append("01" + "025" + clientMainQrId);

			// refundId
			String qrRefundId = String.format("%025d", System.currentTimeMillis());
			field61Sb.append("02" + "025" + qrRefundId);

			String localDate = new DateTime(new Date()).toString("yyyyMMdd'T'HHmmss");
			field61Sb.append("10" + "015" + localDate);

			field61Sb.append("13" + "015" + localDate);

			field61Sb.append("29" + "008" + "87654321");

			String rppID = null;
			String clientPaymentType = (String) hashMap.get("03");
			String CIMBPayClientPaymentType = "802501";
			if (CIMBPayClientPaymentType.equals(clientPaymentType)) {
				// duitnow id
				// duitnowRRPID
				int n = new Random().nextInt(99999999);
				rppID = "30" + "031" + dateFormat2.format(new Date()) + "SIMULATOR" + "030OQR"
						+ formatStringWithPadding(String.valueOf(n), PADDING_ALIGN_LEFT, PADDING_TYPE_ZERO, 8);
				field61Sb.append(rppID);
			}

			// QR Partial Refund
			if ("1271".equals(respond[0])) {
				resIsoMsg.set(48,
						"H10041271H2052Invalid request. Partial refund not supported (1271)E10049999E2181Error From Service Provider.Please refer to //SignonRs/HeaderRs/ProviderList/Provider/ProviderRespStatusList/ProviderRespStatus for the Status Code and Desc : 4831303034313237314832303532496E76616C696420726571756573742E205061727469616C20726566756E64206E6F7420737570706F727465642028313237312945313030343939393945323138314572726F722046726F6D20536572766963652050726F76696465722E506C6561736520726566657220746F202F2F5369676E6F6E52732F43494D425F48656164657252732F43494D425F50726F76696465724C6973742F43494D425F50726F76696465722F43494D425F50726F7669646572526573705374617475734C6973742F43494D425F50726F76696465725265737053746174757320666F72207468652053746174757320436F646520616E642044657363");
			}
			
			field61TLV.write(ConverterUtil.hexStringToByteArray(ConverterUtil.ASCIIToHex(field61Sb.toString())));
			resIsoMsg.set(61, field61TLV.toByteArray());

		}

		return resIsoMsg;
	}

	private static ISOMsg compileTcAdviceResponseMsg(ISOMsg requestISO) throws ISOException {

		String processCode = requestISO.getString(3);
		String stan = requestISO.getString(11);
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);
		String tid = requestISO.getString(41);
		String mid = requestISO.getString(42);

		boolean isBatchUpload = "000000".equals(processCode);

		String field12Resp = time;

		// cubc field 12 format is YYMMDDhhmmss
		if (requestISO.hasField(12) && requestISO.getString(12).length() == 12) {
			SimpleDateFormat sdfCUBC = new SimpleDateFormat("yyMMddhhmmss");
			field12Resp = sdfCUBC.format(new Date());
		}

		ISOMsg resIsoMsg = new ISOMsg();
		resIsoMsg.setPackager(getISOPackagerBasedOnTPDU());
		resIsoMsg.setMTI("0330");
		resIsoMsg.set(3, processCode);
		resIsoMsg.set(11, stan);
		resIsoMsg.set(12, field12Resp);
		resIsoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));
		resIsoMsg.set(39, "00");
		resIsoMsg.set(41, tid);
		resIsoMsg.set(42, mid);

		if (isBatchUpload) {
			resIsoMsg.set(39, "00");
		}

		return resIsoMsg;
	}

	private static ISOMsg compileSettlementResponseMsg(ISOMsg requestISO) throws ISOException {

		String processCode = requestISO.getString(3);
		String stan = requestISO.getString(11);
		String nii = requestISO.getString(24);
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);
		String tid = requestISO.getString(41);
		String mid = requestISO.getString(42);

		boolean isMCCs = "138".equals(nii);
		boolean isUPI = "007".equals(nii);
		boolean isNormal = "105".equals(nii);

		boolean isTrailer = "960000".equals(processCode);

		ISOMsg resIsoMsg = new ISOMsg();
		resIsoMsg.setPackager(new ISO87BPackager());
		resIsoMsg.setMTI("0510");
		resIsoMsg.set(3, processCode);
		resIsoMsg.set(11, stan);
		resIsoMsg.set(12, time);
		resIsoMsg.set(13, currentDate);
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));

		resIsoMsg.set(39, "95");
		resIsoMsg.set(41, tid);
		resIsoMsg.set(42, mid);

		if (isTrailer) {
			resIsoMsg.set(39, "95");
		}

		return resIsoMsg;
	}

	private static ISOMsg compileLogonResponseMsg(ISOMsg requestISO) throws ISOException {
		ISOMsg resIsoMsg = new ISOMsg();
		// resIsoMsg.setPackager(new ISO87BPackager());
		resIsoMsg.setPackager(getISOPackagerBasedOnTPDU());
		resIsoMsg.setMTI("0810");
		resIsoMsg.set(3, "001000");
		resIsoMsg.set(11, "000001");
		resIsoMsg.set(12, "003715");
		resIsoMsg.set(13, "1103");
		resIsoMsg.set(39, "00");
		resIsoMsg.set(41, "M8090000");
		Field55Compiler field55 = new Field55Compiler();
		field55.setField("8A", "3030");
		field55.setField("91", "39383736353433323130");
		resIsoMsg.set(55, Utils.hexStringToByteArray(field55.getMessage()));
		resIsoMsg.set(62, "C160D0F980C2E3F674BF419C67E0B8E4FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");

		return resIsoMsg;
	}

	private static ISOMsg compileOfflineSaleResponseMsg(ISOMsg requestISO) throws ISOException {

		ISOMsg resIsoMsg = new ISOMsg();
		resIsoMsg.setPackager(new ISO87BPackager());
		resIsoMsg.setMTI("0230");
		resIsoMsg.set(3, requestISO.getString(3));
		resIsoMsg.set(11, requestISO.getString(11));
		resIsoMsg.set(24, requestISO.getString(24));
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		resIsoMsg.set(39, "00");
		resIsoMsg.set(41, requestISO.getString(41));
		return resIsoMsg;
	}

	private static ISOMsg compilePreauthResponseMsg(ISOMsg requestISO) throws ISOException {

		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);

		ISOMsg resIsoMsg = new ISOMsg();
		resIsoMsg.setPackager(new ISO87BPackager());
		resIsoMsg.setMTI("0110");
		resIsoMsg.set(3, requestISO.getString(3));
		resIsoMsg.set(4, requestISO.getString(4));
		resIsoMsg.set(11, requestISO.getString(11));
		resIsoMsg.set(12, time);
		resIsoMsg.set(13, currentDate);
		resIsoMsg.set(24, requestISO.getString(24));
		resIsoMsg.set(25, requestISO.getString(25));
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));
		resIsoMsg.set(39, "00");
		resIsoMsg.set(41, requestISO.getString(41));
		resIsoMsg.set(55, requestISO.getString(55));
		return resIsoMsg;
	}

	private static ISOMsg compileBalanceInquiryResponseMsg(ISOMsg requestISO) throws ISOException {

		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		String time = sdf.format(cal.getTime());
		DateFormat dateFormat = new SimpleDateFormat("MMdd");
		Date date = new Date();
		String currentDate = dateFormat.format(date);

		String currencyCode = null;

		String field12Resp = time;

		// cubc field 12 format is YYMMDDhhmmss
		if (requestISO.hasField(12) && requestISO.getString(12).length() == 12) {
			SimpleDateFormat sdfCUBC = new SimpleDateFormat("yyMMddhhmmss");
			field12Resp = sdfCUBC.format(new Date());
		}

		ISOMsg resIsoMsg = new ISOMsg();
		resIsoMsg.setPackager(getISOPackagerBasedOnTPDU());
		resIsoMsg.setMTI("0110");
		resIsoMsg.set(2, requestISO.getString(2));
		resIsoMsg.set(3, requestISO.getString(3));
		resIsoMsg.set(4, requestISO.getString(4));
		resIsoMsg.set(7, requestISO.getString(7));
		resIsoMsg.set(11, requestISO.getString(11));
		resIsoMsg.set(12, field12Resp);
		// resIsoMsg.set(13, currentDate);
		resIsoMsg.set(24, requestISO.getString(24));
		resIsoMsg.set(25, requestISO.getString(25));
		long rand = (long) (Math.random() * 1000000 * 1000000);
		resIsoMsg.set(37, rand + "");
		int rand2 = (new Random()).nextInt(900000) + 100000;
		resIsoMsg.set(38, String.valueOf(rand2));
		resIsoMsg.set(39, "000");
		resIsoMsg.set(41, requestISO.getString(41));

		if (requestISO.hasField(48)) {
			resIsoMsg.set(48, requestISO.getString(48));
		}

		if (requestISO.hasField(49)) {
			resIsoMsg.set(49, requestISO.getString(49));
			resIsoMsg.set(51, requestISO.getString(49)); // probably not in use but set same field 49 anyway
			currencyCode = requestISO.getString(49);
		}

		String accountType = "00"; // unspecified account type
		String amountType = "02"; // available balance
		String creditOrDebit = "C";
		String balance = String.format("%012d", 100);
		resIsoMsg.set(54, accountType + amountType + (currencyCode != null ? currencyCode : "840") + creditOrDebit + balance);

		resIsoMsg.set(55, requestISO.getString(55));
		return resIsoMsg;
	}

	private static void setPushNotification(String stan, String amount, String payType, String qrId, String rppId) {

		Map<String, Object> data = new HashMap<>();
		data.put("pan", "                    ");
		String localDate = new DateTime(new Date()).toString("yyyy-MM-dd HH:mm:ss");
		data.put("txndtm", localDate);
		// data.put("out_trade_no", stan);
		String[] respond = getMockRespond(amount);
		data.put("respcd", respond[0]);
		data.put("resmsg", respond[1]);

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
		data.put("order_id", "12345678");
		data.put("end_to_end_id", rppId);

		String rawRequestString = com.payment.emv.host.utils.JSONFactory.toJson(data);

		if (!respond[2].startsWith("C")) {
			pushNotCQueue.add(rawRequestString);
		}
	}

	public static HashMap parseQrTlvField(final String strField) {

		HashMap<Object, Object> fieldResult = new HashMap<>();

		try {

			StringBuilder remainingField61Sb = new StringBuilder(strField);
			// qrTLVField is in format TTLLLV

			while (remainingField61Sb.length() > 0) {

				// tag + length at least need 5 digit as one valid tlv
				if (remainingField61Sb.length() >= 5) {
					String tag = remainingField61Sb.substring(0, 2);
					remainingField61Sb.delete(0, 2);

					String lengthInString = remainingField61Sb.substring(0, 3);
					remainingField61Sb.delete(0, 3);

					int length = Integer.parseInt(lengthInString);

					String content = remainingField61Sb.substring(0, length);
					remainingField61Sb.delete(0, length);

					System.out.println("      TAG-" + tag + " : " + content);

					fieldResult.put(tag, content);
				}
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return fieldResult;
	}

	public static ByteArrayOutputStream generateQrTagOutput(String type, String field61, String amount, String stan) throws IOException {

		// extract field 61
		HashMap<Object, Object> reqField61HashMap = parseQrTlvField(field61);

		String clientTransactionDateTime = "";
		String clientPaymentType = "";
		String clientMainQrId = "";
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		String CIMBPayClientPaymentType = "802501";

		if (reqField61HashMap.containsKey("01")) {
			clientMainQrId = (String) reqField61HashMap.get("01");
		}

		if (reqField61HashMap.containsKey("10")) {
			clientTransactionDateTime = (String) reqField61HashMap.get("10");
		}
		if (reqField61HashMap.containsKey("02")) {
			clientPaymentType = (String) reqField61HashMap.get("02");
		}
		if (reqField61HashMap.containsKey("03")) {
			clientPaymentType = (String) reqField61HashMap.get("03");
		}

		boolean isQrSale = "sale".equals(type);
		boolean isQrInquiry = "inquiry".equals(type);
		boolean isQrRefund = "".equals(type);

		ByteArrayOutputStream field61TLV = new ByteArrayOutputStream();
		StringBuilder field61Sb = new StringBuilder();
		String rppID = null;

		if (CIMBPayClientPaymentType.equals(clientPaymentType)) {
			// duitnow id
			// duitnowRRPID
			int n = new Random().nextInt(99999999);
			rppID = dateFormat.format(new Date()) + "SIMULATOR" + "030OQR"
					+ formatStringWithPadding(String.valueOf(n), PADDING_ALIGN_LEFT, PADDING_TYPE_ZERO, 8);
		}

		if (isQrSale) {

			String qrId = String.format("%025d", System.currentTimeMillis());
			field61Sb.append("01" + "025" + qrId);

			field61Sb.append(
					"03" + formatStringWithPadding(Integer.toString(clientPaymentType.length()), PADDING_ALIGN_LEFT, PADDING_TYPE_ZERO, 3)
							+ clientPaymentType);

			// payload
			field61Sb.append("06" + "020" + "www.google.com.my");
			// Expire date
			field61Sb.append("07" + "003" + "120");
			// Inquiry time
			field61Sb.append("08" + "002" + "15");
			// transaction Label
			field61Sb.append("09" + "017" + "Simulator Respond");

			// less than 100 all will trigger push notification
			if (Long.valueOf(amount) < 10000) {
				setPushNotification(stan, amount, clientPaymentType, qrId, rppID);
			}

		} else if (isQrInquiry) {

			String[] respond = getMockRespond(amount);
			String returnCode = respond[0];
			String returnStatus = respond[1];

			// respondCode
			field61Sb.append("T1" + "004" + returnCode);
			// respondMsg
			field61Sb.append(
					"T2" + formatStringWithPadding(Integer.toString(returnStatus.length()), PADDING_ALIGN_LEFT, PADDING_TYPE_ZERO, 3)
							+ returnStatus);
			// clientDateTime
			String localDate = new DateTime(new Date()).toString("yyMMdd'T'HHmmss");
			field61Sb.append("10" + formatStringWithPadding(Integer.toString(localDate.length()), PADDING_ALIGN_LEFT, PADDING_TYPE_ZERO, 3)
					+ localDate);
			// localDate
			field61Sb.append("13" + "013" + localDate);
			// stan
			field61Sb.append("11" + "006" + stan);

			// payment type
			if (qrRefundList.contains(clientMainQrId)) {
				field61Sb.append("14" + "006" + "Refund");
			} else {
				field61Sb.append("14" + "007" + "Payment");
			}

			// transactionStatus
			field61Sb.append("TS" + "001" + "0");

			// order id
			field61Sb.append("29" + "008" + "12345678");
			if (rppID != null) {
				field61Sb.append("30" + "031" + rppID);
			}

			qrRefundList.remove(clientMainQrId);

		} else if (isQrRefund) {

			if ("00".equals(getMockRespond(amount)[2])) {
				// qrTransactionId
				field61Sb.append("01" + "025" + clientMainQrId);

				// refundId
				String qrRefundId = String.format("%025d", System.currentTimeMillis());
				field61Sb.append("02" + "025" + qrRefundId);

				String localDate = new DateTime(new Date()).toString("yyyyMMdd'T'HHmmss");
				field61Sb.append("10" + "015" + localDate);

				field61Sb.append("13" + "015" + localDate);
				// order id
				field61Sb.append("29" + "008" + "12345678");
				if (rppID != null) {
					field61Sb.append("30" + "031" + rppID);
				}

				if (!qrRefundList.contains(qrRefundId)) {
					qrRefundList.add(qrRefundId);
				}
			}

		}

		field61TLV.write(ConverterUtil.hexStringToByteArray(ConverterUtil.ASCIIToHex(field61Sb.toString())));

		return field61TLV;
	}

	public static String[] getMockRespond(String amount) {

		String endDigit = amount.substring(amount.length() - 2);
		String[] respond = new String[3];
		respond[0] = "0000";
		respond[1] = "QF_OK";
		respond[2] = "00";

		if (Long.valueOf(amount) == 20000L) {
			respond[0] = "1131";
			respond[1] = "CL_ERR_CHANNEL_TIMEOUT";
			respond[2] = "C8";
			return respond;
		}

		if (endDigit.equals("01")) {
			respond[0] = "1143";
			respond[1] = "QF_ERR_ORDER_NOT_EXIST";
			respond[2] = "E6";
			// Rejected by QF Pay, refer Field 61 for detailed error description
		} else if (endDigit.equals("02")) {
			respond[0] = "1143";
			respond[1] = "QF_ERR_ORDER_STATUS_UNKNOWN";
			respond[2] = "E6";
		} else if (endDigit.equals("03")) {
			respond[0] = "1145";
			respond[1] = "QF_ERR_ORDER_WAIT_PAY";
			respond[2] = "E6";
		} else if (endDigit.equals("04")) {
			respond[0] = "1254";
			respond[1] = "QF_ERR_NOCARD_SYSTEM_ERROR";
			respond[2] = "E6";
		} else if (endDigit.equals("05")) {
			respond[0] = "1100";
			respond[1] = "ESB_ERR_MAINTEN";
			respond[2] = "E3";
			// Rejected by ESB due to error during request/response processing, refer Field 61 for detailed error description
		} else if (endDigit.equals("06")) {
			respond[0] = "1103";
			respond[1] = "CL_ERR_JSON";
			respond[2] = "E7";
			// Rejected by Cardlink, refer Field 61 for detailed error description
		} else if (endDigit.equals("07")) {
			respond[0] = "1131";
			respond[1] = "QF_ERR_CHANNEL_TIMEOUT";
			respond[2] = "C8";
			// Timeout when interfacing with QF Pay
		} else if (endDigit.equals("08")) {
			respond[0] = "1131";
			respond[1] = "CL_ERR_CHANNEL_TIMEOUT";
			respond[2] = "C8";
			// Timeout when interfacing with Cardlink
		} else if (endDigit.equals("09")) {
			respond[0] = "1269";
			respond[1] = "today unsettled amount is not enough(1269)";
			respond[2] = "E6";
			// Timeout when interfacing with Cardlink
		} else if (endDigit.equals("11")) {
			respond[0] = "1271";
			respond[1] = "Invalid request. Partial refund not supported (1271)";
			respond[2] = "E6";
			// QR Partial Refund
		}
		return respond;
	}

	public static String[] getCUBCMockRespond(String amount) {

		String endDigit = amount.substring(amount.length() - 2);
		String[] respond = new String[2];

		respond[0] = "APPROVED";
		respond[1] = "000";

		if (Long.valueOf(amount) == 20000L) {
			respond[0] = "HOST_RESPONSE_TIMED_OUT";
			respond[1] = "912";
			return respond;
		}

		switch (endDigit) {
			case "01":
				respond[0] = "ORI_TRX_NOT_FOUND";
				respond[1] = "914";
				break;
			case "02":
				respond[0] = "REENTER_TRX";
				respond[1] = "903";
				break;
			case "03":
				respond[0] = "REQUEST_IN_PROGRESS";
				respond[1] = "923";
				break;
			case "04":
				respond[0] = "EXPIRED_CARD";
				respond[1] = "201";
				break;
			case "05":
				respond[0] = "BAD_CARD";
				respond[1] = "125";
				break;
			case "06":
				respond[0] = "RETRY_INVALID_TRX";
				respond[1] = "902";
				break;
			case "07":
				respond[0] = "SYSTEM_MALFUNCTION";
				respond[1] = "909";
				break;
			case "08":
				respond[0] = "HOST_RESPONSE_TIMED_OUT";
				respond[1] = "912";
				break;
			case "09":
				respond[0] = "RETRY_INSUFFICIENT_FUNDS";
				respond[1] = "116";
				break;
			case "11":
				respond[0] = "INCORRECT_PIN";
				respond[1] = "117";
				break;
		}

		return respond;
	}

	class SendQRPushNotification implements Runnable {

		@Override
		public void run() {
			if (!pushNotCQueue.isEmpty()) {
				String rawRequestString = pushNotCQueue.get(0);

				if (rawRequestString != null) {
					try {
						Thread.sleep(5000L);

						System.out.println("===============================================");
						System.out.println("sending push notification");
						System.out.printf("JSON: %s", rawRequestString);
						String pushNotAddrress = "http://localhost:8092/qr/result";

						String rawResponseString = doV2Post(pushNotAddrress, rawRequestString, false);

						System.out.println("JSON response: " + rawResponseString);

						pushNotCQueue.remove(0);

					} catch (InterruptedException | IOException e) {
						e.printStackTrace();
					} // 5 seconds only push notification back
				}
			}
		}
	}

	public static String formatStringWithPadding(String inputString, int PADDING_ALIGN, String PADDING_TYPE, int length) {

		inputString = inputString == null ? "" : inputString;

		if (length > 0) {
			if (PADDING_ALIGN_LEFT == PADDING_ALIGN) {
				if (PADDING_TYPE_SPACE == PADDING_TYPE) {
					return String.format("%1$" + length + "s", inputString);
				} else if (PADDING_TYPE_ZERO == PADDING_TYPE) {
					return String.format("%1$" + length + "s", inputString).replace(' ', '0');
				}
			} else if (PADDING_ALIGN_RIGHT == PADDING_ALIGN) {
				if (PADDING_TYPE_SPACE == PADDING_TYPE) {
					return String.format("%1$-" + length + "s", inputString);
				} else if (PADDING_TYPE_ZERO == PADDING_TYPE) {
					return String.format("%1$-" + length + "s", inputString).replace(' ', '0');
				}
			}
		}
		return inputString;
	}

	public static ISOPackager getISOPackagerBasedOnTPDU() {

		if (tpdu == null) {
			return new ISO87BPackager();
		}

		System.out.println("Fetching ISOPackager for TPDU: " + tpdu);
		System.out.println("Using ISO87BPackager...");
		return new ISO87BPackager();

	}
}
