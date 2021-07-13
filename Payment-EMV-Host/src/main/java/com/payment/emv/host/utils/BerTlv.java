package com.payment.emv.host.utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for representing BER-TLV objects.
 */
public class BerTlv {

	private final Tag mTag;
	private final byte[] mValue;

	/**
	 * Constructs a new BER-TLV object from given tag and value.
	 * 
	 * @param tag the tag.
	 * @param value the value.
	 */
	public BerTlv(Tag tag, byte[] value) {
		if (tag == null) {
			throw new IllegalArgumentException("The argument 'tag' can not be null");
		}

		if (value == null) {
			throw new IllegalArgumentException("The argument 'value' can not be null");
		}

		this.mTag = tag;
		this.mValue = value;
	}

	/**
	 * Constructs a new object from given tag and value.
	 * 
	 * @param tag the tag.
	 * @param value the value.
	 */
	public BerTlv(int tag, byte[] value) {
		this(new Tag(tag), value);
	}

	/**
	 * Gets a tag instance of the BER-TLV object.
	 *
	 * @return A tag integer.
	 */
	public Tag getTag() {
		return mTag;
	}

	/**
	 * Gets the encoded length of the BER-TLV object.
	 *
	 * @return the encoded length.
	 */
	public byte[] getLengthBytes() {
		return encodeLength(mValue.length);
	}

	/**
	 * Gets a value of the BER-TLV object.
	 *
	 * @return the value.
	 */
	public byte[] getValue() {
		return mValue;
	}

	/**
	 * Gets a value of the BER-TLV object as HEX string.
	 *
	 * @return the value as HEX string.
	 */
	public String getValueAsHexString() {
		final char[] hex = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
		final char[] buf = new char[mValue.length * 2];

		for (int i = 0, j = 0; i < mValue.length; i++) {
			buf[j++] = hex[((mValue[i] >> 4) & 0xf)];
			buf[j++] = hex[((mValue[i]) & 0xf)];
		}

		return new String(buf);
	}

	/**
	 * Encode the BER-TLV object and store the result in new byte array.
	 * 
	 * @return the encoded object.
	 */
	public byte[] toByteArray() {
		byte[] tmpTag = mTag.getBytes();
		byte[] tmpLen = getLengthBytes();
		byte[] tmpVal = mValue;
		byte[] buffer = new byte[tmpTag.length + tmpLen.length + tmpVal.length];
		System.arraycopy(tmpTag, 0, buffer, 0, tmpTag.length);
		System.arraycopy(tmpLen, 0, buffer, tmpTag.length, tmpLen.length);
		System.arraycopy(tmpVal, 0, buffer, tmpTag.length + tmpLen.length, tmpVal.length);
		return buffer;
	}

	/**
	 * Encode the LENGTH and store the result in new byte array.
	 * 
	 * @return the encoded LENGTH.
	 */
	public static byte[] encodeLength(int length) {
		byte[] data = null;

		if (length == 0) {
			data = new byte[] { (byte) 0x00 };
		} else if (length <= 127) {
			data = new byte[] { (byte) length };
		} else {
			int numberOfBytes = 0;

			do {
				numberOfBytes++;
			} while ((length & (0x7FFFFF << (8 * numberOfBytes))) > 0);

			data = new byte[numberOfBytes + 1];
			data[0] = (byte) (0x80 + numberOfBytes);
			for (int i = 0; i < numberOfBytes; i++) {
				data[numberOfBytes - i] = (byte) ((length >> (i * 8)) & 0xff);
			}
		}

		return data;
	}

	/**
	 * Decode LENGTH from given byte buffer.
	 * 
	 * @return the length.
	 */
	public static int decodeLength(ByteBuffer data) {
		int length = (int) data.get() & 0xff;

		if ((length & 0x80) != 0) {
			int numberOfBytes = length & 0x7F;

			length = 0;
			while (numberOfBytes > 0) {
				length = (length << 8) + ((int) data.get() & 0xff);
				numberOfBytes--;
			}
		}

		return length;
	}

