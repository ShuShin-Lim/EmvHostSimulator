package com.payment.emv.host;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87BPackager;

import com.payment.emv.host.utils.Field55Compiler;
import com.payment.emv.host.utils.Utils;

public class SimpleEmvNacHost {

	public static void main(String[] args) {
		// create a server socket channel
		ServerSocketChannel serverSocketChannel = null;

		boolean isPersistent = true;

		try {
			serverSocketChannel = ServerSocketChannel.open();

			// bind the server socket channel to the following IP address and Port
			serverSocketChannel.bind(new InetSocketAddress("127.0.0.1", 6668));
			System.out.println("emv host started. waiting for client connections...");

			while (true) {
				try {
					SocketChannel socketChannel = serverSocketChannel.accept();

					// process a client request
					if (socketChannel != null) {

						Socket socket = socketChannel.socket();
						socket.setSoLinger(false, 0);
						socket.setKeepAlive(true);
						socket.setTcpNoDelay(true);

						// get the input and output streams for reading and writing
						DataInputStream inputStream = new DataInputStream(socket.getInputStream());
						DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

						do {
							// printing the address of the client
							System.out.println("--START--");
							System.out.println("Request:");
							System.out.println("    Obtained connection from : " + socketChannel.getRemoteAddress().toString());
							// reading the content of the socket input stream
							byte[] reqLen = new byte[2];
							inputStream.read(reqLen);
							System.out.println("    Request Length (HEX) : " + Utils.bytesToHexString(reqLen));
							System.out.println("    Request Length (DEC) : " + Integer.parseInt(Utils.bytesToHexString(reqLen), 16));
							byte reqBuf[] = new byte[Integer.parseInt(Utils.bytesToHexString(reqLen), 16)];
							inputStream.read(reqBuf);
							System.out.println("    Request Data (HEX) : " + Utils.bytesToHexString(reqBuf));

							// parse the iso message
							byte isoBuf[] = new byte[reqBuf.length - 5];
							System.arraycopy(reqBuf, 5, isoBuf, 0, isoBuf.length);
							ISOMsg req_isoMsg = new ISOMsg();
							req_isoMsg.setPackager(new ISO87BPackager());
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

							if ("0200".equals(req_mti)) {
								res_isoMsg = compileSaleResponseMsg(processCode, time, currentDate, stan, tid, mid, req_isoMsg);
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

							outputStream.write(res_msg);
							outputStream.flush();

							System.out.println("---END---");

						} while (isPersistent);

						// socket.shutdownInput();
						// socket.shutdownOutput();
						inputStream.close();
						outputStream.close();

						socket.close();
						socketChannel.close();
						System.out.println("inputStream, outputStream, socket and socketChannel closed");
					}
				} catch (IOException ex) {
					ex.printStackTrace();
				} catch (ISOException e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			try {
				serverSocketChannel.close();
				System.out.println("emv host exiting.");
			} catch (IOException e) {
				e.printStackTrace();
			}
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

	private static ISOMsg compileSaleResponseMsg(String processCode, String time, String currentDate, String stan, String tid, String mid,
			ISOMsg requestISO) throws ISOException {
		ISOMsg res_isoMsg = new ISOMsg();
		res_isoMsg.setPackager(new ISO87BPackager());
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
		// res_isoMsg.set(55, requestISO.getString(55));

		boolean enableRewardPointParsing = false;
		boolean isHappyPay = true;

		if (enableRewardPointParsing) {
			// 1. Field-58 : P00000000100000001000 : 1F0315503030303030303030313030303030303031303030
			// 2. Field-58 : DF0415503030303030303030313030303030303031303030 : DF0415503030303030303030313030303030303031303030

			// Reward Point Request
			if (requestISO.hasField(58)) {
				byte[] header = Utils.hexStringToByteArray("1F041E");
				byte[] body = Utils.hexStringToByteArray(ASCIIToHex("P00000000100000001000000000499"));

				res_isoMsg.set(58, concatByteArrays(header, body));
			} else {
				byte[] header = Utils.hexStringToByteArray("1F0315");
				byte[] body = Utils.hexStringToByteArray(ASCIIToHex("P00000000100000001000"));

				res_isoMsg.set(39, "00");
				res_isoMsg.set(58, concatByteArrays(header, body));

			}
		}

		if (isHappyPay) {
			String cardNo = "5521154000540090";
			String salesAmt = "0000000005000";
			String totalAmt = "1234567890123";
			String interestRate = "12345";
			String interestAmt = "00000060001";
			String installmentPeriod = "123";
			String installmentAmt = "12345678901";
			String approveCode = "123456";
			String promotionCode = "1234567890";
			String promotionName = "promotionnametesting";
			String product = "12345678901234567890";
			String maker = "12345678901234567890";
			String model = "12345678901234567890";
			String feeRate = "00345";
			String feeAmt = "00000008901";
			String businessType = "12";
			String businessName = "0000ness90123456name";
			String merchantCat = "cat1";
			String merchantCatName = "00000ant011234560000";
			String filler = "00000000000000000000";
			String fullString = cardNo + salesAmt + totalAmt + interestRate + interestAmt + installmentPeriod + installmentAmt
					+ approveCode + promotionCode + promotionName + product + maker + model + feeRate + feeAmt + businessType
					+ businessName + merchantCat + merchantCatName + filler;

			res_isoMsg.set(63, fullString);
		}

		// IPP request
		if (requestISO.hasField(61)) {
			// Field-61 : IP1111003000000000100 : 495031313131303033303030303030303030313030
			res_isoMsg.set(61, "00606000000000000000000000000000000000000");

		}

		return res_isoMsg;
	}

	public static String ASCIIToHex(String ascii) {
		char[] chars = ascii.toCharArray();
		StringBuffer hex = new StringBuffer();
		for (int i = 0; i < chars.length; i++) {
			hex.append(Integer.toHexString((int) chars[i]));
		}
		return hex.toString();
	}

	public static byte[] concatByteArrays(final byte[] byteArray1, final byte[] byteArray2) {

		byte[] one = byteArray1;
		byte[] two = byteArray2;
		byte[] combined = new byte[one.length + two.length];

		System.arraycopy(one, 0, combined, 0, one.length);
		System.arraycopy(two, 0, combined, one.length, two.length);

		return combined;
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

	private static ISOMsg compileLogonResponseMsg() throws ISOException {
		ISOMsg res_isoMsg = new ISOMsg();
		res_isoMsg.setPackager(new ISO87BPackager());
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
		// res_isoMsg.set(63, "01ALL PRODUCT         ALL PRODUCT         2 OPTION            ");
		return res_isoMsg;
	}

}