	/**
	 * Constructs a new BER-TLV object from given tag and value.
	 * 
	 * @param tag the tag.
	 * @param value the value.
	 * @return a new BER-TLV object.
	 */
	public static BerTlv create(Tag tag, byte[] value) {
		return new BerTlv(tag, value);
	}

	/**
	 * Constructs a new BER-TLV object from given tag and list of BER-TLV objects as value.
	 * 
	 * @param tag the tag.
	 * @param values the list of BER-TLV objects.
	 * @return a new BER-TLV object.
	 */
	public static BerTlv create(Tag tag, List<BerTlv> values) {
		byte[][] container = new byte[values.size()][];
		int totalDataLen = 0;

		for (int i = 0; i < container.length; i++) {
			container[i] = values.get(i).toByteArray();
			totalDataLen += container[i].length;
		}

		byte[] buffer = new byte[totalDataLen];

		for (int i = 0, off = 0; i < container.length; i++) {
			System.arraycopy(container[i], 0, buffer, off, container[i].length);
			off += container[i].length;
		}

		return new BerTlv(tag, buffer);
	}

	/**
	 * Constructs a new BER-TLV object from given byte buffer.
	 * 
	 * @param buffer the byte buffer.
	 * @return a new BER-TLV object.
	 */
	public static BerTlv create(ByteBuffer buffer) {
		Tag tag = Tag.create(buffer);
		byte[] zeroByte = new byte[] { 0x00 };

		if (Arrays.equals(tag.getBytes(), zeroByte)) {
			return null;
		}

		int len = decodeLength(buffer);
		byte[] val = new byte[len];
		buffer.get(val);
		return new BerTlv(tag, val);
	}

	/**
	 * Constructs a new BER-TLV object from given byte array.
	 * 
	 * @param src the byte array.
	 * @param off the offset into array.
	 * @param len the length.
	 * @return a new BER-TLV object.
	 */
	public static BerTlv create(byte[] src, int off, int len) {
		ByteBuffer buffer = ByteBuffer.wrap(src, off, len);
		return create(buffer);
	}

	/**
	 * Constructs a new BER-TLV object from given byte array.
	 * 
	 * @param src the byte array.
	 * @return a new BER-TLV object.
	 */
	public static BerTlv create(byte[] src) {
		ByteBuffer buffer = ByteBuffer.wrap(src, 0, src.length);
		return create(buffer);
	}

	/**
	 * Constructs a list of BER-TLV objects from given byte buffer.
	 * 
	 * @param buffer the byte buffer.
	 * @return a list of BER-TLV objects.
	 */
	public static List<BerTlv> createList(ByteBuffer buffer) {
		final List<BerTlv> tlvList = new ArrayList<BerTlv>();

		while (buffer.hasRemaining()) {
			BerTlv tlv = BerTlv.create(buffer);
			tlvList.add(tlv);
		}

		return tlvList;
	}

	/**
	 * Constructs a list of BER-TLV objects from given byte array.
	 * 
	 * @param array the byte array.
	 * @return a list of BER-TLV objects.
	 */
	public static List<BerTlv> createList(byte[] array) {
		return createList(ByteBuffer.wrap(array));
	}

	/**
	 * Constructs a map of tag and value from given byte buffer.
	 * 
	 * @param buffer the byte buffer.
	 * @return a map object.
	 */
	public static Map<Tag, byte[]> createMap(ByteBuffer buffer) {
		final Map<Tag, byte[]> tlvMap = new HashMap<Tag, byte[]>();

		while (buffer.hasRemaining()) {
			BerTlv tlv = BerTlv.create(buffer);
			tlvMap.put(tlv.getTag(), tlv.getValue());
		}

		return tlvMap;
	}

	/**
	 * Constructs a map of tag and value from given byte array.
	 * 
	 * @param array the byte array.
	 * @return a map object.
	 */
	public static Map<Tag, byte[]> createMap(byte[] array) {
		return createMap(ByteBuffer.wrap(array));
	}

	/**
	 * Encode the list of BER-TLV objects to byte array.
	 * 
	 * @param input a list of BER-TLV objects.
	 * @return the data.
	 */
	public static byte[] listToByteArray(List<BerTlv> input) {
		final List<byte[]> dataList = new ArrayList<byte[]>();
		int totalLen = 0;

		for (BerTlv tlv : input) {
			byte[] tmp = tlv.toByteArray();
			dataList.add(tmp);
			totalLen += tmp.length;
		}

		byte[] buffer = new byte[totalLen];
		totalLen = 0;
		for (byte[] data : dataList) {
			System.arraycopy(data, 0, buffer, totalLen, data.length);
			totalLen += data.length;
		}

		return buffer;
	}

	/**
	 * Encode the map of tag and values to byte array.
	 * 
	 * @param input the map.
	 * @return the data.
	 */
	public static byte[] mapToByteArray(Map<Tag, byte[]> input) {
		final List<byte[]> dataList = new ArrayList<byte[]>();
		int totalLen = 0;

		for (Tag tag : input.keySet()) {
			byte[] tmpTag = tag.getBytes();
			byte[] tmpVal = input.get(tag);
			byte[] tmpLen = BerTlv.encodeLength(tmpVal.length);

			dataList.add(tmpTag);
			dataList.add(tmpLen);
			dataList.add(tmpVal);

			totalLen += tmpTag.length + tmpLen.length + tmpVal.length;
		}

		byte[] buffer = new byte[totalLen];
		totalLen = 0;
		for (byte[] data : dataList) {
			System.arraycopy(data, 0, buffer, totalLen, data.length);
			totalLen += data.length;
		}

		return buffer;
	}

	/**
	 * Find and constructs a BER-TLV objects from byte buffer.
	 * 
	 * @param buffer the byte buffer.
	 * @param tag the searching tag object.
	 * @return a new BER-TLV object.
	 */
	public static BerTlv find(ByteBuffer buffer, Tag tag) {
		while (buffer.hasRemaining()) {
			BerTlv tlv = BerTlv.create(buffer);

			if (tlv != null) {
				if (tlv.getTag().equals(tag)) {
					return tlv;
				}
			}
		}

		return null;
	}

	/**
	 * Find and constructs a BER-TLV objects from byte buffer.
	 * 
	 * @param buffer the byte buffer.
	 * @param tag the searching tag value.
	 * @return a new BER-TLV object.
	 */
	public static BerTlv find(ByteBuffer buffer, int tag) {
		return find(buffer, new Tag(tag));
	}

	/**
	 * Find and constructs a BER-TLV objects from byte array.
	 * 
	 * @param array the byte array.
	 * @param tag the searching tag object.
	 * @return a new BER-TLV object.
	 */
	public static BerTlv find(byte[] array, Tag tag) {
		return find(ByteBuffer.wrap(array), tag);
	}

	/**
	 * Find and constructs a BER-TLV objects from byte array.
	 * 
	 * @param array the byte array.
	 * @param tag the searching tag value.
	 * @return a new BER-TLV object.
	 */
	public static BerTlv find(byte[] array, int tag) {
		return find(ByteBuffer.wrap(array), tag);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj != null && (obj instanceof Tag)) {
			BerTlv other = (BerTlv) obj;

			if (!mTag.equals(other.getTag()))
				return false;

			if (mValue.length != other.mValue.length)
				return false;

			for (int i = 0; i < mValue.length; i++) {
				if (mValue[i] != other.mValue[i])
					return false;
			}

			return true;
		}

		return false;
	}

	@Override
	public int hashCode() {
		int result = 1 + BerTlv.class.getName().hashCode();

		for (byte element : mTag.getBytes()) {
			result = 31 * result + element;
		}

		for (byte element : mValue) {
			result = 31 * result + element;
		}

		return result;
	}

	@Override
	public String toString() {
		return "BerTlv [Tag=" + mTag.toHexValue() + ", Length=" + mValue.length + ", Value=" + getValueAsHexString() + "]";
	}

}
